package com.example.stripepayment.repository;

import com.example.stripepayment.domain.Payment;
import com.example.stripepayment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Payment} entities.
 *
 * <p>Spring Data JPA automatically generates the implementation at runtime based
 * on the method signatures. No manual SQL or JPQL is required for basic CRUD.
 *
 * <p>The repository extends {@link JpaRepository} which already provides:
 * <ul>
 *   <li>{@code save(entity)} – insert or update</li>
 *   <li>{@code findById(id)} – find by primary key</li>
 *   <li>{@code findAll()} – retrieve all records</li>
 *   <li>{@code deleteById(id)} – delete by primary key</li>
 *   <li>... and more</li>
 * </ul>
 *
 * <p>Custom query methods are derived from their names by Spring Data JPA.
 * For example, {@link #findByStripePaymentIntentId(String)} generates a
 * {@code SELECT ... WHERE stripe_payment_intent_id = ?} query automatically.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Finds a Payment record by its Stripe PaymentIntent ID.
     *
     * <p>The Stripe PaymentIntent ID (e.g., {@code pi_3NtYvJ2eZvKYlo2C1kfmXjBR}) is
     * the primary way to correlate local records with Stripe objects.
     *
     * @param stripePaymentIntentId the Stripe-assigned PaymentIntent ID
     * @return an {@link Optional} containing the Payment if found, or empty
     */
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Finds all Payment records with a specific status.
     *
     * <p>Useful for querying all pending, succeeded, or canceled payments.
     *
     * @param status the {@link PaymentStatus} to filter by
     * @return a (possibly empty) list of payments matching the given status
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Checks whether a payment record already exists for a given Stripe ID.
     *
     * <p>Used to guard against duplicate record creation (idempotency check).
     *
     * @param stripePaymentIntentId the Stripe PaymentIntent ID to check
     * @return {@code true} if a record exists; {@code false} otherwise
     */
    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);
}
