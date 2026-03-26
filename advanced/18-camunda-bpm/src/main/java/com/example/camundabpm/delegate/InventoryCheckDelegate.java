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
 * Camunda Java Delegate that implements the "Check Inventory" service task.
 *
 * <p>The Java Delegate pattern:
 * Camunda calls the {@link #execute(DelegateExecution)} method when the process
 * token arrives at the "Check Inventory" service task in the BPMN diagram.
 * The {@link DelegateExecution} provides access to process variables, process
 * instance metadata, and engine services.
 *
 * <p>This delegate is registered as a Spring bean (@Component). In the BPMN file,
 * the service task references it by bean name:
 * {@code camunda:delegateExpression="${inventoryCheckDelegate}"}
 *
 * <p>Business logic:
 * Simulates a real inventory check. For educational purposes, the check always
 * succeeds (stock is always available). In a real system this would call an
 * inventory service or query a stock database.
 *
 * <p>On success: sets the order status to INVENTORY_CHECKED and sets the process
 * variable {@code inventoryAvailable = true}.
 */
@Component("inventoryCheckDelegate")
public class InventoryCheckDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(InventoryCheckDelegate.class);

    /** Variable name written to the process execution context after inventory check. */
    public static final String VAR_INVENTORY_AVAILABLE = "inventoryAvailable";

    private final OrderRepository orderRepository;

    public InventoryCheckDelegate(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Executes the inventory check for the order referenced in process variables.
     *
     * <p>Steps:
     * <ol>
     *   <li>Read the {@code orderId} process variable to identify the order.</li>
     *   <li>Load the order entity from the database.</li>
     *   <li>Simulate an inventory availability check.</li>
     *   <li>Update the order status to INVENTORY_CHECKED.</li>
     *   <li>Write the result back as a process variable for the exclusive gateway.</li>
     * </ol>
     *
     * @param execution the Camunda execution context — provides variables and engine access
     * @throws Exception if the order is not found; Camunda will catch and create an incident
     */
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Read the orderId process variable set when the process was started
        Long orderId = (Long) execution.getVariable("orderId");
        log.info("[InventoryCheck] Checking inventory for order {}", orderId);

        // Load the order entity from the database
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        // Simulate inventory check:
        // In a real application this would query an inventory service or database.
        // We check that the requested quantity is positive (always true here).
        boolean inventoryAvailable = order.getQuantity() > 0;

        if (inventoryAvailable) {
            // Update order status to reflect successful inventory check
            order.setStatus(OrderStatus.INVENTORY_CHECKED);
            orderRepository.save(order);

            // Write the result to the process execution context.
            // The exclusive gateway "Inventory Available?" reads this variable
            // to decide the next flow path.
            execution.setVariable(VAR_INVENTORY_AVAILABLE, true);
            log.info("[InventoryCheck] Inventory available for order {} (qty: {})",
                    orderId, order.getQuantity());
        } else {
            // Mark order as failed and signal the process to take the failure path
            order.setStatus(OrderStatus.FAILED);
            order.setErrorMessage("Insufficient inventory for quantity: " + order.getQuantity());
            orderRepository.save(order);

            execution.setVariable(VAR_INVENTORY_AVAILABLE, false);
            log.warn("[InventoryCheck] Inventory NOT available for order {}", orderId);
        }
    }
}
