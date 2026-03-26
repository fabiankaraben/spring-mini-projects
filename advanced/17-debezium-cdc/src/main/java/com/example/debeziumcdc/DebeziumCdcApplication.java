package com.example.debeziumcdc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Debezium CDC (Change Data Capture) mini-project.
 *
 * <p>This application demonstrates how to capture database changes in real-time
 * using the <strong>Debezium Embedded Engine</strong> inside a Spring Boot application.
 *
 * <p>Architecture overview:
 * <pre>
 *   REST Client
 *       │  POST/PUT/DELETE /api/products
 *       ▼
 *   ProductController  ──►  ProductService  ──►  ProductRepository (JPA)
 *                                                         │
 *                                                         ▼
 *                                                    PostgreSQL
 *                                                  (WAL enabled)
 *                                                         │
 *                                                         ▼
 *                                          Debezium Embedded Engine
 *                                          (reads logical replication slot)
 *                                                         │
 *                                                         ▼
 *                                            CdcEventDispatcher
 *                                          (translates SourceRecord
 *                                           → ProductCdcEvent)
 *                                                         │
 *                                                         ▼
 *                                                 KafkaTemplate
 *                                                         │
 *                                                         ▼
 *                                          Kafka topic: product-cdc-events
 *                                                         │
 *                                                         ▼
 *                                             CdcEventConsumer (demo)
 *                                           (logs every received event)
 * </pre>
 *
 * <p>Key components:
 * <ul>
 *   <li>{@code Product} — JPA entity representing a product in the catalogue.</li>
 *   <li>{@code ProductController} — REST API for CRUD operations on products.</li>
 *   <li>{@code DebeziumConnectorConfig} — configures and starts the embedded engine.</li>
 *   <li>{@code CdcEventDispatcher} — bridges Debezium SourceRecord events to Kafka.</li>
 *   <li>{@code CdcEventConsumer} — demo Kafka consumer that logs CDC events.</li>
 * </ul>
 */
@SpringBootApplication
public class DebeziumCdcApplication {

    public static void main(String[] args) {
        SpringApplication.run(DebeziumCdcApplication.class, args);
    }
}
