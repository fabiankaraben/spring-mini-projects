package com.example.batchschedulers.config;

import com.example.batchschedulers.model.Product;
import com.example.batchschedulers.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the {@code products} table with sample data on application startup.
 *
 * <p>This class implements {@link ApplicationRunner}, which Spring Boot calls
 * once after the application context has fully started. It only inserts data if
 * the table is empty, making it safe to restart without duplicating records.
 *
 * <p>The {@code @Profile("!integration-test")} annotation ensures this bean is
 * NOT active during integration tests. Integration tests manage their own data
 * using SQL scripts or programmatic inserts so they have full control over the
 * test data state.
 *
 * <p>Sample products span three categories: Electronics, Books, and Clothing.
 * Some products have low stock quantities (below {@code Product.LOW_STOCK_THRESHOLD})
 * to demonstrate the inventory audit job's low-stock flagging behaviour.
 */
@Component
@Profile("!integration-test")
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ProductRepository productRepository;

    public DataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Seeds the products table if it is empty.
     *
     * @param args application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            log.info("DataInitializer: products table already has {} records, skipping seed",
                    productRepository.count());
            return;
        }

        log.info("DataInitializer: seeding sample products...");

        List<Product> products = List.of(
                // ── Electronics (8 products) ──────────────────────────────────────────
                new Product("Laptop Pro 15",        "Electronics", new BigDecimal("1299.99"), 25),
                new Product("Wireless Mouse",       "Electronics", new BigDecimal("29.99"),   5),   // low stock
                new Product("Mechanical Keyboard",  "Electronics", new BigDecimal("89.99"),   15),
                new Product("4K Monitor 27inch",    "Electronics", new BigDecimal("449.99"),  8),   // low stock
                new Product("USB-C Hub 7-port",     "Electronics", new BigDecimal("49.99"),   30),
                new Product("Noise-Cancel Headset", "Electronics", new BigDecimal("199.99"),  12),
                new Product("Webcam HD 1080p",      "Electronics", new BigDecimal("79.99"),   3),   // low stock
                new Product("External SSD 1TB",     "Electronics", new BigDecimal("109.99"),  20),

                // ── Books (6 products) ────────────────────────────────────────────────
                new Product("Clean Code",              "Books", new BigDecimal("39.99"),  50),
                new Product("Design Patterns",         "Books", new BigDecimal("44.99"),  35),
                new Product("Spring in Action",        "Books", new BigDecimal("49.99"),  7),   // low stock
                new Product("Domain-Driven Design",    "Books", new BigDecimal("54.99"),  22),
                new Product("The Pragmatic Programmer","Books", new BigDecimal("42.99"),  40),
                new Product("Refactoring",             "Books", new BigDecimal("47.99"),  9),   // low stock

                // ── Clothing (6 products) ─────────────────────────────────────────────
                new Product("Developer Hoodie L",   "Clothing", new BigDecimal("59.99"),  18),
                new Product("Tech T-Shirt M",       "Clothing", new BigDecimal("24.99"),  45),
                new Product("Coding Cap",           "Clothing", new BigDecimal("19.99"),  6),   // low stock
                new Product("Laptop Backpack",      "Clothing", new BigDecimal("79.99"),  28),
                new Product("Desk Mat XL",          "Clothing", new BigDecimal("34.99"),  2),   // low stock
                new Product("Cable Organiser Bag",  "Clothing", new BigDecimal("14.99"),  55)
        );

        productRepository.saveAll(products);
        log.info("DataInitializer: seeded {} products", products.size());
    }
}
