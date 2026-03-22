package com.example.saga.order.repository;

import com.example.saga.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Order} entities.
 *
 * <p>Spring Data automatically generates the SQL implementation at startup from
 * the method signatures — no manual SQL or JPQL is required for basic CRUD.
 *
 * <p>The base {@link JpaRepository} provides:
 * <ul>
 *   <li>{@code save(entity)} — INSERT or UPDATE depending on whether the entity is new.</li>
 *   <li>{@code findById(id)} — SELECT by primary key, returns Optional.</li>
 *   <li>{@code findAll()} — SELECT all rows.</li>
 *   <li>{@code deleteById(id)} — DELETE by primary key.</li>
 * </ul>
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // No custom queries needed beyond standard CRUD for this mini-project.
}
