package com.example.cqrs.query.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link OrderSummary} read model.
 *
 * <p>The query side only reads from this repository — writes happen exclusively through
 * the {@code OrderProjection} which listens to domain events. This separation ensures
 * the read model is always a faithful projection of the event stream.
 *
 * <p>Spring Data auto-generates the SQL at startup from the method signatures.
 */
public interface OrderSummaryRepository extends JpaRepository<OrderSummary, String> {

    /**
     * Returns all orders for a given product, ordered by placement time.
     *
     * <p>This is a simple example of a read-optimised query that would be painful
     * to execute against the event store but is trivial on the read model.
     *
     * @param productId the product identifier to filter by
     * @return list of matching order summaries
     */
    List<OrderSummary> findByProductIdOrderByPlacedAtDesc(String productId);

    /**
     * Returns all orders with a given status.
     *
     * @param status one of "PLACED", "CONFIRMED", "CANCELLED"
     * @return list of matching order summaries
     */
    List<OrderSummary> findByStatusOrderByPlacedAtDesc(String status);
}
