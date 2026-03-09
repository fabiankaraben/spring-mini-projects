package com.example.kafkaproducer.dto;

import com.example.kafkaproducer.domain.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for the {@code POST /api/orders} endpoint.
 *
 * <p>This record carries the data provided by the API caller. Bean Validation
 * annotations ({@code @NotBlank}, {@code @Min}, etc.) are applied here so that
 * invalid payloads are rejected before reaching the service layer, keeping the
 * service focused on business logic rather than input sanitation.
 *
 * <p>Using a Java {@code record} gives us an immutable, concise DTO with
 * auto-generated {@code equals}, {@code hashCode}, and {@code toString}.
 *
 * @param orderId     business identifier of the order (must not be blank)
 * @param customerId  identifier of the customer placing the order
 * @param product     product name or SKU being ordered
 * @param quantity    number of units (must be at least 1)
 * @param totalAmount total price; must be a positive non-zero value
 * @param status      current order status; defaults to CREATED when null
 */
public record PublishOrderRequest(

        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @NotBlank(message = "customerId must not be blank")
        String customerId,

        @NotBlank(message = "product must not be blank")
        String product,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        @NotNull(message = "totalAmount must not be null")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than zero")
        BigDecimal totalAmount,

        @NotNull(message = "status must not be null")
        OrderStatus status
) {}
