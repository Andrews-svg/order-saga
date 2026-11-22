package com.ordersaga.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OutboxService {

    static final String STATUS_PENDING = "PENDING";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(String eventType, UUID aggregateId, Object payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventType(eventType);
            event.setAggregateId(aggregateId);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus(STATUS_PENDING);
            repository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}