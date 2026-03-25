package com.example.rsocket.domain;

/**
 * Enumeration of trade directions.
 *
 * <p>Used by {@link TradeRecord} to distinguish whether a trade event
 * represents a purchase (BUY) or a sale (SELL) of shares.
 *
 * <p>Stored as a VARCHAR string in the database (via {@code @Enumerated(EnumType.STRING)})
 * so that database queries and audit logs are human-readable without needing to
 * cross-reference ordinal values.
 */
public enum TradeType {

    /**
     * The trader is purchasing shares of a stock.
     */
    BUY,

    /**
     * The trader is selling shares of a stock.
     */
    SELL
}
