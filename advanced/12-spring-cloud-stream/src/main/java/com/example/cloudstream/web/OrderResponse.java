package com.example.cloudstream.web;

import com.example.cloudstream.domain.Order;
import com.example.cloudstream.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response body for order endpoints.
 *
 * <p>This DTO (Data Transfer Object) is a projection of the {@link Order} domain
 * object, formatted for JSON serialization. Using a separate DTO instead of
 * returning the domain object directly keeps the API stable even if the domain
 * model changes internally.
 *
 * @param id              unique identifier of the order
 * @param customerId      ID of the customer
 * @param productId       ID of the product
 * @param quantity        number of units
 * @param totalPrice      total cost
 * @param status          current lifecycle status
 * @param rejectionReason non-null only when status is REJECTED
 * @param createdAt       creation timestamp
 * @param updatedAt       timestamp of the last status change
 */
public record OrderResponse(
        UUID id,
        String customerId,
        String productId,
        int quantity,
        BigDecimal totalPrice,
        OrderStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Convenience factory that converts an {@link Order} domain object into an
     * {@link OrderResponse} DTO.
     *
     * @param order the domain object to convert
     * @return a new {@link OrderResponse} populated from the order
     */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
