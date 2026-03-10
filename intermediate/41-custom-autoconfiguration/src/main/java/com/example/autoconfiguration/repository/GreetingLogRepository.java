package com.example.autoconfiguration.repository;

import com.example.autoconfiguration.entity.GreetingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link GreetingLog} entities.
 *
 * <p>Spring Data automatically provides the standard CRUD methods
 * ({@code findById}, {@code findAll}, {@code save}, {@code deleteById}, etc.)
 * by extending {@link JpaRepository}. No implementation class is needed.
 *
 * <p>The two custom query methods below use Spring Data's method-name derivation:
 * Spring parses the method name at application startup and generates the
 * corresponding JPQL query automatically. No {@code @Query} annotation needed.
 */
public interface GreetingLogRepository extends JpaRepository<GreetingLog, Long> {

    /**
     * Finds all greeting log records for a given name (case-sensitive).
     *
     * <p>Spring Data derives the query: {@code SELECT g FROM GreetingLog g WHERE g.name = :name}
     *
     * @param name the name to filter by
     * @return list of matching greeting logs, empty list if none found
     */
    List<GreetingLog> findByName(String name);

    /**
     * Finds all greeting log records ordered by creation time descending
     * (most recent first).
     *
     * <p>Spring Data derives the query:
     * {@code SELECT g FROM GreetingLog g ORDER BY g.createdAt DESC}
     *
     * @return all greeting logs, newest first
     */
    List<GreetingLog> findAllByOrderByCreatedAtDesc();
}
