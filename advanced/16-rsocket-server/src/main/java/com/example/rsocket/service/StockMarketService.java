package com.example.rsocket.service;

import com.example.rsocket.domain.StockQuote;
import com.example.rsocket.domain.TradeRecord;
import com.example.rsocket.domain.TradeType;
import com.example.rsocket.dto.PriceAlertRequest;
import com.example.rsocket.dto.PriceAlertResponse;
import com.example.rsocket.dto.QuoteResponse;
import com.example.rsocket.dto.TradeRequest;
import com.example.rsocket.repository.StockQuoteRepository;
import com.example.rsocket.repository.TradeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

/**
 * Service layer for the Stock Market Ticker domain.
 *
 * <p>This class encapsulates all business logic for quote retrieval, trade logging,
 * and price alert evaluation. It is intentionally decoupled from RSocket — it
 * operates on JPA entities and returns reactive types (Mono/Flux) that the
 * RSocket controller layer subscribes to.
 *
 * <p>Reactive types used:
 * <ul>
 *   <li>{@code Mono<T>}  — represents a single asynchronous value (0 or 1 item).</li>
 *   <li>{@code Flux<T>}  — represents an asynchronous sequence of 0..N items.</li>
 * </ul>
 *
 * <p>Why wrap JPA calls in Mono/Flux?
 *   JPA calls are inherently blocking (they talk to a database on the calling thread).
 *   We wrap them in {@code Mono.fromCallable()} to signal intent and integrate cleanly
 *   with the reactive controller. For true non-blocking persistence, one would use
 *   Spring Data R2DBC; for this educational project, JPA+H2 is used for simplicity.
 *
 * <p>Transaction management:
 *   Write operations are annotated with {@code @Transactional} to guarantee
 *   atomicity. Read operations use {@code readOnly = true} as a hint to the JPA
 *   provider to skip dirty-checking on the read-only transaction.
 */
@Service
@Transactional(readOnly = true)
public class StockMarketService {

    /** Repository for stock quote persistence and retrieval. */
    private final StockQuoteRepository quoteRepository;

    /** Repository for trade record persistence and retrieval. */
    private final TradeRecordRepository tradeRepository;

    /**
     * Random number generator used to simulate live price fluctuations in
     * the streaming endpoint. In a real system, prices come from a market data feed.
     */
    private final Random random = new Random();

    /**
     * Constructor injection — preferred over field injection because:
     * <ul>
     *   <li>Dependencies are required and immutable (final).</li>
     *   <li>Easier to unit-test (no Spring context needed — just pass mock instances).</li>
     * </ul>
     *
     * @param quoteRepository  JPA repository for stock quotes
     * @param tradeRepository  JPA repository for trade records
     */
    public StockMarketService(StockQuoteRepository quoteRepository,
                               TradeRecordRepository tradeRepository) {
        this.quoteRepository = quoteRepository;
        this.tradeRepository = tradeRepository;
    }

    // =========================================================================
    // Request-Response: getQuote
    // =========================================================================

    /**
     * Retrieve the most recent price quote for the given stock symbol.
     *
     * <p>This is used by the {@code stock.quote} RSocket route (Request-Response).
     * Returns a Mono that emits one {@link QuoteResponse} or completes empty if
     * the symbol is unknown.
     *
     * @param symbol stock ticker symbol (e.g., "AAPL")
     * @return Mono emitting the latest quote, or empty if not found
     */
    public Mono<QuoteResponse> getLatestQuote(String symbol) {
        // Wrap the blocking JPA call in Mono.fromCallable so it integrates
        // cleanly with the reactive pipeline without blocking the event loop.
        return Mono.fromCallable(() ->
                quoteRepository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase())
                        .map(this::toQuoteResponse)
                        .orElse(null)
        );
    }

    // =========================================================================
    // Request-Stream: streamQuotes
    // =========================================================================

    /**
     * Produce a live price stream for the given stock symbol.
     *
     * <p>This is used by the {@code stock.stream} RSocket route (Request-Stream).
     * Emits one quote every second, simulating a real-time market data feed by
     * applying a small random price variation to the last known price.
     *
     * <p>Backpressure: Reactor's {@code Flux.interval} respects downstream demand.
     * If the consumer is slow, it receives only as many items as it requested.
     *
     * @param symbol stock ticker symbol to stream
     * @return infinite Flux of QuoteResponse items (one per second)
     */
    public Flux<QuoteResponse> streamQuotes(String symbol) {
        String upperSymbol = symbol.toUpperCase();

        // Find the seed price — the base from which we simulate fluctuations.
        // If no quote exists yet, default to 100.0 USD.
        Optional<StockQuote> seedQuote = quoteRepository.findTopBySymbolOrderByTimestampDesc(upperSymbol);
        double seedPrice = seedQuote.map(StockQuote::getPrice).orElse(100.0);
        String companyName = seedQuote.map(StockQuote::getCompanyName).orElse(upperSymbol);
        long seedVolume = seedQuote.map(StockQuote::getVolume).orElse(1_000_000L);

        // Use a simple holder array to carry mutable state across lambda boundaries.
        // (Lambdas require effectively-final variables; array elements are mutable.)
        double[] currentPrice = {seedPrice};

        // Flux.interval emits a monotonically increasing Long every second (Duration.ofSeconds(1)).
        // We map each tick to a simulated QuoteResponse with a price that fluctuates ±2%.
        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> {
                    // Simulate price movement: ±2% random walk per tick.
                    double fluctuation = currentPrice[0] * (random.nextGaussian() * 0.01);
                    currentPrice[0] = Math.max(0.01, currentPrice[0] + fluctuation);
                    double change = currentPrice[0] - seedPrice;
                    double changePercent = (change / seedPrice) * 100.0;

                    return new QuoteResponse(
                            upperSymbol,
                            companyName,
                            Math.round(currentPrice[0] * 100.0) / 100.0,
                            Math.round(change * 100.0) / 100.0,
                            Math.round(changePercent * 100.0) / 100.0,
                            seedVolume + (long) (random.nextDouble() * 10_000),
                            Instant.now()
                    );
                });
    }

    // =========================================================================
    // Fire-and-Forget: logTrade
    // =========================================================================

    /**
     * Log a trade event received from a client.
     *
     * <p>This is used by the {@code stock.trade} RSocket route (Fire-and-Forget).
     * Returns {@code Mono<Void>} — the server does not send any response to the client.
     * The client fires the request and immediately continues without waiting.
     *
     * <p>The trade is persisted asynchronously. If persistence fails, the error
     * is logged but does NOT propagate back to the client (fire-and-forget semantics).
     *
     * @param request the trade event submitted by the client
     * @return Mono<Void> — completes when the trade is persisted
     */
    @Transactional
    public Mono<Void> logTrade(TradeRequest request) {
        return Mono.fromRunnable(() -> {
            // Parse the trade type string into the enum value.
            // IllegalArgumentException thrown here if the value is invalid.
            TradeType tradeType;
            try {
                tradeType = TradeType.valueOf(request.tradeType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid tradeType: '" + request.tradeType() + "'. Must be BUY or SELL.");
            }

            // Build and persist the trade record.
            TradeRecord record = new TradeRecord(
                    request.symbol().toUpperCase(),
                    tradeType,
                    request.quantity(),
                    request.executionPrice(),
                    request.traderId(),
                    Instant.now()
            );
            tradeRepository.save(record);
        });
    }

    // =========================================================================
    // Request-Channel: priceAlerts
    // =========================================================================

    /**
     * Process a stream of price-alert subscriptions and emit alert notifications.
     *
     * <p>This is used by the {@code stock.alerts} RSocket route (Request-Channel).
     * For each {@link PriceAlertRequest} in the incoming stream, the server checks
     * whether the current recorded price for the symbol meets or exceeds the threshold.
     * If so, a {@link PriceAlertResponse} is emitted on the outgoing stream.
     *
     * <p>The bidirectional channel allows the client to dynamically add or remove
     * symbols from its watchlist without establishing a new connection.
     *
     * @param requests incoming stream of price-alert subscription requests
     * @return outgoing stream of price-alert notifications (only for triggered alerts)
     */
    public Flux<PriceAlertResponse> evaluatePriceAlerts(Flux<PriceAlertRequest> requests) {
        // For each inbound PriceAlertRequest, look up the current price and decide
        // whether to emit an alert. flatMap is used because each check returns a Mono
        // (possibly empty), and flatMap merges all the Mono results into the output Flux.
        return requests.flatMap(alertRequest -> {
            String symbol = alertRequest.symbol().toUpperCase();
            double threshold = alertRequest.thresholdPrice();

            // Look up the latest recorded price for this symbol.
            Optional<StockQuote> latestQuote =
                    quoteRepository.findTopBySymbolOrderByTimestampDesc(symbol);

            if (latestQuote.isEmpty()) {
                // No data for this symbol — no alert emitted.
                return Mono.empty();
            }

            double currentPrice = latestQuote.get().getPrice();

            if (currentPrice >= threshold) {
                // Price has crossed the threshold — emit an alert.
                String message = String.format(
                        "%s crossed $%.2f threshold at $%.2f", symbol, threshold, currentPrice);

                return Mono.just(new PriceAlertResponse(
                        symbol,
                        threshold,
                        currentPrice,
                        message,
                        Instant.now()
                ));
            }

            // Price is below the threshold — no alert emitted for this request.
            return Mono.empty();
        });
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Map a {@link StockQuote} JPA entity to a {@link QuoteResponse} DTO.
     *
     * <p>Keeping the mapping in the service layer ensures that the controller
     * never accesses JPA entities directly, preserving clean layer separation.
     *
     * @param quote the JPA entity to convert
     * @return the DTO representation suitable for sending over RSocket
     */
    public QuoteResponse toQuoteResponse(StockQuote quote) {
        return new QuoteResponse(
                quote.getSymbol(),
                quote.getCompanyName(),
                quote.getPrice(),
                quote.getChange(),
                quote.getChangePercent(),
                quote.getVolume(),
                quote.getTimestamp()
        );
    }
}
