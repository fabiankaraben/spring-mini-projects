package com.example.rsocket.dto;

/**
 * DTO (Data Transfer Object) for a quote request sent by an RSocket client.
 *
 * <p>Used in:
 * <ul>
 *   <li>{@code getQuote} (Request-Response) — client sends one QuoteRequest,
 *       server responds with one {@link QuoteResponse}.</li>
 *   <li>{@code streamQuotes} (Request-Stream) — client sends one QuoteRequest,
 *       server responds with a continuous stream of {@link QuoteResponse} items.</li>
 * </ul>
 *
 * <p>Spring's RSocket message codec automatically deserializes the incoming
 * JSON payload into this record. The {@code symbol} field identifies which
 * stock the client is interested in (e.g., "AAPL", "GOOG").
 *
 * <p>Why a record?
 *   Java records (introduced in Java 16) are ideal for DTOs because:
 *   <ul>
 *     <li>They are immutable by design — no accidental mutation of request data.</li>
 *     <li>The compiler auto-generates constructor, getters, equals, hashCode, toString.</li>
 *     <li>They clearly communicate intent: this is pure data, not behavior.</li>
 *   </ul>
 */
public record QuoteRequest(

        /**
         * Stock ticker symbol to query (e.g., "AAPL", "GOOG", "MSFT").
         * The server looks up the latest recorded price for this symbol.
         */
        String symbol
) {}
