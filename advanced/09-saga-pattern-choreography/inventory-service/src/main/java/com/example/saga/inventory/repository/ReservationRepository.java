package com.example.saga.inventory.repository;

import com.example.saga.inventory.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Reservation} entities.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Finds a reservation by order ID — used for idempotency checks.
     *
     * @param orderId the saga order identifier
     * @return an Optional containing the reservation if it already exists
     */
    Optional<Reservation> findByOrderId(UUID orderId);
}
