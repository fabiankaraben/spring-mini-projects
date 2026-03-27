package com.example.camel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Apache Camel Pipes mini-project.
 *
 * <p>This application demonstrates the <em>Pipes and Filters</em> Enterprise Integration Pattern
 * (EIP) using Apache Camel. A message (an order payload sent via REST) travels through a
 * sequential pipeline of independent processing stages:
 *
 * <ol>
 *   <li><b>Validation filter</b> — rejects malformed or incomplete orders immediately.</li>
 *   <li><b>Enrichment filter</b> — attaches computed metadata (e.g. VAT, region code).</li>
 *   <li><b>Classification filter</b> — tags the order as STANDARD or PRIORITY.</li>
 *   <li><b>Dispatch filter</b> — routes to the correct JMS queue based on classification.</li>
 *   <li><b>Persistence filter</b> — writes a JSON snapshot to the file-system output directory.</li>
 * </ol>
 *
 * <p>Failed messages are routed to a dead-letter channel (SEDA queue) where they are logged
 * and counted, keeping the main pipeline clean.
 *
 * <p>{@link EnableConfigurationProperties} is <em>not</em> required here because the property
 * class is annotated with {@code @ConfigurationProperties} and auto-detected via component scan.
 */
@SpringBootApplication
public class ApacheCamelPipesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApacheCamelPipesApplication.class, args);
    }
}
