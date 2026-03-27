package com.example.elasticlogging.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new order.
 *
 * <p>Annotated with Bean Validation constraints so that invalid input is rejected
 * before reaching the service layer. Validation failures are logged as structured
 * WARN events, which are visible in Kibana as distinct log entries.
 */
public class CreateOrderRequest {

    /**
     * ID of the customer placing the order.
     * Must be a non-blank string (e.g. "customer-001").
     */
    @NotBlank(message = "customerId must not be blank")
    private String customerId;

    /**
     * Human-readable description of the item(s) being ordered.
     * Must be a non-blank string (e.g. "Laptop model X").
     */
    @NotBlank(message = "description must not be blank")
    private String description;

    /**
     * Total order amount. Must be present and greater than zero.
     * A positive amount is required to ensure valid business data.
     */
    @NotNull(message = "amount must not be null")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    // -------------------------------------------------------------------------
    // Default constructor (required by Jackson for JSON deserialization)
    // -------------------------------------------------------------------------

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String customerId, String description, BigDecimal amount) {
        this.customerId = customerId;
        this.description = description;
        this.amount = amount;
    }

    // -------------------------------------------------------------------------
    // Getters and setters (required by Jackson)
    // -------------------------------------------------------------------------

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
