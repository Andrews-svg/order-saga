package com.ordersaga.inventoryservice.web;

import com.ordersaga.inventoryservice.dto.ReleaseRequest;
import com.ordersaga.inventoryservice.dto.ReserveRequest;
import com.ordersaga.inventoryservice.dto.StockRequest;
import com.ordersaga.inventoryservice.dto.StockResponse;
import com.ordersaga.inventoryservice.service.IdempotencyService;
import com.ordersaga.inventoryservice.service.StockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class StockController {

    private final StockService service;
    private final IdempotencyService idempotency;

    public StockController(StockService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PutMapping("/{productId}")
    public StockResponse setStock(@PathVariable UUID productId, @Valid @RequestBody StockRequest request) {
        return service.setStock(productId, request.totalQuantity());
    }

    @PostMapping("/reserve")
    public StockResponse reserve(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReserveRequest request) {
        if (idempotencyKey == null) {
            return service.reserve(request.productId(), request.quantity());
        }
        return idempotency.execute(idempotencyKey, request, StockResponse.class,
                () -> service.reserve(request.productId(), request.quantity()));
    }

    @PostMapping("/release")
    public StockResponse release(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReleaseRequest request) {
        if (idempotencyKey == null) {
            return service.release(request.productId(), request.quantity());
        }
        return idempotency.execute(idempotencyKey, request, StockResponse.class,
                () -> service.release(request.productId(), request.quantity()));
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable UUID productId) {
        return service.get(productId);
    }
}