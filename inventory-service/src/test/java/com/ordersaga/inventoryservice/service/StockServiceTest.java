package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.domain.Stock;
import com.ordersaga.inventoryservice.dto.StockResponse;
import com.ordersaga.inventoryservice.exception.InsufficientStockException;
import com.ordersaga.inventoryservice.exception.StockNotFoundException;
import com.ordersaga.inventoryservice.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository repository;

    @Mock
    private OutboxService outbox;

    @InjectMocks
    private StockService service;

    private Stock stock(UUID productId, int total, int reserved) {
        Stock s = new Stock();
        s.setProductId(productId);
        s.setTotalQuantity(total);
        s.setReservedQuantity(reserved);
        return s;
    }

    @Test
    void reserve_shouldIncreaseReserved_whenAvailableEnough() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(stock(id, 100, 10)));
        when(repository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        StockResponse response = service.reserve(id, 20);

        assertThat(response.totalQuantity()).isEqualTo(100);
        assertThat(response.reservedQuantity()).isEqualTo(30);
        assertThat(response.availableQuantity()).isEqualTo(70);
        verify(outbox).record(eq(StockService.EVENT_RESERVED), eq(id), any(StockResponse.class));
    }

    @Test
    void reserve_shouldThrow_whenInsufficient() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(stock(id, 100, 95)));

        assertThatThrownBy(() -> service.reserve(id, 10))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available=5")
                .hasMessageContaining("requested=10");
    }

    @Test
    void reserve_shouldThrow_whenStockNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve(id, 1))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void release_shouldDecreaseReserved_whenEnoughReserved() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(stock(id, 100, 30)));
        when(repository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        StockResponse response = service.release(id, 20);

        assertThat(response.reservedQuantity()).isEqualTo(10);
        assertThat(response.availableQuantity()).isEqualTo(90);
        verify(outbox).record(eq(StockService.EVENT_RELEASED), eq(id), any(StockResponse.class));
    }

    @Test
    void release_shouldThrow_whenExceedsReserved() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(stock(id, 100, 5)));

        assertThatThrownBy(() -> service.release(id, 10))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("only 5 reserved");
    }

    @Test
    void get_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void setStock_shouldCreateNewStock_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        StockResponse response = service.setStock(id, 50);

        assertThat(response.totalQuantity()).isEqualTo(50);
        assertThat(response.reservedQuantity()).isZero();
        assertThat(response.availableQuantity()).isEqualTo(50);
    }
}