package com.example.serverless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Serverless Spring Function application.
 *
 * <p>When running locally (with spring-boot-starter-web on the classpath),
 * Spring Cloud Function auto-exposes every {@link java.util.function.Function},
 * {@link java.util.function.Consumer}, and {@link java.util.function.Supplier}
 * bean as an HTTP POST endpoint at {@code /{beanName}}.
 *
 * <p>The same function beans are reused for AWS Lambda deployment by swapping
 * the web adapter for the AWS adapter — zero application code changes required.
 *
 * <p>Functions defined in this project:
 * <ul>
 *   <li>{@code calculateTax}    — POST /calculateTax     — computes tax for an order</li>
 *   <li>{@code applyDiscount}   — POST /applyDiscount    — applies a discount code</li>
 *   <li>{@code generateInvoice} — POST /generateInvoice  — produces a full invoice</li>
 *   <li>{@code auditLogger}     — POST /auditLogger      — logs audit events (no response body)</li>
 * </ul>
 */
@SpringBootApplication
public class ServerlessFunctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerlessFunctionApplication.class, args);
    }
}
