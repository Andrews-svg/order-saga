package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.domain.Stock;
import com.ordersaga.inventoryservice.dto.StockResponse;
import com.ordersaga.inventoryservice.exception.InsufficientStockException;
import com.ordersaga.inventoryservice.exception.StockNotFoundException;
import com.ordersaga.inventoryservice.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StockService {

    private final StockRepository repository;

    public StockService(StockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StockResponse setStock(UUID productId, int totalQuantity) {
        Stock stock = repository.findById(productId).orElseGet(() -> {
            Stock s = new Stock();
            s.setProductId(productId);
            s.setReservedQuantity(0);
            return s;
        });
        stock.setTotalQuantity(totalQuantity);
        if (stock.getReservedQuantity() > totalQuantity) {
            throw new InsufficientStockException(
                    "Cannot set total=" + totalQuantity
                            + " below already reserved=" + stock.getReservedQuantity()
                            + " for product " + productId);
        }
        return toResponse(repository.save(stock));
    }

    @Transactional
    public StockResponse reserve(UUID productId, int quantity) {
        Stock stock = repository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
        int available = stock.getTotalQuantity() - stock.getReservedQuantity();
        if (available < quantity) {
            throw new InsufficientStockException(productId, available, quantity);
        }
        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        return toResponse(repository.save(stock));
    }

    @Transactional
    public StockResponse release(UUID productId, int quantity) {
        Stock stock = repository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
        if (stock.getReservedQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Cannot release " + quantity
                            + " for product " + productId
                            + ": only " + stock.getReservedQuantity() + " reserved");
        }
        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        return toResponse(repository.save(stock));
    }

    @Transactional(readOnly = true)
    public StockResponse get(UUID productId) {
        Stock stock = repository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
        return toResponse(stock);
    }

    private StockResponse toResponse(Stock stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getTotalQuantity(),
                stock.getReservedQuantity(),
                stock.getTotalQuantity() - stock.getReservedQuantity()
        );
    }
}