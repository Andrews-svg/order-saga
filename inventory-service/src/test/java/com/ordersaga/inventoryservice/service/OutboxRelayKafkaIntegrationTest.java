package com.ordersaga.inventoryservice.service;

import com.ordersaga.inventoryservice.AbstractKafkaIntegrationTest;
import com.ordersaga.inventoryservice.domain.OutboxEvent;
import com.ordersaga.inventoryservice.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "outbox.relay.delay-ms=3600000",
        "outbox.relay.initial-delay-ms=3600000"
})
class OutboxRelayKafkaIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    OutboxEventRepository outboxRepository;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
    }

    @Test
    void publishPending_sendsRealMessageToKafka_andMarksSent() {
        String topic = "inventory.reserved.test-" + UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"productId\":\"" + aggregateId + "\",\"qty\":7}";

        OutboxEvent event = new OutboxEvent();
        event.setEventType(topic);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        Long eventId = outboxRepository.save(event).getId();

        Map<String, Object> props = new java.util.HashMap<>();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(0));

            outboxRelay.publishPending();

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo(aggregateId.toString());
            assertThat(record.value()).isEqualTo(payload);
        }

        OutboxEvent reloaded = outboxRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxRelay.STATUS_SENT);
        assertThat(reloaded.getSentAt()).isNotNull();
    }
}