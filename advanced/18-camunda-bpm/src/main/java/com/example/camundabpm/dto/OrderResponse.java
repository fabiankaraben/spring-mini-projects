package com.example.camundabpm.dto;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object (DTO) for the order response in GET and POST /api/orders.
 *
 * <p>This DTO shapes what is returned to API clients. It includes all fields that
 * are meaningful to the consumer:
 * <ul>
 *   <li>Business data: customerName, productName, quantity, unitPrice, totalAmount.</li>
 *   <li>Process data: status, processInstanceId, trackingNumber.</li>
 *   <li>Audit data: createdAt, updatedAt, errorMessage.</li>
 * </ul>
 *
 * <p>The static factory method {@link #from(Order)} converts a domain entity to this DTO,
 * keeping the mapping logic in one place and off the controller.
 */
public class OrderResponse {

    private Long id;
    private String customerName;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private OrderStatus status;

    /**
     * The Camunda process instance ID — allows clients (or operators) to correlate
     * this order with the running or historical Camunda process instance via the
     * Camunda REST API or Cockpit.
     */
    private String processInstanceId;

    /** Shipping tracking number; null until the order has been shipped. */
    private String trackingNumber;

    private Instant createdAt;
    private Instant updatedAt;

    /** Error message when status is FAILED; null otherwise. */
    private String errorMessage;

    /** Private constructor — use {@link #from(Order)} to create instances. */
    private OrderResponse() {}

    /**
     * Factory method that maps an {@link Order} entity to an {@link OrderResponse} DTO.
     *
     * <p>Centralising the mapping here means controllers stay thin and only contain
     * HTTP-specific logic.
     *
     * @param order the domain entity to map
     * @return a populated response DTO
     */
    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.customerName = order.getCustomerName();
        response.productName = order.getProductName();
        response.quantity = order.getQuantity();
        response.unitPrice = order.getUnitPrice();
        response.totalAmount = order.getTotalAmount();
        response.status = order.getStatus();
        response.processInstanceId = order.getProcessInstanceId();
        response.trackingNumber = order.getTrackingNumber();
        response.createdAt = order.getCreatedAt();
        response.updatedAt = order.getUpdatedAt();
        response.errorMessage = order.getErrorMessage();
        return response;
    }

    // -------------------------------------------------------------------------
    // Getters (read-only response DTO — no setters needed externally)
    // -------------------------------------------------------------------------

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public String getProcessInstanceId() { return processInstanceId; }
    public String getTrackingNumber() { return trackingNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getErrorMessage() { return errorMessage; }
}
