package com.example.rsocket.dto;

/**
 * DTO representing a price-alert subscription for a single stock symbol.
 *
 * <p>Used in the Request-Channel interaction model ({@code priceAlerts}):
 * the client sends a continuous stream of {@code PriceAlertRequest} items,
 * each specifying a symbol and the threshold price at which the client wants
 * to be notified.
 *
 * <p>The server processes each item from the incoming stream and, for each one,
 * checks whether the current recorded price crosses the given threshold.
 * If it does, a {@link PriceAlertResponse} is emitted back to the client on
 * the outgoing channel.
 *
 * <p>This bidirectional (channel) pattern is powerful for:
 * <ul>
 *   <li>Real-time price alerting systems where the user's watchlist changes dynamically.</li>
 *   <li>Adaptive streaming — the client can add or remove symbols mid-stream.</li>
 * </ul>
 */
public record PriceAlertRequest(

        /**
         * Stock ticker symbol to watch (e.g., "AAPL").
         */
        String symbol,

        /**
         * Price threshold in USD.
         * An alert is triggered when the current price is at or above this value.
         */
        Double thresholdPrice
) {}
