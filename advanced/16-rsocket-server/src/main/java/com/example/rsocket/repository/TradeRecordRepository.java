package com.example.rsocket.repository;

import com.example.rsocket.domain.TradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TradeRecord} entities.
 *
 * <p>Trade records are created by the Fire-and-Forget RSocket handler
 * ({@code logTrade}) and are never updated — they form an append-only audit log.
 *
 * <p>Provided query methods:
 * <ul>
 *   <li>{@link #findBySymbolOrderByTimestampDesc(String)} — retrieves all trade records
 *       for a given symbol, ordered from most recent to oldest. Useful for trade history
 *       lookups and reporting.</li>
 *   <li>{@link #findByTraderId(String)} — retrieves all trades submitted by a specific
 *       trader. Useful for per-user activity auditing.</li>
 * </ul>
 */
@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {

    /**
     * Find all trades for a given stock symbol, most recent first.
     *
     * <p>Spring Data derives the query:
     *   SELECT * FROM trade_records WHERE symbol = :symbol ORDER BY timestamp DESC
     *
     * @param symbol the stock ticker symbol (e.g., "AAPL")
     * @return list of trade records for the symbol, ordered by timestamp descending
     */
    List<TradeRecord> findBySymbolOrderByTimestampDesc(String symbol);

    /**
     * Find all trades submitted by a specific trader.
     *
     * <p>Spring Data derives the query:
     *   SELECT * FROM trade_records WHERE trader_id = :traderId
     *
     * @param traderId the trader's identifier
     * @return list of trade records for the given trader
     */
    List<TradeRecord> findByTraderId(String traderId);
}
