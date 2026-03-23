package com.example.cloudstream.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * HTTP request body for the POST /api/orders endpoint.
 *
 * <p>Bean Validation annotations ({@link NotBlank}, {@link Positive}) are checked
 * by Spring MVC before the controller method is invoked. If validation fails,
 * Spring returns HTTP 400 Bad Request with a detailed error body.
 *
 * @param customerId the ID of the customer placing the order (must not be blank)
 * @param productId  the ID of the product being ordered (must not be blank)
 * @param quantity   number of units to order (must be at least 1)
 * @param totalPrice total cost for the order (must be positive)
 */
public record PlaceOrderRequest(

        @NotBlank(message = "customerId must not be blank")
        String customerId,

        @NotBlank(message = "productId must not be blank")
        String productId,

        @Positive(message = "quantity must be a positive integer")
        int quantity,

        @Positive(message = "totalPrice must be positive")
        BigDecimal totalPrice
) {
}
