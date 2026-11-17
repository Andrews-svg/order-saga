package com.ordersaga.productservice.web;

import com.ordersaga.productservice.AbstractIntegrationTest;
import com.ordersaga.productservice.dto.ProductRequest;
import com.ordersaga.productservice.dto.ProductResponse;
import com.ordersaga.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ProductRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private ProductResponse createProduct(String name, String description, String price) {
        ProductRequest request = new ProductRequest(name, description, new BigDecimal(price));
        ResponseEntity<ProductResponse> response =
                rest.postForEntity("/products", request, ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void createProduct_returns201WithBody() {
        ProductResponse body = createProduct("Laptop", "ThinkPad X1", "1299.99");
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Laptop");
        assertThat(body.description()).isEqualTo("ThinkPad X1");
        assertThat(body.price()).isEqualByComparingTo("1299.99");
        assertThat(body.createdAt()).isNotNull();
        assertThat(body.updatedAt()).isNotNull();
    }

    @Test
    void getById_returns200() {
        ProductResponse created = createProduct("Laptop", "ThinkPad X1", "1299.99");

        ResponseEntity<ProductResponse> response =
                rest.getForEntity("/products/" + created.id(), ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(created.id());
        assertThat(response.getBody().name()).isEqualTo("Laptop");
        assertThat(response.getBody().price()).isEqualByComparingTo("1299.99");
    }

    @Test
    void getById_notFound_returns404() {
        ResponseEntity<String> response =
                rest.getForEntity("/products/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAll_returnsList() {
        ProductResponse created = createProduct("Laptop", "ThinkPad X1", "1299.99");

        ResponseEntity<ProductResponse[]> response =
                rest.getForEntity("/products", ProductResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<ProductResponse> body = List.of(response.getBody());
        assertThat(body).hasSize(1);
        assertThat(body.get(0).id()).isEqualTo(created.id());
    }

    @Test
    void update_returns200WithUpdatedBody() {
        ProductResponse created = createProduct("Laptop", "ThinkPad X1", "1299.99");
        ProductRequest update = new ProductRequest("Laptop Pro", "Updated", new BigDecimal("1499.00"));

        ResponseEntity<ProductResponse> response =
                rest.exchange("/products/" + created.id(), HttpMethod.PUT,
                        new HttpEntity<>(update), ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(created.id());
        assertThat(response.getBody().name()).isEqualTo("Laptop Pro");
        assertThat(response.getBody().description()).isEqualTo("Updated");
        assertThat(response.getBody().price()).isEqualByComparingTo("1499.00");
    }

    @Test
    void update_notFound_returns404() {
        ProductRequest update = new ProductRequest("Ghost", null, new BigDecimal("1.00"));

        ResponseEntity<String> response =
                rest.exchange("/products/" + UUID.randomUUID(), HttpMethod.PUT,
                        new HttpEntity<>(update), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_returns204() {
        ProductResponse created = createProduct("Laptop", "ThinkPad X1", "1299.99");

        ResponseEntity<Void> response =
                rest.exchange("/products/" + created.id(), HttpMethod.DELETE,
                        null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_notFound_returns404() {
        ResponseEntity<String> response =
                rest.exchange("/products/" + UUID.randomUUID(), HttpMethod.DELETE,
                        null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_blankName_returns400() {
        ProductRequest request = new ProductRequest("", null, new BigDecimal("1.00"));

        ResponseEntity<String> response =
                rest.postForEntity("/products", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_nullPrice_returns400() {
        ProductRequest request = new ProductRequest("Laptop", null, null);

        ResponseEntity<String> response =
                rest.postForEntity("/products", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}