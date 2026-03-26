package com.example.camundabpm.delegate;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.repository.OrderRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Camunda Java Delegate that implements the "Schedule Shipment" service task.
 *
 * <p>This delegate runs after payment has been successfully processed.
 *
 * <p>Business logic:
 * Generates a unique tracking number and "schedules" the shipment with a
 * fictional logistics provider. In a real system this would call a shipping
 * API (FedEx, UPS, DHL, etc.) and receive back a real tracking number.
 *
 * <p>On success: stores the tracking number on the order, updates the status
 * to SHIPPED, and sets the process variable {@code trackingNumber} so the
 * notification delegate can include it in the confirmation message.
 */
@Component("shippingDelegate")
public class ShippingDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ShippingDelegate.class);

    /** Variable name for the tracking number shared with the notification delegate. */
    public static final String VAR_TRACKING_NUMBER = "trackingNumber";

    private final OrderRepository orderRepository;

    public ShippingDelegate(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Executes the shipping scheduling step for the order.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the order using the {@code orderId} process variable.</li>
     *   <li>Generate a unique tracking number (UUID-based, prefixed with "TRK-").</li>
     *   <li>Persist the tracking number on the order.</li>
     *   <li>Update order status to SHIPPED.</li>
     *   <li>Set the {@code trackingNumber} process variable for downstream delegates.</li>
     * </ol>
     *
     * @param execution the Camunda execution context
     * @throws Exception if the order is not found
     */
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Long orderId = (Long) execution.getVariable("orderId");
        log.info("[Shipping] Scheduling shipment for order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        // Generate a tracking number using a UUID. In a real system this would come
        // from the shipping provider's API response.
        // The UUID ensures uniqueness across orders. The "TRK-" prefix makes it
        // recognisable as a tracking number in logs and the database.
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().toUpperCase();

        // Persist the tracking number and update status
        order.setTrackingNumber(trackingNumber);
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        // Share the tracking number via process variables so the notification
        // delegate can include it in the customer confirmation message.
        execution.setVariable(VAR_TRACKING_NUMBER, trackingNumber);

        log.info("[Shipping] Order {} shipped with tracking number {}", orderId, trackingNumber);
    }
}
