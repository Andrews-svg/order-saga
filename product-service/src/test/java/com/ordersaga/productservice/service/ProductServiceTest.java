package com.ordersaga.productservice.service;

import com.ordersaga.productservice.domain.Product;
import com.ordersaga.productservice.dto.ProductRequest;
import com.ordersaga.productservice.dto.ProductResponse;
import com.ordersaga.productservice.exception.ProductNotFoundException;
import com.ordersaga.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @InjectMocks
    private ProductService service;

    @Test
    void create_shouldSaveAndReturnResponse() {
        ProductRequest request = new ProductRequest("Laptop", "Good laptop", new BigDecimal("1500.00"));

        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            OffsetDateTime now = OffsetDateTime.now();
            ReflectionTestUtils.setField(p, "createdAt", now);
            ReflectionTestUtils.setField(p, "updatedAt", now);
            return p;
        });

        ProductResponse response = service.create(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Laptop");
        assertThat(response.description()).isEqualTo("Good laptop");
        assertThat(response.price()).isEqualByComparingTo("1500.00");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        verify(repository).save(any(Product.class));
    }

    @Test
    void getById_shouldReturnResponse_whenFound() {
        UUID id = UUID.randomUUID();
        Product product = productWith(id, "Laptop", "Good laptop", "1500.00");
        when(repository.findById(id)).thenReturn(Optional.of(product));

        ProductResponse response = service.getById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Laptop");
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getAll_shouldReturnAll() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Product p1 = productWith(id1, "Laptop", "Good laptop", "1500.00");
        Product p2 = productWith(id2, "Mouse", "Wireless", "25.00");
        when(repository.findAll()).thenReturn(List.of(p1, p2));

        List<ProductResponse> result = service.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(id1);
        assertThat(result.get(0).name()).isEqualTo("Laptop");
        assertThat(result.get(1).id()).isEqualTo(id2);
        assertThat(result.get(1).name()).isEqualTo("Mouse");
        verify(repository).findAll();
    }

    @Test
    void update_shouldUpdateFieldsAndReturnResponse_whenFound() {
        UUID id = UUID.randomUUID();
        Product existing = productWith(id, "Old", "Old desc", "100.00");
        ProductRequest request = new ProductRequest("New", "New desc", new BigDecimal("200.00"));

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = service.update(id, request);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("New");
        assertThat(response.description()).isEqualTo("New desc");
        assertThat(response.price()).isEqualByComparingTo("200.00");
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void update_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new ProductRequest("X", null, BigDecimal.ONE)))
                .isInstanceOf(ProductNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void delete_shouldDelete_whenExists() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(repository).deleteById(id);
    }

    @Test
    void delete_shouldThrow_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ProductNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    private Product productWith(UUID id, String name, String description, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        return p;
    }
}