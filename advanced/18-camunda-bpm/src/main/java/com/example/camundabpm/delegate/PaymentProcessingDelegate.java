package com.example.camundabpm.delegate;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.repository.OrderRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Camunda Java Delegate that implements the "Process Payment" service task.
 *
 * <p>This delegate is called after the inventory check succeeds (the exclusive gateway
 * evaluated {@code inventoryAvailable == true} and routed to this task).
 *
 * <p>Business logic:
 * Calculates the total order amount (quantity × unitPrice) and simulates charging
 * the customer. In a real system this would call a payment gateway (Stripe, PayPal, etc.).
 *
 * <p>On success: sets order status to PAYMENT_PROCESSED, stores the total amount,
 * and sets the process variable {@code paymentSuccess = true}.
 *
 * <p>On failure (negative total): sets order status to FAILED and sets
 * {@code paymentSuccess = false}, triggering the failure path in the gateway.
 */
@Component("paymentProcessingDelegate")
public class PaymentProcessingDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingDelegate.class);

    /** Variable name written to the process execution context after payment processing. */
    public static final String VAR_PAYMENT_SUCCESS = "paymentSuccess";

    /** Variable name for the computed total amount (shared with subsequent delegates). */
    public static final String VAR_TOTAL_AMOUNT = "totalAmount";

    private final OrderRepository orderRepository;

    public PaymentProcessingDelegate(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Executes the payment processing step for the order.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the order from the database using the {@code orderId} variable.</li>
     *   <li>Compute totalAmount = quantity × unitPrice.</li>
     *   <li>Simulate payment authorisation (always succeeds in this demo).</li>
     *   <li>Store the total amount on the order entity.</li>
     *   <li>Update order status to PAYMENT_PROCESSED.</li>
     *   <li>Set {@code paymentSuccess} process variable for the next gateway.</li>
     * </ol>
     *
     * @param execution the Camunda execution context
     * @throws Exception if the order is not found
     */
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Long orderId = (Long) execution.getVariable("orderId");
        log.info("[PaymentProcessing] Processing payment for order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        // Calculate total charge: quantity × unit price
        BigDecimal totalAmount = order.getUnitPrice()
                .multiply(BigDecimal.valueOf(order.getQuantity()));

        // Simulate payment: in a real application, call an external payment gateway.
        // We consider any positive total amount as a successful payment.
        boolean paymentSuccess = totalAmount.compareTo(BigDecimal.ZERO) > 0;

        if (paymentSuccess) {
            // Persist the computed total on the order entity
            order.setTotalAmount(totalAmount);
            order.setStatus(OrderStatus.PAYMENT_PROCESSED);
            orderRepository.save(order);

            // Share the total with subsequent delegates via process variables
            execution.setVariable(VAR_PAYMENT_SUCCESS, true);
            execution.setVariable(VAR_TOTAL_AMOUNT, totalAmount.toPlainString());

            log.info("[PaymentProcessing] Payment of {} processed for order {}",
                    totalAmount, orderId);
        } else {
            order.setStatus(OrderStatus.FAILED);
            order.setErrorMessage("Payment failed: total amount is zero or negative");
            orderRepository.save(order);

            execution.setVariable(VAR_PAYMENT_SUCCESS, false);
            log.warn("[PaymentProcessing] Payment FAILED for order {}", orderId);
        }
    }
}
