package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayUnitTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxRelay relay;

    private OutboxEvent pending(String type, UUID aggregateId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.setEventType(type);
        e.setAggregateId(aggregateId);
        e.setPayload(payload);
        e.setStatus(OutboxRelay.STATUS_PENDING);
        return e;
    }

    @Test
    void publishPending_sendsToKafkaAndMarksSent() {
        UUID aggId = UUID.randomUUID();
        OutboxEvent event = pending("inventory.reserved", aggId, "{\"x\":1}");
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxRelay.STATUS_PENDING))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("inventory.reserved"), eq(aggId.toString()), eq("{\"x\":1}")))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        relay.publishPending();

        verify(kafkaTemplate).send(eq("inventory.reserved"), eq(aggId.toString()), eq("{\"x\":1}"));
        assertThat(event.getStatus()).isEqualTo(OutboxRelay.STATUS_SENT);
        assertThat(event.getSentAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void publishPending_marksFailedAndContinues_whenSendThrows() {
        OutboxEvent failing = pending("inventory.reserved", UUID.randomUUID(), "{\"a\":1}");
        OutboxEvent ok = pending("inventory.released", UUID.randomUUID(), "{\"b\":2}");
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxRelay.STATUS_PENDING))
                .thenReturn(List.of(failing, ok));
        when(kafkaTemplate.send(eq("inventory.reserved"), anyString(), anyString()))
                .thenThrow(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(eq("inventory.released"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        relay.publishPending();

        assertThat(failing.getStatus()).isEqualTo(OutboxRelay.STATUS_FAILED);
        assertThat(ok.getStatus()).isEqualTo(OutboxRelay.STATUS_SENT);
    }

    @Test
    void publishPending_marksFailed_whenSendFutureFails() {
        OutboxEvent event = pending("inventory.reserved", UUID.randomUUID(), "{\"x\":1}");
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxRelay.STATUS_PENDING))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka async down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        relay.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxRelay.STATUS_FAILED);
    }

    @Test
    void publishPending_noop_whenNoPending() {
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxRelay.STATUS_PENDING))
                .thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
