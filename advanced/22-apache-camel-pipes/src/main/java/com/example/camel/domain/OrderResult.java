package com.example.camel.domain;

/**
 * Response returned to the HTTP caller after an order is accepted into the pipeline.
 *
 * <p>This is a lightweight acknowledgement object — it does NOT wait for the order to complete
 * all pipeline stages.  The pipeline processes the order asynchronously after this response
 * is sent.
 *
 * @param orderId    The order identifier echoed back for correlation.
 * @param status     Acceptance status: "ACCEPTED" when the order entered the pipeline,
 *                   "REJECTED" when validation failed.
 * @param message    Human-readable description of the outcome.
 */
public record OrderResult(String orderId, String status, String message) {

    /**
     * Factory method for a successfully accepted order.
     *
     * @param orderId The order that entered the pipeline.
     * @return An {@code OrderResult} with status "ACCEPTED".
     */
    public static OrderResult accepted(String orderId) {
        return new OrderResult(orderId, "ACCEPTED",
                "Order accepted and entering the processing pipeline.");
    }

    /**
     * Factory method for a rejected order (failed validation).
     *
     * @param orderId The order that was rejected.
     * @param reason  Reason for rejection (from the validation processor).
     * @return An {@code OrderResult} with status "REJECTED".
     */
    public static OrderResult rejected(String orderId, String reason) {
        return new OrderResult(orderId, "REJECTED", reason);
    }
}
