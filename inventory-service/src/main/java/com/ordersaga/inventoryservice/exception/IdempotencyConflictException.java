package com.ordersaga.inventoryservice.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key already used with different payload: " + key);
    }
}