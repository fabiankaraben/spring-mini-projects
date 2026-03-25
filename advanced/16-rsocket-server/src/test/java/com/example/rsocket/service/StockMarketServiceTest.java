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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StockMarketService}.
 *
 * <p>These tests focus on the business logic in the service layer without starting
 * a Spring context, an RSocket server, or a database.
 *
 * <p>Testing strategy:
 * <ul>
 *   <li>{@link ExtendWith(MockitoExtension.class)} — Mockito JUnit 5 extension
 *       initializes mocks and injects them via constructor injection.</li>
 *   <li>{@link Mock} — creates mocks of the two repositories so behavior can
 *       be controlled per test without hitting a real database.</li>
 *   <li>{@link InjectMocks} — creates an instance of {@link StockMarketService}
 *       with the mocked repositories injected.</li>
 *   <li>StepVerifier (from reactor-test) — the idiomatic tool for asserting
 *       reactive pipelines: it subscribes to a Mono/Flux and verifies that
 *       expected items are emitted in the right order, then it completes or errors.</li>
 * </ul>
 *
 * <p>Why unit tests for reactive code?
 *   StepVerifier gives us fine-grained control over what is emitted:
 *   <ul>
 *     <li>{@code .expectNext(value)} — assert the next emitted item.</li>
 *     <li>{@code .expectComplete()} — assert the stream completed normally.</li>
 *     <li>{@code .expectError(Class)} — assert the stream terminated with an error.</li>
 *     <li>{@code .verifyComplete()} — combines subscribe + await + assertion in one call.</li>
 *   </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StockMarketService Unit Tests")
class StockMarketServiceTest {

    /**
     * Mocked repository for stock quotes.
     * All calls to this repository are intercepted by Mockito.
     */
    @Mock
    private StockQuoteRepository quoteRepository;

    /**
     * Mocked repository for trade records.
     */
    @Mock
    private TradeRecordRepository tradeRepository;

    /**
     * The class under test.
     * Mockito injects the two mocked repositories via constructor injection.
     */
    @InjectMocks
    private StockMarketService stockMarketService;

    // =========================================================================
    // Tests for getLatestQuote()
    // =========================================================================

    @Nested
    @DisplayName("getLatestQuote()")
    class GetLatestQuote {

        @Test
        @DisplayName("returns QuoteResponse when symbol exists in repository")
        void returnsQuoteWhenSymbolFound() {
            // Given: a stock quote exists for AAPL.
            StockQuote quote = buildQuote("AAPL", "Apple Inc.", 215.50, +3.25, +1.53, 72_000_000L);
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("AAPL"))
                    .thenReturn(Optional.of(quote));

            // When: we request the latest quote for AAPL.
            Mono<QuoteResponse> result = stockMarketService.getLatestQuote("AAPL");

            // Then: StepVerifier subscribes and verifies the emitted QuoteResponse.
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.symbol()).isEqualTo("AAPL");
                        assertThat(response.companyName()).isEqualTo("Apple Inc.");
                        assertThat(response.price()).isEqualTo(215.50);
                        assertThat(response.change()).isEqualTo(+3.25);
                        assertThat(response.changePercent()).isEqualTo(+1.53);
                        assertThat(response.volume()).isEqualTo(72_000_000L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns empty Mono when symbol does not exist")
        void returnsEmptyWhenSymbolNotFound() {
            // Given: no quote exists for UNKN.
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("UNKN"))
                    .thenReturn(Optional.empty());

            // When: we request the latest quote for an unknown symbol.
            Mono<QuoteResponse> result = stockMarketService.getLatestQuote("UNKN");

            // Then: the Mono completes empty (no item emitted, no error).
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("normalizes symbol to uppercase before querying repository")
        void normalizesSymbolToUppercase() {
            // Given: the repository expects uppercase.
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("AAPL"))
                    .thenReturn(Optional.empty());

            // When: the client passes a lowercase symbol.
            stockMarketService.getLatestQuote("aapl").block();

            // Then: the repository is called with the uppercase version.
            verify(quoteRepository).findTopBySymbolOrderByTimestampDesc("AAPL");
        }
    }

    // =========================================================================
    // Tests for streamQuotes()
    // =========================================================================

    @Nested
    @DisplayName("streamQuotes()")
    class StreamQuotes {

        @Test
        @DisplayName("emits QuoteResponse items with the correct symbol")
        void emitsQuoteResponsesWithCorrectSymbol() {
            // Given: a seed quote exists for GOOG.
            StockQuote seedQuote = buildQuote("GOOG", "Alphabet Inc.", 178.30, -1.10, -0.61, 18_000_000L);
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("GOOG"))
                    .thenReturn(Optional.of(seedQuote));

            // When: we subscribe to the quote stream for GOOG.
            // We take only the first 3 items to avoid an infinite stream in tests.
            Flux<QuoteResponse> stream = stockMarketService.streamQuotes("GOOG").take(3);

            // Then: all three items have the correct symbol.
            StepVerifier.create(stream)
                    .assertNext(r -> assertThat(r.symbol()).isEqualTo("GOOG"))
                    .assertNext(r -> assertThat(r.symbol()).isEqualTo("GOOG"))
                    .assertNext(r -> assertThat(r.symbol()).isEqualTo("GOOG"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("emits QuoteResponse items with positive prices")
        void emitsQuoteResponsesWithPositivePrices() {
            // Given: a seed quote with a known price.
            StockQuote seedQuote = buildQuote("MSFT", "Microsoft Corporation", 420.75, +2.15, +0.51, 24_000_000L);
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("MSFT"))
                    .thenReturn(Optional.of(seedQuote));

            // When: we take the first 5 items from the stream.
            Flux<QuoteResponse> stream = stockMarketService.streamQuotes("MSFT").take(5);

            // Then: all prices are positive (simulated fluctuation stays > 0).
            StepVerifier.create(stream)
                    .thenConsumeWhile(r -> r.price() > 0)
                    .verifyComplete();
        }

        @Test
        @DisplayName("uses default price when no seed quote exists for the symbol")
        void usesDefaultPriceWhenNoSeedQuoteExists() {
            // Given: no quote exists for NEWCO.
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("NEWCO"))
                    .thenReturn(Optional.empty());

            // When: we take the first item from the stream.
            Flux<QuoteResponse> stream = stockMarketService.streamQuotes("NEWCO").take(1);

            // Then: the stream still emits (using 100.0 as default seed price).
            StepVerifier.create(stream)
                    .assertNext(r -> {
                        assertThat(r.symbol()).isEqualTo("NEWCO");
                        assertThat(r.price()).isPositive();
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // Tests for logTrade()
    // =========================================================================

    @Nested
    @DisplayName("logTrade()")
    class LogTrade {

        @Test
        @DisplayName("persists a BUY trade record and completes with no response")
        void persistsBuyTradeAndCompletesEmpty() {
            // Given: the trade repository accepts any save.
            when(tradeRepository.save(any(TradeRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TradeRequest request = new TradeRequest("AAPL", "BUY", 100, 215.50, "trader-001");

            // When: we log the trade.
            Mono<Void> result = stockMarketService.logTrade(request);

            // Then: the Mono completes with no items (Void = fire-and-forget semantics).
            StepVerifier.create(result)
                    .verifyComplete();

            // And: the trade record was persisted with correct values.
            ArgumentCaptor<TradeRecord> captor = ArgumentCaptor.forClass(TradeRecord.class);
            verify(tradeRepository).save(captor.capture());
            TradeRecord saved = captor.getValue();
            assertThat(saved.getSymbol()).isEqualTo("AAPL");
            assertThat(saved.getTradeType()).isEqualTo(TradeType.BUY);
            assertThat(saved.getQuantity()).isEqualTo(100);
            assertThat(saved.getExecutionPrice()).isEqualTo(215.50);
            assertThat(saved.getTraderId()).isEqualTo("trader-001");
        }

        @Test
        @DisplayName("persists a SELL trade and normalizes symbol to uppercase")
        void persistsSellTradeAndNormalizesSymbol() {
            // Given: the trade repository accepts any save.
            when(tradeRepository.save(any(TradeRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Note: symbol is lowercase to test normalization.
            TradeRequest request = new TradeRequest("tsla", "SELL", 50, 248.90, "trader-002");

            // When: we log the trade.
            Mono<Void> result = stockMarketService.logTrade(request);

            // Then: completes without error.
            StepVerifier.create(result)
                    .verifyComplete();

            // And: symbol was uppercased before saving.
            ArgumentCaptor<TradeRecord> captor = ArgumentCaptor.forClass(TradeRecord.class);
            verify(tradeRepository).save(captor.capture());
            assertThat(captor.getValue().getSymbol()).isEqualTo("TSLA");
            assertThat(captor.getValue().getTradeType()).isEqualTo(TradeType.SELL);
        }

        @Test
        @DisplayName("emits error for invalid tradeType string")
        void emitsErrorForInvalidTradeType() {
            // Given: a trade request with an invalid type.
            TradeRequest request = new TradeRequest("AAPL", "HOLD", 10, 215.00, "trader-003");

            // When + Then: the Mono emits an IllegalArgumentException.
            StepVerifier.create(stockMarketService.logTrade(request))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            // And: the repository was never called.
            verify(tradeRepository, never()).save(any());
        }
    }

    // =========================================================================
    // Tests for evaluatePriceAlerts()
    // =========================================================================

    @Nested
    @DisplayName("evaluatePriceAlerts()")
    class EvaluatePriceAlerts {

        @Test
        @DisplayName("emits PriceAlertResponse when current price meets threshold")
        void emitsAlertWhenPriceMeetsThreshold() {
            // Given: AAPL has a current price of 215.50.
            StockQuote quote = buildQuote("AAPL", "Apple Inc.", 215.50, 3.25, 1.53, 72_000_000L);
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("AAPL"))
                    .thenReturn(Optional.of(quote));

            // When: client requests an alert with threshold 200.00 (below current price).
            Flux<PriceAlertRequest> requests = Flux.just(
                    new PriceAlertRequest("AAPL", 200.00)
            );

            Flux<PriceAlertResponse> alerts = stockMarketService.evaluatePriceAlerts(requests);

            // Then: an alert is emitted because 215.50 >= 200.00.
            StepVerifier.create(alerts)
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
        @DisplayName("does not emit alert when current price is below threshold")
        void doesNotEmitAlertWhenPriceBelowThreshold() {
            // Given: AAPL has a current price of 215.50.
            StockQuote quote = buildQuote("AAPL", "Apple Inc.", 215.50, 3.25, 1.53, 72_000_000L);
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("AAPL"))
                    .thenReturn(Optional.of(quote));

            // When: client requests an alert with threshold 300.00 (above current price).
            Flux<PriceAlertRequest> requests = Flux.just(
                    new PriceAlertRequest("AAPL", 300.00)
            );

            Flux<PriceAlertResponse> alerts = stockMarketService.evaluatePriceAlerts(requests);

            // Then: no alert is emitted because 215.50 < 300.00.
            StepVerifier.create(alerts)
                    .verifyComplete();
        }

        @Test
        @DisplayName("emits alert when price exactly equals threshold")
        void emitsAlertWhenPriceExactlyEqualsThreshold() {
            // Given: TSLA has a current price of 248.90.
            StockQuote quote = buildQuote("TSLA", "Tesla Inc.", 248.90, -8.45, -3.28, 95_000_000L);
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("TSLA"))
                    .thenReturn(Optional.of(quote));

            // When: threshold is exactly 248.90 (equal to current price).
            Flux<PriceAlertRequest> requests = Flux.just(
                    new PriceAlertRequest("TSLA", 248.90)
            );

            // Then: an alert IS emitted (>= comparison includes equality).
            StepVerifier.create(stockMarketService.evaluatePriceAlerts(requests))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("does not emit alert for unknown symbol (no quote in repository)")
        void doesNotEmitAlertForUnknownSymbol() {
            // Given: no quote exists for UNKN.
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("UNKN"))
                    .thenReturn(Optional.empty());

            Flux<PriceAlertRequest> requests = Flux.just(
                    new PriceAlertRequest("UNKN", 100.00)
            );

            // Then: no alert emitted for a symbol with no data.
            StepVerifier.create(stockMarketService.evaluatePriceAlerts(requests))
                    .verifyComplete();
        }

        @Test
        @DisplayName("processes multiple alert requests and emits only triggered alerts")
        void processesMultipleRequestsAndEmitsOnlyTriggeredAlerts() {
            // Given: AAPL = 215.50, GOOG = 178.30.
            StockQuote appleQuote = buildQuote("AAPL", "Apple Inc.", 215.50, 3.25, 1.53, 72_000_000L);
            StockQuote googQuote = buildQuote("GOOG", "Alphabet Inc.", 178.30, -1.10, -0.61, 18_000_000L);

            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("AAPL"))
                    .thenReturn(Optional.of(appleQuote));
            when(quoteRepository.findTopBySymbolOrderByTimestampDesc("GOOG"))
                    .thenReturn(Optional.of(googQuote));

            // When: two requests — AAPL threshold 200 (should trigger), GOOG threshold 200 (should NOT).
            Flux<PriceAlertRequest> requests = Flux.just(
                    new PriceAlertRequest("AAPL", 200.00),  // 215.50 >= 200.00 → ALERT
                    new PriceAlertRequest("GOOG", 200.00)   // 178.30 < 200.00  → NO alert
            );

            // Then: only one alert is emitted (for AAPL).
            StepVerifier.create(stockMarketService.evaluatePriceAlerts(requests))
                    .assertNext(alert -> assertThat(alert.symbol()).isEqualTo("AAPL"))
                    .verifyComplete();
        }
    }

    // =========================================================================
    // Tests for toQuoteResponse() (mapping helper)
    // =========================================================================

    @Nested
    @DisplayName("toQuoteResponse()")
    class ToQuoteResponse {

        @Test
        @DisplayName("maps all fields from StockQuote entity to QuoteResponse DTO")
        void mapsAllFieldsCorrectly() {
            // Given: a fully populated StockQuote entity.
            Instant now = Instant.now();
            StockQuote quote = new StockQuote(
                    "AMZN", "Amazon.com Inc.", 198.40, +4.60, +2.37, 35_000_000L, now);

            // When: we map it to a QuoteResponse.
            QuoteResponse response = stockMarketService.toQuoteResponse(quote);

            // Then: all fields are correctly mapped.
            assertThat(response.symbol()).isEqualTo("AMZN");
            assertThat(response.companyName()).isEqualTo("Amazon.com Inc.");
            assertThat(response.price()).isEqualTo(198.40);
            assertThat(response.change()).isEqualTo(+4.60);
            assertThat(response.changePercent()).isEqualTo(+2.37);
            assertThat(response.volume()).isEqualTo(35_000_000L);
            assertThat(response.timestamp()).isEqualTo(now);
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Build a {@link StockQuote} entity for use in test setups.
     *
     * @param symbol        stock ticker
     * @param companyName   company name
     * @param price         current price
     * @param change        price change
     * @param changePercent percent change
     * @param volume        trading volume
     * @return configured StockQuote instance with a current timestamp
     */
    private StockQuote buildQuote(String symbol, String companyName, double price,
                                   double change, double changePercent, long volume) {
        return new StockQuote(symbol, companyName, price, change, changePercent, volume, Instant.now());
    }
}
