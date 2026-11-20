package com.ordersaga.inventoryservice.repository;

import com.ordersaga.inventoryservice.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
}