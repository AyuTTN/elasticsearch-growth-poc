package com.poc.elasticsearch.dto;

import com.poc.elasticsearch.model.Order;
import com.poc.elasticsearch.model.OrderItem;
import com.poc.elasticsearch.model.OrderStatus;
import com.poc.elasticsearch.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        String id,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stock,
        Instant createdAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getStock(),
                product.getCreatedAt()
        );
    }
}
