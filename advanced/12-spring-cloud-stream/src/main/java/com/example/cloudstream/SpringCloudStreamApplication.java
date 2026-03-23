package com.example.cloudstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Cloud Stream mini-project.
 *
 * <p>This application demonstrates the Spring Cloud Stream <em>functional programming model</em>
 * for building event-driven microservices. Instead of writing broker-specific code
 * (e.g. KafkaTemplate, @KafkaListener), you define plain Java functions:
 *
 * <ul>
 *   <li>{@link java.util.function.Supplier}  — produces messages (outbound binding)</li>
 *   <li>{@link java.util.function.Consumer}  — consumes messages (inbound binding)</li>
 *   <li>{@link java.util.function.Function}  — transforms messages (inbound + outbound binding)</li>
 * </ul>
 *
 * <p>Spring Cloud Stream automatically:
 * <ol>
 *   <li>Discovers these beans in the application context.</li>
 *   <li>Creates Kafka topic bindings according to {@code application.yml}.</li>
 *   <li>Serializes/deserializes message payloads as JSON.</li>
 *   <li>Manages consumer group offsets and retries.</li>
 * </ol>
 *
 * <p>Domain pipeline in this project:
 * <pre>
 *   REST Client
 *       │  POST /api/orders
 *       ▼
 *   OrderController
 *       │  saves order to in-memory store, triggers Supplier
 *       ▼
 *   orderSupplier (Supplier) ─────► Kafka topic: orders
 *                                             │
 *                                             ▼
 *                               orderProcessor (Function)
 *                               enriches/validates order
 *                                             │
 *                                             ▼
 *                               Kafka topic: orders-processed
 *                                             │
 *                                             ▼
 *                         notificationConsumer (Consumer)
 *                         logs simulated email/SMS notification
 * </pre>
 */
@SpringBootApplication
public class SpringCloudStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudStreamApplication.class, args);
    }
}
