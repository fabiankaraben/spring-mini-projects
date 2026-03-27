package com.example.elasticlogging.dto;

import com.example.elasticlogging.model.Order;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating the status of an existing order.
 *
 * <p>The controller uses this DTO to receive the desired new {@link Order.Status}
 * from the HTTP request body. Validation ensures the status field is present.
 */
public class UpdateOrderStatusRequest {

    /**
     * The new status to apply to the order.
     * Must be one of: PENDING, PROCESSING, SHIPPED, CANCELLED.
     */
    @NotNull(message = "status must not be null")
    private Order.Status status;

    // -------------------------------------------------------------------------
    // Default constructor (required by Jackson for JSON deserialization)
    // -------------------------------------------------------------------------

    public UpdateOrderStatusRequest() {
    }

    public UpdateOrderStatusRequest(Order.Status status) {
        this.status = status;
    }

    // -------------------------------------------------------------------------
    // Getters and setters (required by Jackson)
    // -------------------------------------------------------------------------

    public Order.Status getStatus() {
        return status;
    }

    public void setStatus(Order.Status status) {
        this.status = status;
    }
}
