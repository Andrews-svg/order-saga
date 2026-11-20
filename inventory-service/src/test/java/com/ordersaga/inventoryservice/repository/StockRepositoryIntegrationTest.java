package com.ordersaga.inventoryservice.repository;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.Stock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    StockRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void contextLoads_flywayApplied_schemaValid() {
        assertThat(repository.count()).isZero();
    }

    @Test
    void saveAndFindStock() {
        UUID productId = UUID.randomUUID();
        Stock stock = new Stock();
        stock.setProductId(productId);
        stock.setTotalQuantity(100);
        stock.setReservedQuantity(0);

        repository.save(stock);

        Stock found = repository.findById(productId).orElseThrow();
        assertThat(found.getProductId()).isEqualTo(productId);
        assertThat(found.getTotalQuantity()).isEqualTo(100);
        assertThat(found.getReservedQuantity()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}