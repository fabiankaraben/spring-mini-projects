package com.example.rsocket.dto;

import java.time.Instant;

/**
 * DTO representing a stock price quote returned to the RSocket client.
 *
 * <p>This record is the wire-transfer representation used on the RSocket boundary.
 * The server maps the {@link com.example.rsocket.domain.StockQuote} JPA entity
 * to this DTO so that the persistence model and the transport model can evolve
 * independently.
 *
 * <p>Used in:
 * <ul>
 *   <li>{@code getQuote} (Request-Response) — single response item.</li>
 *   <li>{@code streamQuotes} (Request-Stream) — one item emitted per tick.</li>
 *   <li>{@code priceAlerts} (Request-Channel) — emitted when a price threshold is crossed.</li>
 * </ul>
 *
 * <p>Spring's RSocket message codec automatically serializes this record to JSON
 * before sending it to the client.
 */
public record QuoteResponse(

        /**
         * Stock ticker symbol (e.g., "AAPL").
         */
        String symbol,

        /**
         * Human-readable company name (e.g., "Apple Inc.").
         */
        String companyName,

        /**
         * Current market price per share in USD.
         */
        Double price,

        /**
         * Price change from the previous trading day's close.
         * Positive means the price is up; negative means it is down.
         */
        Double change,

        /**
         * Percentage change from the previous close.
         * Computed as: (change / previousClose) * 100.
         */
        Double changePercent,

        /**
         * Number of shares traded in the current session.
         */
        Long volume,

        /**
         * Timestamp when this quote was recorded on the server.
         */
        Instant timestamp
) {}
