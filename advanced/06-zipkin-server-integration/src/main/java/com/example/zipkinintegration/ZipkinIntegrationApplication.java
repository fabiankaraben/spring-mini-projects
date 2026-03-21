package com.example.zipkinintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Zipkin Server Integration mini-project.
 *
 * <p>This application demonstrates how to export distributed tracing spans
 * to a Zipkin server using Spring Boot's auto-configuration for Micrometer
 * Tracing and the Brave reporter.
 *
 * <p>Key components in this project:
 * <ul>
 *   <li><strong>Micrometer Tracing (Brave bridge)</strong> – instruments every
 *       incoming HTTP request and outgoing RestTemplate call with a trace ID
 *       and span ID, then propagates them via HTTP headers (B3 format).</li>
 *   <li><strong>Zipkin Reporter</strong> – buffers completed spans and sends
 *       them asynchronously to the Zipkin HTTP collector endpoint.</li>
 *   <li><strong>Order Service</strong> – a simulated service with multiple
 *       tiers (controller → service → inventory sub-service) so that parent
 *       and child spans appear in the Zipkin UI as a trace tree.</li>
 * </ul>
 */
@SpringBootApplication
public class ZipkinIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZipkinIntegrationApplication.class, args);
    }
}
