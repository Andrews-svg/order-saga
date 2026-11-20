package com.ordersaga.inventoryservice.repository;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.IdempotencyKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    IdempotencyKeyRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void contextLoads_flywayV2Applied_schemaValid() {
        assertThat(repository.count()).isZero();
    }

    @Test
    void saveAndFindKey() {
        IdempotencyKey key = new IdempotencyKey();
        key.setIdempotencyKey("test-key-1");
        key.setRequestHash("abc123");
        key.setResponseBody("{\"productId\":\"x\"}");
        key.setResponseStatus(200);

        repository.save(key);

        IdempotencyKey found = repository.findById("test-key-1").orElseThrow();
        assertThat(found.getRequestHash()).isEqualTo("abc123");
        assertThat(found.getResponseStatus()).isEqualTo(200);
        assertThat(found.getCreatedAt()).isNotNull();
    }
}