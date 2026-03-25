package com.example.grpcclient.config;

import com.example.grpcclient.domain.InventoryItem;
import com.example.grpcclient.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seed component that pre-populates the H2 in-memory database with sample
 * inventory data on application startup.
 *
 * <p>{@link ApplicationRunner#run} is called by Spring Boot after the full
 * application context is initialized — including JPA schema creation — so
 * it is safe to persist entities here.
 *
 * <p>Why seed data?
 *   This is a demo project. Pre-seeded inventory makes the REST API immediately
 *   usable without requiring manual setup steps. The curl examples in the README
 *   reference these specific SKUs.
 *
 * <p>Why {@code @Profile("!test")}?
 *   The "test" profile uses a separate test context with controlled data per test.
 *   Excluding this initializer from the test profile prevents it from interfering
 *   with integration tests that set up their own data.
 */
@Component
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * JPA repository used to persist the seed inventory items.
     */
    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Constructor injection.
     *
     * @param inventoryItemRepository the JPA repository for inventory items
     */
    public DataInitializer(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Seed the database with sample inventory items.
     *
     * <p>Each item has:
     * <ul>
     *   <li>A SKU (Stock Keeping Unit) — used as the primary key.</li>
     *   <li>A human-readable product name.</li>
     *   <li>A total quantity (units physically in warehouse).</li>
     *   <li>A reserved quantity (units already held for orders — starts at 0).</li>
     * </ul>
     *
     * <p>Available quantity = total - reserved. All items start with 0 reserved.
     *
     * @param args Spring Boot application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        // Only seed if the database is empty (idempotent — safe to call multiple times).
        if (inventoryItemRepository.count() > 0) {
            log.info("DataInitializer: inventory already seeded, skipping.");
            return;
        }

        log.info("DataInitializer: seeding inventory items...");

        // Electronics
        inventoryItemRepository.save(new InventoryItem(
                "SKU-LAPTOP-001", "Laptop Pro 15", 50, 0));
        inventoryItemRepository.save(new InventoryItem(
                "SKU-LAPTOP-002", "Laptop Air 13", 30, 0));
        inventoryItemRepository.save(new InventoryItem(
                "SKU-MOUSE-001", "Wireless Mouse", 200, 0));
        inventoryItemRepository.save(new InventoryItem(
                "SKU-KEYBOARD-001", "Mechanical Keyboard", 150, 0));
        inventoryItemRepository.save(new InventoryItem(
                "SKU-MONITOR-001", "4K Monitor 27\"", 40, 0));

        // Peripherals
        inventoryItemRepository.save(new InventoryItem(
                "SKU-HEADPHONES-001", "ANC Headphones", 80, 0));
        inventoryItemRepository.save(new InventoryItem(
                "SKU-WEBCAM-001", "HD Webcam 1080p", 60, 0));

        // Limited stock item (for testing insufficient stock scenario)
        inventoryItemRepository.save(new InventoryItem(
                "SKU-GPU-001", "Graphics Card RTX", 5, 0));

        // Out-of-stock item (total = reserved = 0 means nothing available)
        inventoryItemRepository.save(new InventoryItem(
                "SKU-CONSOLE-001", "Gaming Console", 10, 10));

        log.info("DataInitializer: seeded {} inventory items.", inventoryItemRepository.count());
    }
}
