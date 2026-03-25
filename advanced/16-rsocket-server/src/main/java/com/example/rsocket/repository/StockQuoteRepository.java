package com.example.rsocket.repository;

import com.example.rsocket.domain.StockQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link StockQuote} entities.
 *
 * <p>Spring Data automatically generates the implementation of this interface
 * at startup by scanning for repository interfaces annotated with {@code @Repository}
 * (or extending {@code JpaRepository}). No SQL is written manually.
 *
 * <p>Provided query methods:
 * <ul>
 *   <li>{@link #findTopBySymbolOrderByTimestampDesc(String)} — returns the most recently
 *       recorded quote for a given symbol. Used by the Request-Response and
 *       Request-Stream handlers to retrieve the current price.</li>
 * </ul>
 *
 * <p>Inherited from {@code JpaRepository}:
 * <ul>
 *   <li>{@code save(entity)} — INSERT or UPDATE.</li>
 *   <li>{@code findAll()}    — SELECT all rows.</li>
 *   <li>{@code findById(id)} — SELECT by primary key.</li>
 *   <li>etc.</li>
 * </ul>
 */
@Repository
public interface StockQuoteRepository extends JpaRepository<StockQuote, Long> {

    /**
     * Find the most recent quote for a given stock symbol.
     *
     * <p>Spring Data derives the query from the method name:
     * <ul>
     *   <li>{@code findTop} — limit result to 1 row.</li>
     *   <li>{@code BySymbol} — WHERE symbol = :symbol.</li>
     *   <li>{@code OrderByTimestampDesc} — ORDER BY timestamp DESC (latest first).</li>
     * </ul>
     *
     * @param symbol the stock ticker symbol (e.g., "AAPL")
     * @return an Optional containing the latest quote, or empty if no quote exists
     */
    Optional<StockQuote> findTopBySymbolOrderByTimestampDesc(String symbol);
}
