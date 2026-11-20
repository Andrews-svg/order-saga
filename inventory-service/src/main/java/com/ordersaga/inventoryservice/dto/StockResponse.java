package com.ordersaga.inventoryservice.dto;

import java.util.UUID;

public record StockResponse(
        UUID productId,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity
) {
}