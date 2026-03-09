package com.example.kafkaproducer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Kafka Producer mini-project.
 *
 * <p>This application demonstrates how to publish (produce) events to an
 * Apache Kafka topic from a Spring Boot service using {@code KafkaTemplate}.
 *
 * <p>The application exposes a REST API that accepts event payloads and
 * forwards them to the configured Kafka topic. Kafka itself is provided via
 * Docker Compose for local development and production-like deployments.
 */
@SpringBootApplication
public class KafkaProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaProducerApplication.class, args);
    }
}
