package com.ordersaga.inventoryservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractKafkaIntegrationTest extends AbstractIntegrationTest {

    @ServiceConnection
    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withImagePullPolicy(image -> System.getenv("CI") != null);

    static {
        KAFKA.start();
    }
}