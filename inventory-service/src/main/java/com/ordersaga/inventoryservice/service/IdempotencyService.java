package com.ordersaga.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersaga.inventoryservice.domain.IdempotencyKey;
import com.ordersaga.inventoryservice.exception.IdempotencyConflictException;
import com.ordersaga.inventoryservice.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(String idempotencyKey, Object payload, Class<T> responseType, Supplier<T> action) {
        String requestHash = hash(payload);

        Optional<IdempotencyKey> existing = repository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return resolve(existing.get(), requestHash, responseType);
        }

        T result = action.get();
        save(idempotencyKey, requestHash, result);
        return result;
    }

    private <T> T resolve(IdempotencyKey existing, String requestHash, Class<T> responseType) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        try {
            return objectMapper.readValue(existing.getResponseBody(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize stored response", e);
        }
    }

    private void save(String key, String requestHash, Object response) {
        try {
            IdempotencyKey entity = new IdempotencyKey();
            entity.setIdempotencyKey(key);
            entity.setRequestHash(requestHash);
            entity.setResponseBody(objectMapper.writeValueAsString(response));
            entity.setResponseStatus(200);
            repository.save(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }

    String hash(Object payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute request hash", e);
        }
    }
}