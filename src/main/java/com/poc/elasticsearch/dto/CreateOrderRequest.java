package com.poc.elasticsearch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Email String customerEmail,
        @NotEmpty List<OrderItemRequest> items
) {

    public record OrderItemRequest(
            @NotBlank String productId,
            @NotNull @Positive Integer quantity
    ) {
    }
}
