package com.ordersaga.inventoryservice.repository;

import com.ordersaga.inventoryservice.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}