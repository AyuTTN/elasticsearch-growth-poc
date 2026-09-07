package com.poc.elasticsearch.model;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

public record OrderItem(
        @Field(type = FieldType.Keyword) String productId,
        @Field(type = FieldType.Text, analyzer = "standard") String productName,
        @Field(type = FieldType.Integer) int quantity,
        @Field(type = FieldType.Double) BigDecimal unitPrice
) {
}
