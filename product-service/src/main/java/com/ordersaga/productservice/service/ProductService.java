package com.ordersaga.productservice.service;

import com.ordersaga.productservice.domain.Product;
import com.ordersaga.productservice.dto.ProductRequest;
import com.ordersaga.productservice.dto.ProductResponse;
import com.ordersaga.productservice.exception.ProductNotFoundException;
import com.ordersaga.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        Product saved = repository.save(product);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        Product updated = repository.saveAndFlush(product);
        return toResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
