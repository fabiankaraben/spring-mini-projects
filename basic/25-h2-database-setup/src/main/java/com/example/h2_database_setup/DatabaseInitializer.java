package com.example.h2_database_setup;

import com.example.h2_database_setup.model.Product;
import com.example.h2_database_setup.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * DatabaseInitializer configures the embedded H2 database and queries it on
 * startup.
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final ProductRepository productRepository;

    public DatabaseInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("--- Starting Database Initialization ---");

        // Save some initial data to the H2 database
        productRepository.save(new Product("Laptop", 1200.00));
        productRepository.save(new Product("Smartphone", 800.00));

        log.info("--- Initial Data Inserted ---");

        // Querying data on startup
        log.info("--- Querying Embedded H2 Database Data ---");
        for (Product product : productRepository.findAll()) {
            log.info("Found Product: {}", product);
        }
        log.info("--- Database Query Finished ---");
    }
}
