package com.ordersaga.inventoryservice.web;

import com.ordersaga.inventoryservice.dto.ReleaseRequest;
import com.ordersaga.inventoryservice.dto.ReserveRequest;
import com.ordersaga.inventoryservice.dto.StockRequest;
import com.ordersaga.inventoryservice.dto.StockResponse;
import com.ordersaga.inventoryservice.service.StockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @PutMapping("/{productId}")
    public StockResponse setStock(@PathVariable UUID productId, @Valid @RequestBody StockRequest request) {
        return service.setStock(productId, request.totalQuantity());
    }

    @PostMapping("/reserve")
    public StockResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return service.reserve(request.productId(), request.quantity());
    }

    @PostMapping("/release")
    public StockResponse release(@Valid @RequestBody ReleaseRequest request) {
        return service.release(request.productId(), request.quantity());
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable UUID productId) {
        return service.get(productId);
    }
}