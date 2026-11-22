package com.ordersaga.inventoryservice.web;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.domain.Stock;
import com.ordersaga.inventoryservice.dto.ReleaseRequest;
import com.ordersaga.inventoryservice.dto.ReserveRequest;
import com.ordersaga.inventoryservice.repository.IdempotencyKeyRepository;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import com.ordersaga.inventoryservice.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    OutboxEventRepository outboxRepository;

    @Autowired
    IdempotencyKeyRepository keyRepository;

    @BeforeEach
    void cleanUp() {
        stockRepository.deleteAll();
        outboxRepository.deleteAll();
        keyRepository.deleteAll();
    }

    private UUID seedStock(int total, int reserved) {
        UUID id = UUID.randomUUID();
        Stock stock = new Stock();
        stock.setProductId(id);
        stock.setTotalQuantity(total);
        stock.setReservedQuantity(reserved);
        stockRepository.save(stock);
        return id;
    }

    @Test
    void reserve_createsPendingOutboxEvent() {
        UUID id = seedStock(100, 0);

        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/reserve", new ReserveRequest(id, 30), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("inventory.reserved");
        assertThat(event.getAggregateId()).isEqualTo(id);
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPayload()).contains("\"reservedQuantity\":30");
        assertThat(event.getSentAt()).isNull();
    }

    @Test
    void release_createsPendingOutboxEvent() {
        UUID id = seedStock(100, 50);

        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/release", new ReleaseRequest(id, 30), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("inventory.released");
        assertThat(events.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void reserve_insufficient_doesNotCreateOutboxEvent() {
        UUID id = seedStock(100, 95);

        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/reserve", new ReserveRequest(id, 10), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(outboxRepository.findAll()).isEmpty();
    }
}