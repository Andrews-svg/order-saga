package com.ordersaga.productservice.web;

import com.ordersaga.productservice.AbstractIntegrationTest;
import com.ordersaga.productservice.dto.ProductRequest;
import com.ordersaga.productservice.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void createProduct_returns201WithBody() {
        ProductRequest request = new ProductRequest("Laptop", "ThinkPad X1", new BigDecimal("1299.99"));

        ResponseEntity<ProductResponse> response =
                rest.postForEntity("/products", request, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Laptop");
        assertThat(response.getBody().description()).isEqualTo("ThinkPad X1");
        assertThat(response.getBody().price()).isEqualByComparingTo("1299.99");
        assertThat(response.getBody().createdAt()).isNotNull();
        assertThat(response.getBody().updatedAt()).isNotNull();
    }
}