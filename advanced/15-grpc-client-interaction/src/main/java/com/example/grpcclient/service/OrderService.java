package com.example.grpcclient.service;

import com.example.grpcclient.domain.Order;
import com.example.grpcclient.domain.OrderLineItem;
import com.example.grpcclient.domain.OrderStatus;
import com.example.grpcclient.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for Order domain logic.
 *
 * <p>This class encapsulates all business rules for order management.
 * It operates exclusively on JPA {@link Order} and {@link OrderLineItem} entities,
 * with no dependency on protobuf types or gRPC (those concerns live in the
 * gRPC service layer).
 *
 * <p>Why separate service from gRPC layer?
 * <ul>
 *   <li>Business logic can be unit-tested without a running gRPC server.</li>
 *   <li>The same service could be reused by REST controllers or message consumers.</li>
 *   <li>Clear separation: gRPC layer handles protocol; service handles domain.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    /**
     * Spring Data JPA repository for CRUD and query operations on orders.
     */
    private final OrderRepository orderRepository;

    /**
     * Constructor injection — ensures the dependency is required and immutable.
     *
     * @param orderRepository the JPA repository for orders
     */
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Retrieve a single order by its unique order ID.
     *
     * @param orderId the UUID string order identifier
     * @return Optional containing the order if found, empty otherwise
     */
    public Optional<Order> findById(String orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Retrieve all orders for a specific customer, most recent first.
     *
     * @param customerId the customer identifier to filter by
     * @return list of orders for that customer, sorted by creation time descending
     */
    public List<Order> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Create a new order with the given line items and persist it to the database.
     *
     * <p>Business rules applied:
     * <ul>
     *   <li>A UUID is generated server-side as the order ID.</li>
     *   <li>New orders start with {@link OrderStatus#PENDING} status.</li>
     *   <li>Total amount is computed as the sum of (quantity * unitPrice) per line item.</li>
     *   <li>Creation timestamp is set to the current server time.</li>
     * </ul>
     *
     * @param customerId the customer placing the order
     * @param items      the line items for the order (must not be empty)
     * @return the persisted {@link Order} with a generated UUID and PENDING status
     * @throws IllegalArgumentException if customerId is blank or items is empty
     */
    @Transactional
    public Order createOrder(String customerId, List<OrderLineItem> items) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // Generate a UUID string as the order identifier.
        String orderId = UUID.randomUUID().toString();

        // Compute total amount from line items.
        double total = items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();

        // Create the order entity.
        Order order = new Order(orderId, customerId, OrderStatus.PENDING, total, LocalDateTime.now());

        // Attach each line item to the order (sets the FK back-reference).
        for (OrderLineItem item : items) {
            order.addItem(item);
        }

        return orderRepository.save(order);
    }

    /**
     * Update the status of an existing order.
     *
     * @param orderId   the ID of the order to update
     * @param newStatus the new status to apply
     * @return Optional containing the updated order, or empty if not found
     */
    @Transactional
    public Optional<Order> updateStatus(String orderId, OrderStatus newStatus) {
        return orderRepository.findById(orderId).map(order -> {
            order.setStatus(newStatus);
            return orderRepository.save(order);
        });
    }
}
