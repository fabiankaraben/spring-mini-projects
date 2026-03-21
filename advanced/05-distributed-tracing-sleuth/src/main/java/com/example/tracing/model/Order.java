package com.example.tracing.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Represents an order submitted by a client.
 *
 * <p>This record is used as both the request body for {@code POST /orders}
 * and as a value object inside the service layer. Using a Java {@code record}
 * ensures immutability — the order cannot be mutated after creation, which is
 * important in concurrent/traced code where the same object may be referenced
 * across multiple threads or span callbacks.
 *
 * <p><b>Tracing context:</b>
 * When an Order is processed, the {@code OrderService} creates a child span named
 * {@code "process-order"} and attaches the {@code orderId}, {@code productId}, and
 * {@code quantity} as span tags. These tags appear in the Zipkin UI alongside the
 * trace ID, making it easy to find the trace for a specific order later.
 *
 * @param orderId   unique identifier assigned by the client (or generated server-side)
 * @param productId the product being ordered; forwarded to the InventoryService
 * @param quantity  number of units; must be at least 1
 * @param customer  human-readable customer name for display in trace tags
 */
public record Order(
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @NotBlank(message = "productId must not be blank")
        String productId,

        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotBlank(message = "customer must not be blank")
        String customer
) {}
