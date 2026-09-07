package com.poc.elasticsearch.dto;

import com.poc.elasticsearch.model.Order;
import com.poc.elasticsearch.model.OrderItem;
import com.poc.elasticsearch.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerEmail,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        Instant createdAt
) {

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getStatus(),
                items,
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }

    public record OrderItemResponse(
            String productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice
    ) {

        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice()
            );
        }
    }
}
