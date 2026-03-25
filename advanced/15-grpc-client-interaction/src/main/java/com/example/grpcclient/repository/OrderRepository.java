package com.example.grpcclient.repository;

import com.example.grpcclient.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Order} entities.
 *
 * <p>Spring Data automatically generates an implementation at startup by scanning
 * for interfaces that extend {@link JpaRepository}. No boilerplate SQL or
 * EntityManager calls are needed for standard CRUD operations.
 *
 * <p>The primary key type is {@code String} (UUID) rather than a numeric type,
 * reflecting the design choice to use human-readable UUIDs as order identifiers.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * Find all orders placed by a given customer, ordered by creation timestamp descending
     * (most recent first).
     *
     * <p>Spring Data translates this method name into:
     * <pre>SELECT o FROM Order o WHERE o.customerId = :customerId ORDER BY o.createdAt DESC</pre>
     *
     * @param customerId the customer identifier to filter by
     * @return list of orders for that customer, most recent first; may be empty
     */
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
