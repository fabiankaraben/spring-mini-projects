package com.example.camundabpm.repository;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Order} entities.
 *
 * <p>Spring Data auto-generates the implementation at startup — no SQL or
 * implementation classes are needed. The interface extends {@link JpaRepository}
 * which already provides standard CRUD operations:
 * <ul>
 *   <li>{@code save(Order)} — insert or update</li>
 *   <li>{@code findById(Long)} — lookup by primary key</li>
 *   <li>{@code findAll()} — retrieve all orders</li>
 *   <li>{@code deleteById(Long)} — remove by primary key</li>
 * </ul>
 *
 * <p>Custom query methods below are auto-implemented by Spring Data using
 * method name parsing conventions (e.g., {@code findByStatus} generates a
 * {@code WHERE status = ?1} query).
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Finds all orders with the given status.
     *
     * <p>Spring Data generates: {@code SELECT * FROM orders WHERE status = ?}
     *
     * @param status the order status to filter by
     * @return list of orders in that status (may be empty)
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Finds an order by its Camunda process instance ID.
     *
     * <p>Spring Data generates: {@code SELECT * FROM orders WHERE process_instance_id = ?}
     * Useful for Camunda delegates that need to load the order using the process
     * instance ID stored in the execution context.
     *
     * @param processInstanceId the Camunda process instance ID
     * @return the matching order, or empty if no order is linked to that process instance
     */
    Optional<Order> findByProcessInstanceId(String processInstanceId);

    /**
     * Finds all orders placed by a specific customer.
     *
     * <p>Spring Data generates: {@code SELECT * FROM orders WHERE customer_name = ?}
     *
     * @param customerName the customer's name (case-sensitive)
     * @return list of orders for that customer (may be empty)
     */
    List<Order> findByCustomerName(String customerName);
}
