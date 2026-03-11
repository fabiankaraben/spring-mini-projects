package com.example.stripepayment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO (Data Transfer Object) for the "create payment" HTTP request body.
 *
 * <p>The client sends this JSON payload when calling
 * {@code POST /api/payments/create}.
 *
 * <p>Example request body:
 * <pre>{@code
 * {
 *   "amount": 2000,
 *   "currency": "usd",
 *   "description": "Order #1234 – Spring Boot T-Shirt"
 * }
 * }</pre>
 *
 * <p>Bean Validation annotations ensure the data is well-formed before
 * reaching the service layer.
 *
 * @param amount      the payment amount in the smallest currency unit (e.g., cents for USD).
 *                    Must be at least 1 (Stripe requires positive amounts).
 * @param currency    the 3-letter ISO 4217 currency code in lowercase (e.g., "usd", "eur").
 * @param description an optional human-readable description shown in the Stripe dashboard.
 */
public record CreatePaymentRequest(

        /**
         * Amount in the smallest currency unit.
         *
         * <p>Examples:
         * <ul>
         *   <li>2000 = $20.00 USD</li>
         *   <li>1050 = €10.50 EUR</li>
         *   <li>500  = ¥500 JPY (JPY has no decimal places)</li>
         * </ul>
         */
        @NotNull(message = "amount is required")
        @Min(value = 1, message = "amount must be at least 1 (smallest currency unit)")
        Long amount,

        /**
         * 3-letter ISO 4217 currency code (lowercase).
         * Stripe validates this server-side, but we add a basic check here too.
         */
        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code (e.g. usd)")
        String currency,

        /**
         * Optional description for the payment (shown in the Stripe dashboard).
         * Max 500 characters to match the PaymentIntent description limit.
         */
        @Size(max = 500, message = "description must not exceed 500 characters")
        String description

) {
}
