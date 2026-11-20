package com.ordersaga.inventoryservice.web;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.Stock;
import com.ordersaga.inventoryservice.dto.ReleaseRequest;
import com.ordersaga.inventoryservice.dto.ReserveRequest;
import com.ordersaga.inventoryservice.dto.StockResponse;
import com.ordersaga.inventoryservice.repository.IdempotencyKeyRepository;
import com.ordersaga.inventoryservice.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    IdempotencyKeyRepository keyRepository;

    @BeforeEach
    void cleanUp() {
        stockRepository.deleteAll();
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

    private ResponseEntity<StockResponse> postReserve(String key, ReserveRequest body) {
        HttpHeaders headers = new HttpHeaders();
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange("/inventory/reserve", HttpMethod.POST,
                new HttpEntity<>(body, headers), StockResponse.class);
    }

    @Test
    void reserve_sameKeySamePayload_returnsSameResponseAndReservesOnce() {
        UUID id = seedStock(100, 0);
        ReserveRequest request = new ReserveRequest(id, 30);
        String key = UUID.randomUUID().toString();

        ResponseEntity<StockResponse> first = postReserve(key, request);
        ResponseEntity<StockResponse> second = postReserve(key, request);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(second.getBody().reservedQuantity()).isEqualTo(30);

        Stock db = stockRepository.findById(id).orElseThrow();
        assertThat(db.getReservedQuantity()).isEqualTo(30);
        assertThat(keyRepository.count()).isEqualTo(1);
    }

    @Test
    void reserve_sameKeyDifferentPayload_returns409() {
        UUID id1 = seedStock(100, 0);
        UUID id2 = seedStock(100, 0);
        String key = UUID.randomUUID().toString();

        ResponseEntity<StockResponse> first = postReserve(key, new ReserveRequest(id1, 10));
        ResponseEntity<StockResponse> second = postReserve(key, new ReserveRequest(id2, 20));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Stock db1 = stockRepository.findById(id1).orElseThrow();
        Stock db2 = stockRepository.findById(id2).orElseThrow();
        assertThat(db1.getReservedQuantity()).isEqualTo(10);
        assertThat(db2.getReservedQuantity()).isZero();
    }

    @Test
    void reserve_withoutKey_worksAsPlainCall() {
        UUID id = seedStock(100, 0);

        ResponseEntity<StockResponse> response = postReserve(null, new ReserveRequest(id, 25));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reservedQuantity()).isEqualTo(25);
        assertThat(keyRepository.count()).isZero();
    }

    @Test
    void release_sameKeySamePayload_idempotent() {
        UUID id = seedStock(100, 50);
        ReleaseRequest request = new ReleaseRequest(id, 30);
        String key = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        ResponseEntity<StockResponse> first = rest.exchange("/inventory/release", HttpMethod.POST,
                new HttpEntity<>(request, headers), StockResponse.class);
        ResponseEntity<StockResponse> second = rest.exchange("/inventory/release", HttpMethod.POST,
                new HttpEntity<>(request, headers), StockResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(second.getBody().reservedQuantity()).isEqualTo(20);

        Stock db = stockRepository.findById(id).orElseThrow();
        assertThat(db.getReservedQuantity()).isEqualTo(20);
    }
}