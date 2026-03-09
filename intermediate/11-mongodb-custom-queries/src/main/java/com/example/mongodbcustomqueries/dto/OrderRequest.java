package com.example.mongodbcustomqueries.dto;

import com.example.mongodbcustomqueries.domain.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data Transfer Object (DTO) for creating a new order via the REST API.
 *
 * <p>A DTO decouples the API contract from the internal domain model
 * ({@link com.example.mongodbcustomqueries.domain.Order}). This allows the
 * HTTP interface to evolve independently of the storage schema.
 *
 * <p>Bean Validation annotations enforce input constraints before the request
 * reaches the service layer. Spring Boot's default error handler converts
 * constraint violations into a 400 Bad Request response with detailed field
 * error messages.
 */
public class OrderRequest {

    /**
     * Name of the customer placing the order.
     * {@code @NotBlank} rejects null, empty, and whitespace-only strings.
     */
    @NotBlank(message = "Customer name must not be blank")
    private String customerName;

    /**
     * Geographic region for the order (e.g. "North", "South", "East", "West").
     * {@code @NotBlank} ensures the region field is always provided.
     */
    @NotBlank(message = "Region must not be blank")
    private String region;

    /**
     * Order status (e.g. "PENDING", "SHIPPED", "DELIVERED", "CANCELLED").
     * {@code @NotBlank} ensures a valid status string is provided.
     */
    @NotBlank(message = "Status must not be blank")
    private String status;

    /**
     * Total monetary value of the order.
     * {@code @NotNull} ensures this field is present in the request body.
     */
    @NotNull(message = "Total amount must not be null")
    private BigDecimal totalAmount;

    /**
     * List of line items included in this order.
     * {@code @NotEmpty} ensures at least one item is provided.
     * {@code @Valid} cascades Bean Validation to each {@link OrderItem} element.
     */
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItem> items;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Jackson for JSON deserialisation. */
    public OrderRequest() {}

    /**
     * Convenience all-args constructor used in tests.
     *
     * @param customerName  customer identifier
     * @param region        geographic region
     * @param status        order status
     * @param totalAmount   total order value
     * @param items         list of order line items
     */
    public OrderRequest(String customerName, String region, String status,
                        BigDecimal totalAmount, List<OrderItem> items) {
        this.customerName = customerName;
        this.region = region;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
