package com.example.saga.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Inventory Service microservice.
 *
 * <p>The Inventory Service is a <em>saga participant</em> in the choreography pattern.
 * It reacts to payment success events and attempts to reserve the requested stock.
 *
 * <p>Event flow:
 * <pre>
 *   Consumes: payment.processed     → reserves stock for the order
 *     → success: publishes inventory.reserved
 *     → failure: publishes inventory.failed  (triggers compensation in Order Service)
 * </pre>
 *
 * <p>Inventory simulation:
 *   Each product is pre-seeded with 10 units of stock on first access.
 *   A reservation succeeds if the available stock {@code >=} requested quantity.
 *   A reservation fails if the available stock {@code <} requested quantity.
 *
 * <p>Example: product "prod-A" has 10 units.
 * <ul>
 *   <li>Order for 5 units → succeeds (10 - 5 = 5 remaining).</li>
 *   <li>Next order for 6 units → fails (only 5 remaining).</li>
 * </ul>
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
