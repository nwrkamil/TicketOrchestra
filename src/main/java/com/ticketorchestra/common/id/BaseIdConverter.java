package com.ticketorchestra.common.id;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.UUID;
import java.util.function.Function;

public abstract class BaseIdConverter<T> implements AttributeConverter<T> {
    private final Function<UUID, T> factory;
    private final Function<T, UUID> extractor;
    private final Class<T> clazz;

    protected BaseIdConverter(Function<UUID, T> factory, Function<T, UUID> extractor, Class<T> clazz) {
        this.factory = factory;
        this.extractor = extractor;
        this.clazz = clazz;
    }

    @Override
    public AttributeValue transformFrom(T input) {
        return AttributeValue.builder().s(extractor.apply(input).toString()).build();
    }

    @Override
    public T transformTo(AttributeValue input) {
        return factory.apply(UUID.fromString(input.s()));
    }

    @Override
    public EnhancedType<T> type() {
        return EnhancedType.of(clazz);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
