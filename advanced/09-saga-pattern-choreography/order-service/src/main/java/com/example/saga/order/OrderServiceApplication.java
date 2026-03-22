package com.example.saga.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Order Service microservice.
 *
 * <p>The Order Service is the <em>saga initiator</em> in the choreography pattern.
 * It accepts incoming order requests from clients via REST, persists them locally,
 * and publishes domain events to Kafka. Other microservices (Payment, Inventory)
 * listen to those events and react autonomously — there is no central orchestrator.
 *
 * <p>Saga happy path:
 * <pre>
 *   Client → POST /api/orders
 *     ↓ OrderCreatedEvent (Kafka)
 *   Payment Service → PaymentProcessedEvent (Kafka)
 *     ↓
 *   Inventory Service → InventoryReservedEvent (Kafka)
 *     ↓
 *   Order Service → marks order COMPLETED
 * </pre>
 *
 * <p>Compensating transactions (rollback):
 * <pre>
 *   Payment Service fails → PaymentFailedEvent
 *     ↓ Order Service → marks order CANCELLED
 *
 *   Inventory Service fails → InventoryFailedEvent
 *     ↓ Order Service → publishes PaymentRefundEvent, marks order CANCELLED
 * </pre>
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
