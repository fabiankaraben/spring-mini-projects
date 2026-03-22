package com.example.eventsourcing.query.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link AccountSummary} read model.
 *
 * <p>This repository operates on the <em>query side</em> of the CQRS pattern.
 * It reads from the {@code account_summaries} table which is maintained by
 * {@code AccountProjection} (the event handler that updates this table in
 * response to domain events).
 *
 * <p>Note that the write side (the event store) is managed entirely by Axon Framework
 * and is never accessed through this repository.
 */
@Repository
public interface AccountSummaryRepository extends JpaRepository<AccountSummary, String> {

    /**
     * Finds all account summaries with the given status.
     *
     * <p>Used by {@code AccountProjection.handle(FindAllAccountsQuery)} to support
     * optional status filtering on the {@code GET /api/accounts?status=ACTIVE} endpoint.
     *
     * @param status the status to filter by ("ACTIVE" or "CLOSED")
     * @return list of matching account summaries
     */
    List<AccountSummary> findByStatus(String status);
}
