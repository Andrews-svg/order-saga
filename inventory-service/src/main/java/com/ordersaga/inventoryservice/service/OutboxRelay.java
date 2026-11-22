package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SENT = "SENT";

    private final OutboxEventRepository repository;

    public OutboxRelay(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.findByStatusOrderByCreatedAtAsc(STATUS_PENDING);
        for (OutboxEvent event : pending) {
            log.info("Publishing outbox event: type={}, aggregateId={}, payload={}",
                    event.getEventType(), event.getAggregateId(), event.getPayload());
            event.setStatus(STATUS_SENT);
            event.setSentAt(OffsetDateTime.now());
            repository.save(event);
        }
    }
}