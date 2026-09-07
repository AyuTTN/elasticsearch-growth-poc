package com.poc.elasticsearch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String category,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull @Min(0) Integer stock
) {
}
