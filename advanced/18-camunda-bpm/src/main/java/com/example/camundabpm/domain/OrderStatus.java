package com.example.camundabpm.domain;

/**
 * Represents the lifecycle status of an order as it flows through the
 * Camunda BPMN fulfilment process.
 *
 * <p>Status transitions follow this path (happy path):
 * <pre>
 *   PENDING → INVENTORY_CHECKED → PAYMENT_PROCESSED → SHIPPED → COMPLETED
 * </pre>
 *
 * <p>Any step can transition to FAILED if a delegate throws an exception
 * or sets an error condition in the process variables.
 */
public enum OrderStatus {

    /**
     * The order has been created and submitted via the REST API.
     * The Camunda process instance has been started but the first
     * service task (inventory check) has not yet executed.
     */
    PENDING,

    /**
     * The InventoryCheckDelegate has confirmed that all ordered items
     * are available in stock. The process is ready to proceed to payment.
     */
    INVENTORY_CHECKED,

    /**
     * The PaymentProcessingDelegate has successfully charged the customer.
     * The process is ready to proceed to shipping.
     */
    PAYMENT_PROCESSED,

    /**
     * The ShippingDelegate has scheduled the order for shipment.
     * A tracking number has been assigned and the process is ready to
     * send the confirmation notification.
     */
    SHIPPED,

    /**
     * The NotificationDelegate has sent the customer a confirmation.
     * The process has completed successfully. This is the terminal success state.
     */
    COMPLETED,

    /**
     * An error occurred in one of the service tasks. The process has ended
     * via the error end event. This is the terminal failure state.
     */
    FAILED
}
