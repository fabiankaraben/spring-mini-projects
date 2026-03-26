package com.example.camundabpm.delegate;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.repository.OrderRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Camunda Java Delegate that implements the "Send Notification" service task.
 *
 * <p>This is the final step in the happy-path fulfilment flow, executed after
 * the order has been shipped.
 *
 * <p>Business logic:
 * Sends a confirmation notification to the customer with the tracking number.
 * In a real system this would send an email (via SendGrid, SES, etc.),
 * an SMS (via Twilio), or a push notification.
 *
 * <p>On success: updates the order status to COMPLETED, marking the entire
 * fulfilment process as done. This is the terminal success state.
 */
@Component("notificationDelegate")
public class NotificationDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(NotificationDelegate.class);

    private final OrderRepository orderRepository;

    public NotificationDelegate(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Executes the customer notification step for the order.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the order using the {@code orderId} process variable.</li>
     *   <li>Read the {@code trackingNumber} process variable set by ShippingDelegate.</li>
     *   <li>Simulate sending a confirmation notification to the customer.</li>
     *   <li>Update order status to COMPLETED — the final success state.</li>
     * </ol>
     *
     * @param execution the Camunda execution context
     * @throws Exception if the order is not found
     */
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Long orderId = (Long) execution.getVariable("orderId");
        String trackingNumber = (String) execution.getVariable("trackingNumber");

        log.info("[Notification] Sending notification for order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        // Simulate sending notification:
        // In a real application this would call an email or SMS service.
        // Here we just log the notification content for demonstration.
        log.info("[Notification] Dear {}, your order for '{}' (x{}) has been shipped! " +
                        "Tracking number: {}. Total charged: {}.",
                order.getCustomerName(),
                order.getProductName(),
                order.getQuantity(),
                trackingNumber,
                order.getTotalAmount());

        // Mark the order as COMPLETED — this is the happy-path terminal state.
        // The process will end at the "Order Fulfilled" end event after this task.
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        log.info("[Notification] Order {} marked as COMPLETED", orderId);
    }
}
