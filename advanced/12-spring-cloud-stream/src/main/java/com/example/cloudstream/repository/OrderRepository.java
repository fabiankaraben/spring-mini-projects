package com.example.cloudstream.repository;

import com.example.cloudstream.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for {@link Order} entities.
 *
 * <p>This project focuses on the <em>messaging layer</em> (Spring Cloud Stream),
 * not persistence. A {@link ConcurrentHashMap} provides thread-safe in-memory
 * storage without requiring a database or JPA setup.
 *
 * <p>In a production system you would replace this with a Spring Data JPA
 * or Spring Data MongoDB repository backed by a real database.
 *
 * <p>Thread safety:
 * {@link ConcurrentHashMap} provides safe concurrent reads and writes,
 * which is important because the Spring Cloud Stream consumer callbacks
 * run on separate threads from the REST API handlers.
 */
@Repository
public class OrderRepository {

    /**
     * The in-memory store.
     * Key:   order UUID (as String for simplicity)
     * Value: Order domain object
     */
    private final ConcurrentHashMap<UUID, Order> store = new ConcurrentHashMap<>();

    /**
     * Saves (inserts or updates) an order in the in-memory store.
     *
     * @param order the order to save
     * @return the same order (mirrors the Spring Data Repository contract)
     */
    public Order save(Order order) {
        store.put(order.getId(), order);
        return order;
    }

    /**
     * Finds an order by its UUID.
     *
     * @param id the order's unique identifier
     * @return an {@link Optional} containing the order, or empty if not found
     */
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Returns all orders currently in the store.
     *
     * @return unmodifiable snapshot of all orders
     */
    public Collection<Order> findAll() {
        return store.values();
    }

    /**
     * Removes all orders from the store.
     * Useful in tests to reset state between test methods.
     */
    public void deleteAll() {
        store.clear();
    }

    /**
     * Returns the number of orders in the store.
     *
     * @return current size of the in-memory store
     */
    public int count() {
        return store.size();
    }
}
