package com.ordersaga.inventoryservice.web;

import com.ordersaga.inventoryservice.AbstractIntegrationTest;
import com.ordersaga.inventoryservice.domain.Stock;
import com.ordersaga.inventoryservice.dto.ReleaseRequest;
import com.ordersaga.inventoryservice.dto.ReserveRequest;
import com.ordersaga.inventoryservice.dto.StockRequest;
import com.ordersaga.inventoryservice.dto.StockResponse;
import com.ordersaga.inventoryservice.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    StockRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private UUID seedStock(int total, int reserved) {
        UUID id = UUID.randomUUID();
        Stock stock = new Stock();
        stock.setProductId(id);
        stock.setTotalQuantity(total);
        stock.setReservedQuantity(reserved);
        repository.save(stock);
        return id;
    }

    @Test
    void put_setStock_returns200() {
        UUID id = UUID.randomUUID();
        ResponseEntity<StockResponse> response = rest.exchange(
                "/inventory/" + id, HttpMethod.PUT,
                new HttpEntity<>(new StockRequest(100)), StockResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalQuantity()).isEqualTo(100);
        assertThat(response.getBody().availableQuantity()).isEqualTo(100);
    }

    @Test
    void reserve_happy_returns200() {
        UUID id = seedStock(100, 10);

        ResponseEntity<StockResponse> response = rest.postForEntity(
                "/inventory/reserve", new ReserveRequest(id, 20), StockResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reservedQuantity()).isEqualTo(30);
        assertThat(response.getBody().availableQuantity()).isEqualTo(70);
    }

    @Test
    void reserve_insufficient_returns409() {
        UUID id = seedStock(100, 95);

        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/reserve", new ReserveRequest(id, 10), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reserve_notFound_returns404() {
        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/reserve", new ReserveRequest(UUID.randomUUID(), 1), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void release_happy_returns200() {
        UUID id = seedStock(100, 30);

        ResponseEntity<StockResponse> response = rest.postForEntity(
                "/inventory/release", new ReleaseRequest(id, 20), StockResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reservedQuantity()).isEqualTo(10);
        assertThat(response.getBody().availableQuantity()).isEqualTo(90);
    }

    @Test
    void release_exceedsReserved_returns409() {
        UUID id = seedStock(100, 5);

        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/release", new ReleaseRequest(id, 10), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reserve_zeroQuantity_returns400() {
        UUID id = seedStock(100, 0);

        ResponseEntity<String> response = rest.postForEntity(
                "/inventory/reserve", new ReserveRequest(id, 0), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}