package com.example.elasticlogging.exception;

/**
 * Exception thrown when an order with the given ID cannot be found.
 *
 * <p>The controller advice maps this exception to an HTTP 404 Not Found response.
 * The exception is also logged as a structured WARN event with the orderId field
 * included in the JSON log object via StructuredArguments.
 */
public class OrderNotFoundException extends RuntimeException {

    /** The ID that was looked up but not found. */
    private final String orderId;

    /**
     * Creates the exception with the missing order ID.
     *
     * @param orderId the ID that was not found in the order store
     */
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    /**
     * Returns the ID that was not found.
     *
     * @return the missing order ID
     */
    public String getOrderId() {
        return orderId;
    }
}
