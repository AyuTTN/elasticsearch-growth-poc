package com.poc.elasticsearch.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(indexName = "orders")
public record Order(
        @Id @Field(type = FieldType.Keyword) String id,
        @Field(type = FieldType.Keyword) String customerEmail,
        @Field(type = FieldType.Keyword) String status,
        @Field(type = FieldType.Nested) List<OrderItem> items,
        @Field(type = FieldType.Double) BigDecimal total,
        @Field(type = FieldType.Date) Instant createdAt
) {
}
