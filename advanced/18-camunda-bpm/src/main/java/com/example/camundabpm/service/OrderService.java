package com.example.camundabpm.service;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.dto.CreateOrderRequest;
import com.example.camundabpm.repository.OrderRepository;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service that bridges the REST layer with the Camunda process engine.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Persist a new {@link Order} entity to the database.</li>
 *   <li>Start the Camunda BPMN fulfilment process, passing the order ID as a
 *       process variable so each delegate can load and update the correct order.</li>
 *   <li>Provide query operations (find by id, find all, find by status).</li>
 * </ol>
 *
 * <p>Transaction strategy:
 * <ul>
 *   <li>{@code createAndStartOrder} is @Transactional — the order is saved and the
 *       process is started atomically. If the process start fails the order save is
 *       rolled back.</li>
 *   <li>Read operations use {@code readOnly = true} — optimises database access
 *       by signalling no writes will occur.</li>
 * </ul>
 *
 * <p>Key Camunda concept — process variables:
 * A Map<String, Object> is passed to {@code runtimeService.startProcessInstanceByKey}.
 * Variables are stored in the Camunda database (ACT_RU_VARIABLE table) and are
 * accessible from every delegate in the process via {@code execution.getVariable("key")}.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * The process definition key — must match the {@code id} attribute of the
     * {@code <process>} element in the BPMN file (order-fulfilment.bpmn).
     */
    public static final String PROCESS_KEY = "order-fulfilment";

    /** Variable name used to pass the order ID into the process. */
    public static final String VAR_ORDER_ID = "orderId";

    private final OrderRepository orderRepository;

    /**
     * Camunda's RuntimeService provides the API for managing process instances:
     * starting instances, querying running instances, setting/getting variables, etc.
     * It is auto-configured as a Spring bean by the Camunda Spring Boot Starter.
     */
    private final RuntimeService runtimeService;

    public OrderService(OrderRepository orderRepository, RuntimeService runtimeService) {
        this.orderRepository = orderRepository;
        this.runtimeService = runtimeService;
    }

    /**
     * Creates an order and starts the Camunda fulfilment process for it.
     *
     * <p>Steps:
     * <ol>
     *   <li>Map the incoming DTO to an Order entity and save it to the database.
     *       At this point the order has status PENDING and a generated ID.</li>
     *   <li>Build the initial process variables map. The orderId is the critical
     *       variable — every delegate uses it to look up the order.</li>
     *   <li>Start the process instance. Camunda synchronously executes all service
     *       tasks in sequence (inventory check → payment → shipping → notification).
     *       Because the process has no user tasks or async markers, all service tasks
     *       run in the same thread before this method returns.</li>
     *   <li>Store the Camunda process instance ID on the order for cross-reference.</li>
     * </ol>
     *
     * @param request the validated order creation request from the REST API
     * @return the updated order entity after the process completes
     */
    @Transactional
    public Order createAndStartOrder(CreateOrderRequest request) {
        // Step 1: persist the order entity with PENDING status
        Order order = new Order(
                request.getCustomerName(),
                request.getProductName(),
                request.getQuantity(),
                request.getUnitPrice()
        );
        order = orderRepository.save(order);
        log.info("Order {} created with status PENDING", order.getId());

        // Step 2: build process variables — the orderId allows delegates to load the order
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_ORDER_ID, order.getId());

        // Step 3: start the Camunda process instance
        // startProcessInstanceByKey looks up the latest deployed version of the process
        // with key "order-fulfilment" and starts a new instance.
        //
        // Because all service tasks in this process are SYNCHRONOUS (no async:before/after),
        // Camunda executes all delegates sequentially in this call before returning.
        // The process instance returned here is already in a completed or failed state.
        // Step 3a: start the process — all synchronous delegates run here
        log.info("Starting Camunda process '{}' for order {}", PROCESS_KEY, order.getId());
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY,
                variables
        );
        log.info("Camunda process instance {} started for order {}",
                processInstance.getId(), order.getId());

        // Step 3b: persist the Camunda process instance ID on the order so that clients
        // and operators can cross-reference the business entity with the Camunda history.
        // We do this with a direct update after the process completes.
        orderRepository.findById(order.getId()).ifPresent(o -> {
            o.setProcessInstanceId(processInstance.getId());
            orderRepository.save(o);
        });

        // Step 4: reload the order to get the final state set by the delegates
        // (delegates updated the order during process execution via the same transaction).
        // Capture the ID in a final variable so it can be referenced inside the lambda
        // (Java requires variables used in lambdas to be effectively final).
        final Long savedOrderId = order.getId();
        order = orderRepository.findById(savedOrderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Order " + savedOrderId + " not found after process completion"));

        log.info("Order {} completed with status {}", order.getId(), order.getStatus());
        return order;
    }

    /**
     * Retrieves a single order by its primary key.
     *
     * @param id the order ID
     * @return an Optional containing the order if found, or empty
     */
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    /**
     * Retrieves all orders stored in the database.
     *
     * @return list of all orders (may be empty)
     */
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    /**
     * Retrieves all orders with a specific status.
     *
     * @param status the status to filter by
     * @return list of matching orders (may be empty)
     */
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
}
