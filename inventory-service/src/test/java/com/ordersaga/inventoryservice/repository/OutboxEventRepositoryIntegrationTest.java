package com.ordersaga.inventoryservice.repository;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    OutboxEventRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void contextLoads_flywayV3Applied_schemaValid() {
        assertThat(repository.count()).isZero();
    }

    @Test
    void saveAndFindByStatus() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setEventType("inventory.reserved");
        event.setAggregateId(aggregateId);
        event.setPayload("{\"productId\":\"" + aggregateId + "\"}");
        event.setStatus("PENDING");

        repository.save(event);

        List<OutboxEvent> pending = repository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(pending).hasSize(1);
        OutboxEvent found = pending.get(0);
        assertThat(found.getEventType()).isEqualTo("inventory.reserved");
        assertThat(found.getAggregateId()).isEqualTo(aggregateId);
        assertThat(found.getStatus()).isEqualTo("PENDING");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getSentAt()).isNull();
    }
}