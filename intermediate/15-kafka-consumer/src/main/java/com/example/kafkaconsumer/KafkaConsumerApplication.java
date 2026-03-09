package com.example.kafkaconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Kafka Consumer Spring Boot application.
 *
 * <p>{@link SpringBootApplication} is a meta-annotation that enables:
 * <ul>
 *   <li>{@code @Configuration}   – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – activates Spring Boot's auto-configuration
 *       mechanism, which wires up Kafka, Jackson, and MVC automatically based on
 *       classpath contents and {@code application.yml} properties.</li>
 *   <li>{@code @ComponentScan}   – scans the current package and sub-packages for
 *       Spring-managed components (@Component, @Service, @RestController, etc.).</li>
 * </ul>
 *
 * <h2>What this application demonstrates</h2>
 * <p>This mini-project shows how to consume events from a Kafka topic using
 * Spring Kafka's {@code @KafkaListener} annotation. When an {@link com.example.kafkaconsumer.domain.OrderEvent}
 * message arrives on the configured topic, the listener deserialises it from JSON
 * and hands it to the service layer for processing.
 *
 * <p>A companion REST API (GET /api/events) lets you query the events that have
 * been received and processed so far, making it easy to observe the consumer
 * behaviour without needing Kafka CLI tools.
 */
@SpringBootApplication
public class KafkaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerApplication.class, args);
    }
}
