package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SENT = "SENT";
    static final String STATUS_FAILED = "FAILED";
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(initialDelayString = "${outbox.relay.initial-delay-ms:0}", fixedDelayString = "${outbox.relay.delay-ms:5000}")
    public void publishPending() {
        List<OutboxEvent> pending = repository.findByStatusOrderByCreatedAtAsc(STATUS_PENDING);
        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.setStatus(STATUS_SENT);
            event.setSentAt(OffsetDateTime.now());
            repository.save(event);
        } catch (Exception ex) {
            log.warn("Outbox publish failed: id={}, type={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId(), ex);
            event.setStatus(STATUS_FAILED);
            repository.save(event);
        }
    }
}