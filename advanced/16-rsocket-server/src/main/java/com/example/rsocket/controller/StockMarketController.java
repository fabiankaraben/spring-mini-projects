package com.example.rsocket.controller;

import com.example.rsocket.dto.PriceAlertRequest;
import com.example.rsocket.dto.PriceAlertResponse;
import com.example.rsocket.dto.QuoteRequest;
import com.example.rsocket.dto.QuoteResponse;
import com.example.rsocket.dto.TradeRequest;
import com.example.rsocket.service.StockMarketService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RSocket controller exposing all four interaction models for the Stock Market Ticker domain.
 *
 * <p>How RSocket routing works in Spring Boot:
 * <ul>
 *   <li>{@code @Controller} marks this class as an RSocket message handler
 *       (analogous to {@code @RestController} for HTTP).</li>
 *   <li>{@code @MessageMapping("route.name")} maps an incoming RSocket frame
 *       to a handler method based on the route metadata sent by the client
 *       (analogous to {@code @GetMapping("/path")} for HTTP).</li>
 *   <li>The return type of the handler method determines the interaction model:
 *       <ul>
 *         <li>{@code Mono<T>}    → Request-Response (one request, one response).</li>
 *         <li>{@code Flux<T>}    → Request-Stream   (one request, many responses).</li>
 *         <li>{@code Mono<Void>} → Fire-and-Forget  (one request, no response).</li>
 *         <li>{@code Flux<T>} accepting {@code Flux<R>} → Request-Channel (many↔many).</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Message codec:
 *   Spring Boot auto-configures Jackson as the default RSocket message codec,
 *   so Java records (DTOs) are automatically serialized/deserialized to/from JSON.
 *
 * <p>Routes defined here:
 * <pre>
 *   stock.quote   – Request-Response:  get the latest price for a symbol
 *   stock.stream  – Request-Stream:    subscribe to a live price stream
 *   stock.trade   – Fire-and-Forget:   log a trade event (no response)
 *   stock.alerts  – Request-Channel:   watch symbols, receive price alerts
 * </pre>
 */
@Controller
public class StockMarketController {

    /**
     * Service that encapsulates all business logic.
     * Injected via constructor to keep the controller thin and testable.
     */
    private final StockMarketService stockMarketService;

    /**
     * Constructor injection ensures the dependency is required and immutable.
     *
     * @param stockMarketService the stock market domain service
     */
    public StockMarketController(StockMarketService stockMarketService) {
        this.stockMarketService = stockMarketService;
    }

    // =========================================================================
    // 1. Request-Response: stock.quote
    // =========================================================================

    /**
     * Fetch the latest price quote for a given stock symbol.
     *
     * <p><b>Interaction model: Request-Response</b>
     * <ul>
     *   <li>Client sends one {@link QuoteRequest} frame.</li>
     *   <li>Server responds with one {@link QuoteResponse} frame.</li>
     *   <li>If the symbol is not found, the Mono completes empty and Spring
     *       returns an APPLICATION_ERROR frame to the client.</li>
     * </ul>
     *
     * <p>Return type {@code Mono<QuoteResponse>} signals to the RSocket framework
     * that this is a Request-Response handler.
     *
     * @param request the quote request containing the stock symbol
     * @return Mono emitting one QuoteResponse, or empty if the symbol is unknown
     */
    @MessageMapping("stock.quote")
    public Mono<QuoteResponse> getQuote(QuoteRequest request) {
        return stockMarketService.getLatestQuote(request.symbol());
    }

    // =========================================================================
    // 2. Request-Stream: stock.stream
    // =========================================================================

    /**
     * Subscribe to a live price stream for a given stock symbol.
     *
     * <p><b>Interaction model: Request-Stream</b>
     * <ul>
     *   <li>Client sends one {@link QuoteRequest} frame.</li>
     *   <li>Server responds with a continuous stream of {@link QuoteResponse} frames
     *       (one per second, simulating a real-time market data feed).</li>
     *   <li>The client controls backpressure via the RSocket REQUEST_N mechanism —
     *       it asks for N items at a time, preventing the server from overwhelming it.</li>
     *   <li>The stream runs until the client cancels (sends a CANCEL frame) or
     *       the server terminates the connection.</li>
     * </ul>
     *
     * <p>Return type {@code Flux<QuoteResponse>} signals to the RSocket framework
     * that this is a Request-Stream handler.
     *
     * @param request the quote request containing the stock symbol to stream
     * @return Flux emitting QuoteResponse items at 1-second intervals
     */
    @MessageMapping("stock.stream")
    public Flux<QuoteResponse> streamQuotes(QuoteRequest request) {
        return stockMarketService.streamQuotes(request.symbol());
    }

    // =========================================================================
    // 3. Fire-and-Forget: stock.trade
    // =========================================================================

    /**
     * Record a trade event sent by a client (no response is sent back).
     *
     * <p><b>Interaction model: Fire-and-Forget</b>
     * <ul>
     *   <li>Client sends one {@link TradeRequest} frame.</li>
     *   <li>Server does NOT send any response frame.</li>
     *   <li>The client continues immediately after sending, without waiting.</li>
     *   <li>This is ideal for high-frequency event logging where the producer
     *       should not be blocked by the consumer's persistence latency.</li>
     * </ul>
     *
     * <p>Return type {@code Mono<Void>} signals to the RSocket framework
     * that this is a Fire-and-Forget handler.
     *
     * @param request the trade event (symbol, tradeType, quantity, price, traderId)
     * @return Mono<Void> — completes when the trade is logged; no response to client
     */
    @MessageMapping("stock.trade")
    public Mono<Void> logTrade(TradeRequest request) {
        return stockMarketService.logTrade(request);
    }

    // =========================================================================
    // 4. Request-Channel: stock.alerts
    // =========================================================================

    /**
     * Bidirectional stream: receive a stream of watchlist subscriptions, emit alerts.
     *
     * <p><b>Interaction model: Request-Channel</b>
     * <ul>
     *   <li>Client sends a {@code Flux<PriceAlertRequest>} — a stream of symbols and
     *       threshold prices it wants to watch.</li>
     *   <li>Server responds with a {@code Flux<PriceAlertResponse>} — emitting an alert
     *       for each symbol whose current price meets or exceeds the threshold.</li>
     *   <li>Both streams remain open simultaneously (full-duplex communication).</li>
     *   <li>The client can add new symbols to watch by emitting more requests;
     *       alerts are emitted asynchronously as checks complete.</li>
     * </ul>
     *
     * <p>Spring identifies this as a Request-Channel handler because the method
     * accepts a {@code Flux<>} parameter AND returns a {@code Flux<>}.
     *
     * @param requests incoming stream of price-alert subscription requests
     * @return outgoing stream of price-alert notifications
     */
    @MessageMapping("stock.alerts")
    public Flux<PriceAlertResponse> priceAlerts(Flux<PriceAlertRequest> requests) {
        return stockMarketService.evaluatePriceAlerts(requests);
    }
}
