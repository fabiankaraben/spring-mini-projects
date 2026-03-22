package com.example.saga.payment.repository;

import com.example.saga.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Payment} entities.
 *
 * <p>Provides standard CRUD operations plus a custom query to find a payment
 * by its associated order ID — used for idempotency checks and status queries.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Finds the payment record associated with a given order.
     *
     * <p>Used by:
     * <ul>
     *   <li>The REST endpoint to look up payment status by order ID.</li>
     *   <li>The refund handler to find and update the original charge record.</li>
     * </ul>
     *
     * @param orderId the saga order identifier
     * @return an Optional containing the payment if it exists
     */
    Optional<Payment> findByOrderId(UUID orderId);
}
