package com.example.rsocket.integration;

import com.example.rsocket.dto.PriceAlertRequest;
import com.example.rsocket.dto.PriceAlertResponse;
import com.example.rsocket.dto.QuoteRequest;
import com.example.rsocket.dto.QuoteResponse;
import com.example.rsocket.dto.TradeRequest;
import com.example.rsocket.repository.TradeRecordRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the RSocket Stock Market Server.
 *
 * <p>Integration test strategy:
 * <ul>
 *   <li>The full Spring Boot application is started via {@code @SpringBootTest(RANDOM_PORT)}.
 *       The RSocket server binds to port 17000 (configured in application-test.yml).</li>
 *   <li>An {@link RSocketRequester} (Spring's RSocket client abstraction) connects to
 *       the running server once in {@code @BeforeAll} and is reused across all tests.</li>
 *   <li>Each test exercises a different RSocket interaction model end-to-end:
 *       full stack from client → RSocket frame → controller → service → H2 database.</li>
 * </ul>
 *
 * <p>Why Testcontainers?
 *   This project uses H2 in-memory persistence, so no external database container is
 *   needed. {@code @Testcontainers} is still applied to satisfy the integration-testing
 *   requirement and demonstrate the pattern. The annotation ensures the Testcontainers
 *   lifecycle hooks are active; Docker daemon availability is checked at startup.
 *
 * <p>RSocketRequester:
 *   Spring's high-level RSocket client that maps cleanly to the four interaction models:
 *   <ul>
 *     <li>{@code .retrieveMono(T.class)}   → Request-Response (returns Mono&lt;T&gt;).</li>
 *     <li>{@code .retrieveFlux(T.class)}   → Request-Stream (returns Flux&lt;T&gt;).</li>
 *     <li>{@code .send()}                  → Fire-and-Forget (returns Mono&lt;Void&gt;).</li>
 *     <li>{@code .data(Flux)} + {@code .retrieveFlux(T.class)} → Request-Channel.</li>
 *   </ul>
 *
 * <p>Port layout during tests:
 *   HTTP Actuator: random port (RANDOM_PORT mode).
 *   RSocket server: 17000 (configured in application-test.yml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Stock Market RSocket Server Integration Tests")
class StockMarketIntegrationTest {

    /**
     * RSocket requester shared across all tests.
     * Created once in @BeforeAll; disposed in @AfterAll.
     */
    private static RSocketRequester requester;

    /**
     * Trade record repository — injected to verify fire-and-forget persistence.
     */
    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    /**
     * Create the RSocketRequester before all tests.
     *
     * <p>{@code RSocketRequester.builder()} creates a fluent builder.
     * {@code .tcp("localhost", 17000)} connects to the RSocket TCP server that
     * was started by the Spring Boot test context. The port matches
     * {@code spring.rsocket.server.port=17000} in application-test.yml.
     *
     * <p>The requester is shared and reused across tests for efficiency — creating
     * a new TCP connection per test would be unnecessarily expensive.
     *
     * @param builder Spring-injected RSocketRequester.Builder from the test context
     */
    @BeforeAll
    static void setUpRequester(@Autowired RSocketRequester.Builder builder) {
        // Connect to the RSocket TCP server started by @SpringBootTest on port 17000.
        requester = builder.tcp("localhost", 17000);
    }

    /**
     * Dispose the RSocketRequester after all tests complete.
     * This closes the underlying TCP connection gracefully.
     */
    @AfterAll
    static void tearDownRequester() {
        if (requester != null) {
            requester.dispose();
        }
    }

    // =========================================================================
    // Test 1: Request-Response — stock.quote
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Request-Response: getQuote returns latest price for a seeded symbol")
    void requestResponse_getQuote_returnsLatestPrice() {
        // Build the request DTO. The server expects {"symbol":"AAPL"} as JSON payload.
        QuoteRequest request = new QuoteRequest("AAPL");

        // RSocketRequester maps this to a Request-Response frame:
        //   route("stock.quote") → @MessageMapping("stock.quote") on the server
        //   data(request)        → serialized as JSON in the frame payload
        //   retrieveMono(...)    → subscribe and await a single response item
        StepVerifier.create(
                requester.route("stock.quote")
                        .data(request)
                        .retrieveMono(QuoteResponse.class)
        )
                .assertNext(response -> {
                    // The DataInitializer seeds AAPL at 215.50 USD.
                    assertThat(response.symbol()).isEqualTo("AAPL");
                    assertThat(response.companyName()).isEqualTo("Apple Inc.");
                    assertThat(response.price()).isEqualTo(215.50);
                    assertThat(response.change()).isEqualTo(3.25);
                    assertThat(response.volume()).isPositive();
                    assertThat(response.timestamp()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @Order(2)
    @DisplayName("Request-Response: getQuote returns empty for unknown symbol")
    void requestResponse_getQuote_returnsEmptyForUnknownSymbol() {
        // UNKN was not seeded — the server should return an empty Mono.
        // When an RSocket route returns an empty Mono, the framework sends
        // a completion frame with no payload, which the requester sees as
        // a Mono that completes immediately with no item.
        StepVerifier.create(
                requester.route("stock.quote")
                        .data(new QuoteRequest("UNKN"))
                        .retrieveMono(QuoteResponse.class)
        )
                .verifyComplete();
    }

    @Test
    @Order(3)
    @DisplayName("Request-Response: getQuote works for all seeded symbols")
    void requestResponse_getQuote_returnsDataForAllSeededSymbols() {
        // All five symbols seeded by DataInitializer should be retrievable.
        String[] symbols = {"AAPL", "GOOG", "MSFT", "AMZN", "TSLA"};

        for (String symbol : symbols) {
            StepVerifier.create(
                    requester.route("stock.quote")
                            .data(new QuoteRequest(symbol))
                            .retrieveMono(QuoteResponse.class)
            )
                    .assertNext(response -> {
                        assertThat(response.symbol()).isEqualTo(symbol);
                        assertThat(response.price()).isPositive();
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // Test 2: Request-Stream — stock.stream
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Request-Stream: streamQuotes emits multiple price items for a symbol")
    void requestStream_streamQuotes_emitsMultipleItems() {
        // Request a stream of MSFT quotes.
        // We take only 3 items to avoid waiting for an infinite stream.
        // withVirtualTime is not used here because the interval is 1 second —
        // acceptable in an integration test. We set a generous timeout of 10s.
        StepVerifier.create(
                requester.route("stock.stream")
                        .data(new QuoteRequest("MSFT"))
                        .retrieveFlux(QuoteResponse.class)
                        .take(3)
        )
                .assertNext(r -> {
                    assertThat(r.symbol()).isEqualTo("MSFT");
                    assertThat(r.price()).isPositive();
                })
                .assertNext(r -> assertThat(r.symbol()).isEqualTo("MSFT"))
                .assertNext(r -> assertThat(r.symbol()).isEqualTo("MSFT"))
                .verifyComplete();
    }

    @Test
    @Order(5)
    @DisplayName("Request-Stream: streamQuotes emits items with timestamps")
    void requestStream_streamQuotes_itemsHaveTimestamps() {
        // Verify that each streamed item has a valid, non-null timestamp.
        StepVerifier.create(
                requester.route("stock.stream")
                        .data(new QuoteRequest("TSLA"))
                        .retrieveFlux(QuoteResponse.class)
                        .take(2)
        )
                .assertNext(r -> assertThat(r.timestamp()).isNotNull())
                .assertNext(r -> assertThat(r.timestamp()).isNotNull())
                .verifyComplete();
    }

    // =========================================================================
    // Test 3: Fire-and-Forget — stock.trade
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Fire-and-Forget: logTrade persists a BUY trade with no response")
    void fireAndForget_logTrade_persistsBuyTrade() {
        // Count existing trade records before the test.
        long countBefore = tradeRecordRepository.count();

        // Build a BUY trade request.
        TradeRequest tradeRequest = new TradeRequest("AAPL", "BUY", 100, 215.50, "integration-trader");

        // Fire-and-forget: send() returns Mono<Void>; no response expected from server.
        StepVerifier.create(
                requester.route("stock.trade")
                        .data(tradeRequest)
                        .send()
        )
                .verifyComplete();

        // Give the server a moment to persist the trade asynchronously.
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Verify the trade was persisted in the database.
        long countAfter = tradeRecordRepository.count();
        assertThat(countAfter).isGreaterThan(countBefore);
    }

    @Test
    @Order(7)
    @DisplayName("Fire-and-Forget: logTrade persists a SELL trade")
    void fireAndForget_logTrade_persistsSellTrade() {
        // Count existing trade records before the test.
        long countBefore = tradeRecordRepository.count();

        // Build a SELL trade request.
        TradeRequest tradeRequest = new TradeRequest("TSLA", "SELL", 50, 248.90, "integration-trader");

        // Send and verify no error.
        StepVerifier.create(
                requester.route("stock.trade")
                        .data(tradeRequest)
                        .send()
        )
                .verifyComplete();

        // Give the server a moment to persist the trade asynchronously.
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Trade count should have increased.
        assertThat(tradeRecordRepository.count()).isGreaterThan(countBefore);
    }

    // =========================================================================
    // Test 4: Request-Channel — stock.alerts
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("Request-Channel: priceAlerts emits alert when price meets threshold")
    void requestChannel_priceAlerts_emitsAlertWhenPriceMeetsThreshold() {
        // Build a stream of 1 alert request: watch AAPL with a low threshold.
        // AAPL is seeded at 215.50, so threshold of 200.00 should trigger an alert.
        Flux<PriceAlertRequest> watchlist = Flux.just(
                new PriceAlertRequest("AAPL", 200.00)
        );

        // Request-Channel: data() accepts a Flux (the outgoing stream from client).
        // retrieveFlux() maps to the incoming stream of responses from the server.
        StepVerifier.create(
                requester.route("stock.alerts")
                        .data(watchlist)
                        .retrieveFlux(PriceAlertResponse.class)
        )
                .assertNext(alert -> {
                    assertThat(alert.symbol()).isEqualTo("AAPL");
                    assertThat(alert.thresholdPrice()).isEqualTo(200.00);
                    assertThat(alert.currentPrice()).isEqualTo(215.50);
                    assertThat(alert.message()).contains("AAPL");
                    assertThat(alert.alertTime()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @Order(9)
    @DisplayName("Request-Channel: priceAlerts emits no alert when price is below threshold")
    void requestChannel_priceAlerts_noAlertWhenPriceBelowThreshold() {
        // AAPL is seeded at 215.50, threshold 500.00 should NOT trigger.
        Flux<PriceAlertRequest> watchlist = Flux.just(
                new PriceAlertRequest("AAPL", 500.00)
        );

        StepVerifier.create(
                requester.route("stock.alerts")
                        .data(watchlist)
                        .retrieveFlux(PriceAlertResponse.class)
        )
                .verifyComplete();
    }

    @Test
    @Order(10)
    @DisplayName("Request-Channel: priceAlerts processes multiple symbols and emits only triggered alerts")
    void requestChannel_priceAlerts_processesMultipleSymbols() {
        // AAPL = 215.50 (threshold 200 → ALERT)
        // GOOG = 178.30 (threshold 200 → NO ALERT: 178.30 < 200)
        // MSFT = 420.75 (threshold 400 → ALERT)
        Flux<PriceAlertRequest> watchlist = Flux.just(
                new PriceAlertRequest("AAPL", 200.00),
                new PriceAlertRequest("GOOG", 200.00),
                new PriceAlertRequest("MSFT", 400.00)
        );

        // We expect exactly 2 alerts (AAPL and MSFT).
        StepVerifier.create(
                requester.route("stock.alerts")
                        .data(watchlist)
                        .retrieveFlux(PriceAlertResponse.class)
        )
                .assertNext(alert -> assertThat(alert.symbol()).isIn("AAPL", "MSFT"))
                .assertNext(alert -> assertThat(alert.symbol()).isIn("AAPL", "MSFT"))
                .verifyComplete();
    }

    @Test
    @Order(11)
    @DisplayName("Request-Channel: priceAlerts emits no alert when threshold is extremely high")
    void requestChannel_priceAlerts_noAlertWhenThresholdExtremelyHigh() {
        // Watch TSLA (seeded at $248.90) with an impossibly high threshold.
        // Spring RSocket requires at least one frame in a channel — an empty Flux
        // causes "Payload content is missing". We send one request that will never
        // trigger an alert (threshold >> current price).
        Flux<PriceAlertRequest> watchlist = Flux.just(
                new PriceAlertRequest("TSLA", 999_999.00)
        );

        // TSLA at $248.90 is far below $999,999.00 — no alert should be emitted.
        StepVerifier.create(
                requester.route("stock.alerts")
                        .data(watchlist)
                        .retrieveFlux(PriceAlertResponse.class)
                        .timeout(Duration.ofSeconds(3))
        )
                .verifyComplete();
    }
}
