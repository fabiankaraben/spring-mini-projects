package com.example.grpc.config;

import com.example.grpc.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Data initializer — seeds the H2 in-memory database with sample products on startup.
 *
 * <p>Why use {@link CommandLineRunner}?
 *   It runs after the Spring application context is fully started (all beans initialized,
 *   database schema created). This is the right place to insert seed data because:
 *   <ul>
 *     <li>The JPA schema is already created by {@code spring.jpa.hibernate.ddl-auto=create-drop}.</li>
 *     <li>All repository and service beans are available for injection.</li>
 *   </ul>
 *
 * <p>In production, seed data would typically be managed by Flyway or Liquibase migrations
 * rather than a CommandLineRunner.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Seed the database with a representative set of products across several categories.
     *
     * <p>The {@link CommandLineRunner} lambda is called once at application startup.
     * It delegates to {@link ProductService#createProduct} so that business rules
     * (e.g., initial status assignment) are applied consistently.
     *
     * @param productService the service used to create seed products
     * @return a CommandLineRunner that inserts sample data
     */
    @Bean
    public CommandLineRunner seedDatabase(ProductService productService) {
        return args -> {
            log.info("Seeding database with sample products...");

            // --- Electronics ---
            productService.createProduct(
                    "Wireless Mechanical Keyboard",
                    "Compact TKL layout with Cherry MX Red switches, Bluetooth 5.0 and USB-C receiver.",
                    "electronics", 89.99, 150);

            productService.createProduct(
                    "4K USB-C Monitor",
                    "27-inch IPS panel, 3840x2160 resolution, HDR400, USB-C 65W power delivery.",
                    "electronics", 499.99, 42);

            productService.createProduct(
                    "Noise-Cancelling Headphones",
                    "Over-ear ANC headphones with 30h battery, multipoint Bluetooth pairing.",
                    "electronics", 249.99, 0);  // out of stock

            productService.createProduct(
                    "USB-C Hub 7-in-1",
                    "HDMI 4K, 3x USB-A 3.0, SD/microSD, 100W PD pass-through.",
                    "electronics", 39.99, 320);

            // --- Furniture ---
            productService.createProduct(
                    "Ergonomic Office Chair",
                    "Adjustable lumbar support, 4D armrests, breathable mesh back, 5-year warranty.",
                    "furniture", 349.00, 28);

            productService.createProduct(
                    "Standing Desk Frame",
                    "Electric dual-motor height adjustment, memory presets, anti-collision sensor.",
                    "furniture", 599.00, 15);

            // --- Books ---
            productService.createProduct(
                    "Designing Data-Intensive Applications",
                    "Martin Kleppmann's guide to distributed systems, databases, and stream processing.",
                    "books", 49.95, 200);

            productService.createProduct(
                    "Clean Architecture",
                    "Robert C. Martin's principles for building maintainable software systems.",
                    "books", 39.99, 175);

            log.info("Database seeded with {} products.", 8);
        };
    }
}
