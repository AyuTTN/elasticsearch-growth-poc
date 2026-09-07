package com.poc.elasticsearch.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

@Document(indexName = "products")
public record Product(
        @Id @Field(type = FieldType.Keyword) String id,
        @Field(type = FieldType.Text, analyzer = "standard") String name,
        @Field(type = FieldType.Text, analyzer = "standard") String description,
        @Field(type = FieldType.Keyword) String category,
        @Field(type = FieldType.Double) BigDecimal price,
        @Field(type = FieldType.Integer) int stock
) {
    public Product withStock(int newStock) {
        return new Product(id, name, description, category, price, newStock);
    }
}
