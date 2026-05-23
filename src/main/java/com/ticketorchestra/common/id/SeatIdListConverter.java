package com.ticketorchestra.common.id;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.UUID;

public class SeatIdListConverter implements AttributeConverter<List<SeatId>> {
    @Override
    public AttributeValue transformFrom(List<SeatId> input) {
        return AttributeValue.builder().ss(input.stream().map(s -> s.id().toString()).toList()).build();
    }

    @Override
    public List<SeatId> transformTo(AttributeValue input) {
        return input.ss().stream().map(UUID::fromString).map(SeatId::new).toList();
    }

    @Override
    public EnhancedType<List<SeatId>> type() {
        return EnhancedType.listOf(SeatId.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.SS;
    }
}
