package com.ticketorchestra.common.config;

import lombok.extern.slf4j.Slf4j;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("local")
public class DynamoDbInitializer {

    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000;

    @SuppressFBWarnings("EI2")
    public DynamoDbInitializer(@Lazy DynamoDbClient dynamoDbClient, @Lazy SqsClient sqsClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.sqsClient = sqsClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info("Initializing infrastructure for local profile (with retries)...");
        
        retryOperation("DynamoDB Tables", () -> {
            createTables();
            return true;
        });

        retryOperation("SQS Queues", () -> {
            createQueues();
            return true;
        });
    }

    private void retryOperation(String name, java.util.function.Supplier<Boolean> operation) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                if (operation.get()) {
                    return;
                }
            } catch (Exception e) {
                attempt++;
                log.error("Attempt {} failed to initialize {}: {}", attempt, name, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("Failed to initialize {} after {} attempts.", name, MAX_RETRIES);
    }

    private void createTables() {
        createTable("Reservations", "reservationId", null);
        createTable("Events", "eventId", null);
        createTable("Seats", "eventId", "seatId");
        createTable("Outbox", "eventId", null);
        createTable("Payments", "paymentId", null);
    }

    private void createQueues() {
        createQueue("reservation-events");
        createQueue("payment-events");
    }

    private void createQueue(String queueName) {
        try {
            sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build());
            log.info("Queue '{}' created successfully.", queueName);
        } catch (QueueNameExistsException e) {
            log.info("Queue '{}' already exists.", queueName);
        } catch (SqsException e) {
            if (e.statusCode() == 500) {
                throw e; // Trigger retry
            }
            log.error("Failed to create SQS queue '{}': {}", queueName, e.getMessage());
        }
    }

    private void createTable(String tableName, String pkName, String skName) {
        try {
            List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
            attributeDefinitions.add(AttributeDefinition.builder()
                    .attributeName(pkName)
                    .attributeType(ScalarAttributeType.S)
                    .build());

            List<KeySchemaElement> keySchema = new ArrayList<>();
            keySchema.add(KeySchemaElement.builder()
                    .attributeName(pkName)
                    .keyType(KeyType.HASH)
                    .build());

            if (skName != null) {
                attributeDefinitions.add(AttributeDefinition.builder()
                        .attributeName(skName)
                        .attributeType(ScalarAttributeType.S)
                        .build());
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(skName)
                        .keyType(KeyType.RANGE)
                        .build());
            }

            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(attributeDefinitions)
                    .keySchema(keySchema)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();

            dynamoDbClient.createTable(request);
            log.info("Table '{}' created successfully.", tableName);
        } catch (ResourceInUseException e) {
            log.info("Table '{}' already exists.", tableName);
        } catch (DynamoDbException e) {
            if (e.statusCode() == 500) {
                throw e; // Trigger retry
            }
            log.error("Failed to create DynamoDB table '{}': {}", tableName, e.getMessage());
        }
    }
}
