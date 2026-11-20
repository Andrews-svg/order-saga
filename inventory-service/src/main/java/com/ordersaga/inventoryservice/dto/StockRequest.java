package com.ordersaga.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockRequest(
        @NotNull(message = "totalQuantity is required")
        @PositiveOrZero(message = "totalQuantity must be >= 0")
        Integer totalQuantity
) {
}