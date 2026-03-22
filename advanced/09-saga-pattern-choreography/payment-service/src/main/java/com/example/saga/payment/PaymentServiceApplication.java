package com.example.saga.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Payment Service microservice.
 *
 * <p>The Payment Service is a <em>saga participant</em> in the choreography pattern.
 * It reacts to events published by other services — it does NOT call them directly.
 *
 * <p>Event flow:
 * <pre>
 *   Consumes: order.created         → processes payment
 *     → success: publishes payment.processed
 *     → failure: publishes payment.failed
 *
 *   Consumes: payment.refund        → reverses a prior charge
 *     → publishes payment.refunded (for audit/notification)
 * </pre>
 *
 * <p>Payment simulation logic:
 *   To keep this demo deterministic without a real payment gateway,
 *   orders with a total price whose integer part is ODD are rejected
 *   (simulates a declined card), while EVEN totals succeed.
 *   Example: $29.99 → fails, $30.00 → succeeds.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
