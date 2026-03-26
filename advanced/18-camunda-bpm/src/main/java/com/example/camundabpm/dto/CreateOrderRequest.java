package com.example.camundabpm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Data Transfer Object (DTO) for the POST /api/orders request body.
 *
 * <p>DTOs are used instead of exposing the JPA entity directly in the API layer.
 * This provides:
 * <ul>
 *   <li>Input validation via Jakarta Bean Validation annotations.</li>
 *   <li>Decoupling between the API contract and the internal domain model
 *       (e.g., id, status, processInstanceId are not exposed for input).</li>
 *   <li>A clear boundary — the controller maps this DTO into an Order entity.</li>
 * </ul>
 *
 * <p>The @Valid annotation on the controller method parameter triggers validation.
 * If validation fails, Spring returns HTTP 400 Bad Request with constraint details.
 */
public class CreateOrderRequest {

    /**
     * The name of the customer placing the order.
     * @NotBlank ensures the field is present and not just whitespace.
     */
    @NotBlank(message = "Customer name must not be blank")
    private String customerName;

    /**
     * The product to order (e.g., "Laptop Pro 15", "USB-C Hub").
     * @NotBlank ensures a product name is always provided.
     */
    @NotBlank(message = "Product name must not be blank")
    private String productName;

    /**
     * The number of units to order.
     * @NotNull ensures the field is present.
     * @Min(1) ensures at least one unit is ordered.
     */
    @NotNull(message = "Quantity must not be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /**
     * The price per unit of the product.
     * @NotNull ensures the field is present.
     * @DecimalMin ensures the price is greater than zero.
     */
    @NotNull(message = "Unit price must not be null")
    @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
    private BigDecimal unitPrice;

    // -------------------------------------------------------------------------
    // Getters and setters (required by Jackson for JSON deserialization)
    // -------------------------------------------------------------------------

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
