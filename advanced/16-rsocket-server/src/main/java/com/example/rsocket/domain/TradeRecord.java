package com.example.rsocket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * JPA entity representing a single trade event.
 *
 * <p>A {@code TradeRecord} is created whenever a client submits a buy or sell
 * order via the Fire-and-Forget RSocket interaction model. Because fire-and-forget
 * requires no response, the server logs the trade asynchronously without making
 * the client wait for confirmation.
 *
 * <p>This design reflects real-world event sourcing patterns where:
 * <ul>
 *   <li>High-frequency trading events are captured as immutable append-only records.</li>
 *   <li>The producer (client) is never blocked by the consumer (server) persistence layer.</li>
 *   <li>Backpressure is managed at the RSocket protocol level.</li>
 * </ul>
 */
@Entity
@Table(name = "trade_records")
public class TradeRecord {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stock ticker symbol being traded (e.g., "AAPL").
     */
    @NotBlank(message = "Symbol must not be blank")
    @Column(nullable = false, length = 20)
    private String symbol;

    /**
     * Type of trade: BUY or SELL.
     * Stored as a VARCHAR string for database readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeType tradeType;

    /**
     * Number of shares traded. Must be at least 1.
     */
    @Positive(message = "Quantity must be positive")
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Price per share at which the trade was executed, in USD.
     */
    @Positive(message = "Execution price must be positive")
    @Column(nullable = false)
    private Double executionPrice;

    /**
     * ID of the trader who submitted this order.
     * Used for auditing and reporting purposes.
     */
    @Column(nullable = false, length = 50)
    private String traderId;

    /**
     * Timestamp when the trade was received and logged by the server.
     */
    @Column(nullable = false)
    private Instant timestamp;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** JPA requires a no-arg constructor. */
    protected TradeRecord() {}

    /**
     * Full constructor used by the service layer.
     *
     * @param symbol         stock ticker symbol
     * @param tradeType      BUY or SELL
     * @param quantity       number of shares
     * @param executionPrice price per share
     * @param traderId       ID of the trader
     * @param timestamp      time the trade was logged
     */
    public TradeRecord(String symbol, TradeType tradeType, Integer quantity,
                       Double executionPrice, String traderId, Instant timestamp) {
        this.symbol = symbol;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.traderId = traderId;
        this.timestamp = timestamp;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public TradeType getTradeType() { return tradeType; }
    public void setTradeType(TradeType tradeType) { this.tradeType = tradeType; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(Double executionPrice) { this.executionPrice = executionPrice; }

    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
