package com.example.rsocket.dto;

import java.time.Instant;

/**
 * DTO representing a price alert notification sent from server to client.
 *
 * <p>Emitted by the server in the Request-Channel ({@code priceAlerts}) interaction
 * model whenever the current price of a watched symbol meets or exceeds the
 * threshold specified in the corresponding {@link PriceAlertRequest}.
 *
 * <p>Each {@code PriceAlertResponse} carries enough information for the client
 * to act immediately: the symbol that triggered the alert, the threshold that
 * was set, the actual current price, and the time of the check.
 */
public record PriceAlertResponse(

        /**
         * Stock ticker symbol that triggered the alert.
         */
        String symbol,

        /**
         * The threshold price the client specified.
         */
        Double thresholdPrice,

        /**
         * The current recorded price that crossed the threshold.
         */
        Double currentPrice,

        /**
         * Human-readable alert message (e.g., "AAPL crossed $200.00 threshold at $215.50").
         */
        String message,

        /**
         * Timestamp when the alert was evaluated and emitted by the server.
         */
        Instant alertTime
) {}
