package com.example.cassandraintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Cassandra Integration Spring Boot application.
 *
 * <p>This application demonstrates how to connect a Spring Boot backend to
 * Apache Cassandra — a distributed wide-column NoSQL database — using
 * Spring Data Cassandra.</p>
 *
 * <p>Key concepts covered:</p>
 * <ul>
 *   <li>Defining Cassandra tables via {@code @Table} entity annotations</li>
 *   <li>Using {@code @PrimaryKey} with composite partition and clustering keys</li>
 *   <li>CassandraRepository for standard CRUD operations</li>
 *   <li>Custom CQL queries using {@code @Query} annotations</li>
 *   <li>Keyspace auto-creation via application configuration</li>
 * </ul>
 *
 * <p>Domain: A simple product catalog where products belong to categories.
 * Cassandra's wide-column model is a natural fit for time-series and
 * category-based lookups.</p>
 */
@SpringBootApplication
public class CassandraIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CassandraIntegrationApplication.class, args);
    }
}
