package com.ordersaga.inventoryservice.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int available, int requested) {
        super("Insufficient stock for product " + productId
                + ": available=" + available + ", requested=" + requested);
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}