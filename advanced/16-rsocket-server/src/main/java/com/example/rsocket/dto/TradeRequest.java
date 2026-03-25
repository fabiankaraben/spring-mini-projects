package com.example.rsocket.dto;

/**
 * DTO representing a trade event submitted by an RSocket client.
 *
 * <p>Used exclusively in the Fire-and-Forget interaction model:
 * {@code logTrade} — the client sends one {@code TradeRequest} and receives
 * no response. The server logs the trade asynchronously without blocking
 * the client.
 *
 * <p>This pattern is ideal for high-frequency, low-latency event recording
 * where the producer should not be slowed down by the consumer's persistence layer.
 *
 * <p>The {@code tradeType} field is expected to be either {@code "BUY"} or {@code "SELL"},
 * matching the {@link com.example.rsocket.domain.TradeType} enum values.
 */
public record TradeRequest(

        /**
         * Stock ticker symbol being traded (e.g., "AAPL", "MSFT").
         */
        String symbol,

        /**
         * Direction of the trade: "BUY" or "SELL".
         * Parsed into {@link com.example.rsocket.domain.TradeType} in the service layer.
         */
        String tradeType,

        /**
         * Number of shares in this order. Must be at least 1.
         */
        Integer quantity,

        /**
         * Price per share at which the trade was executed, in USD.
         */
        Double executionPrice,

        /**
         * Identifier of the trader submitting the order.
         * Used for auditing and trade history.
         */
        String traderId
) {}
