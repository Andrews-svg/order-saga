package com.ordersaga.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must not exceed 255 characters")
        String name,

        @Size(max = 10000, message = "description must not exceed 10000 characters")
        String description,

        @NotNull(message = "price is required")
        @Positive(message = "price must be positive")
        BigDecimal price
) {
}
