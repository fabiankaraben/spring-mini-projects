package com.example.stripepayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Stripe Payment API mini-project.
 *
 * <p>This application demonstrates how to integrate the Stripe Java SDK
 * with a Spring Boot backend to process payments.
 *
 * <p>Key design choices:
 * <ul>
 *   <li>Stripe Java SDK – the official Stripe client library for server-side operations.</li>
 *   <li>PostgreSQL – stores local payment records for auditing and retrieval.</li>
 *   <li>Testcontainers – spins up a real PostgreSQL instance during integration tests.</li>
 *   <li>WireMock-style approach – Stripe API calls are mocked in unit tests so no
 *       real Stripe credentials are needed during testing.</li>
 * </ul>
 *
 * <p>Stripe operations covered:
 * <ul>
 *   <li>Create a PaymentIntent (initiates a payment flow).</li>
 *   <li>Confirm a PaymentIntent (simulates the client confirming the payment).</li>
 *   <li>Retrieve a PaymentIntent by its Stripe ID.</li>
 *   <li>Cancel a PaymentIntent.</li>
 *   <li>List all local payment records stored in PostgreSQL.</li>
 * </ul>
 */
@SpringBootApplication
public class StripePaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(StripePaymentApplication.class, args);
    }
}
