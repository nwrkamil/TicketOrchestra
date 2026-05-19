package com.ticketorchestra.common.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

@Component
@Profile("local")
public class DynamoDbInitializer {

    private final DynamoDbClient dynamoDbClient;

    @SuppressFBWarnings("EI2")
    public DynamoDbInitializer(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createTables() {
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName("Reservations")
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("reservationId")
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("reservationId")
                            .keyType(KeyType.HASH)
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            System.out.println("Table 'Reservations' created successfully.");
        } catch (ResourceInUseException e) {
            System.out.println("Table 'Reservations' already exists.");
        }
    }
}
