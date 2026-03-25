package com.example.rsocket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * JPA entity representing a stock price quote.
 *
 * <p>A {@code StockQuote} captures the market price of a traded stock symbol
 * at a specific point in time. It is the core domain object used by:
 * <ul>
 *   <li>{@code getQuote}     — Request-Response: return the latest quote for a symbol.</li>
 *   <li>{@code streamQuotes} — Request-Stream: emit a continuous price stream for a symbol.</li>
 *   <li>{@code priceAlerts}  — Request-Channel: emit alert events when prices cross thresholds.</li>
 * </ul>
 *
 * <p>Persistence note:
 *   This entity is stored in H2 (in-memory) and seeded by {@link com.example.rsocket.config.DataInitializer}.
 *   In a real trading system, quotes would arrive from a market-data feed and be
 *   persisted to a time-series database or a message broker.
 */
@Entity
@Table(name = "stock_quotes")
public class StockQuote {

    /**
     * Auto-generated primary key.
     * Uses H2's IDENTITY (auto-increment) strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stock ticker symbol (e.g., "AAPL", "GOOG", "MSFT").
     * Must not be blank; stored as VARCHAR(20).
     */
    @NotBlank(message = "Symbol must not be blank")
    @Column(nullable = false, length = 20)
    private String symbol;

    /**
     * Company name (e.g., "Apple Inc.").
     * Provides a human-readable label alongside the ticker symbol.
     */
    @Column(nullable = false, length = 100)
    private String companyName;

    /**
     * Current market price per share in USD.
     * Must be strictly positive.
     */
    @Positive(message = "Price must be positive")
    @Column(nullable = false)
    private Double price;

    /**
     * Price change from the previous trading day's close.
     * Positive = the stock went up; negative = the stock went down.
     */
    @Column(nullable = false)
    private Double change;

    /**
     * Percentage change from the previous close.
     * Computed as: (change / previousClose) * 100.
     */
    @Column(nullable = false)
    private Double changePercent;

    /**
     * Number of shares traded in the current session.
     */
    @Column(nullable = false)
    private Long volume;

    /**
     * Timestamp when this quote was recorded.
     * Stored as a UTC epoch-based Instant in the database.
     */
    @Column(nullable = false)
    private Instant timestamp;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** JPA requires a no-arg constructor. */
    protected StockQuote() {}

    /**
     * Full constructor used by the service layer and data initializer.
     *
     * @param symbol        stock ticker symbol
     * @param companyName   human-readable company name
     * @param price         current price per share
     * @param change        price change from previous close
     * @param changePercent percentage change from previous close
     * @param volume        share trading volume
     * @param timestamp     time at which this quote was recorded
     */
    public StockQuote(String symbol, String companyName, Double price,
                      Double change, Double changePercent, Long volume, Instant timestamp) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getChange() { return change; }
    public void setChange(Double change) { this.change = change; }

    public Double getChangePercent() { return changePercent; }
    public void setChangePercent(Double changePercent) { this.changePercent = changePercent; }

    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
