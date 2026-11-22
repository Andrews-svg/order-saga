package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private OutboxEvent pendingEvent(String type) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(type);
        event.setAggregateId(UUID.randomUUID());
        event.setPayload("{\"test\":true}");
        event.setStatus("PENDING");
        return repository.save(event);
    }

    @Test
    void publishPending_marksPendingAsSent() {
        OutboxEvent e1 = pendingEvent("inventory.reserved");
        OutboxEvent e2 = pendingEvent("inventory.released");

        relay.publishPending();

        List<OutboxEvent> all = repository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).allMatch(e -> "SENT".equals(e.getStatus()));
        assertThat(all).allMatch(e -> e.getSentAt() != null);
        assertThat(repository.findByStatusOrderByCreatedAtAsc("PENDING")).isEmpty();
    }

    @Test
    void publishPending_skipsAlreadySent() {
        OutboxEvent sent = pendingEvent("inventory.reserved");
        sent.setStatus("SENT");
        sent.setSentAt(java.time.OffsetDateTime.now());
        repository.save(sent);
        pendingEvent("inventory.released");

        relay.publishPending();

        assertThat(repository.findByStatusOrderByCreatedAtAsc("PENDING")).isEmpty();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void publishPending_noop_whenNoPending() {
        relay.publishPending();

        assertThat(repository.findAll()).isEmpty();
    }
}