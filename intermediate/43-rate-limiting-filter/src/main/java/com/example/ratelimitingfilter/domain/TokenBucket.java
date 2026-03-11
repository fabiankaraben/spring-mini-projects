package com.example.ratelimitingfilter.domain;

/**
 * Pure-domain model representing the state of a single token bucket.
 *
 * <p>The token-bucket algorithm works as follows:
 * <ol>
 *   <li>Each client starts with a bucket that holds {@code capacity} tokens.</li>
 *   <li>Every incoming request consumes one token.</li>
 *   <li>If the bucket is empty the request is rejected (HTTP 429).</li>
 *   <li>After {@code refillPeriodSeconds} the bucket is fully replenished.</li>
 * </ol>
 *
 * <p>This class is intentionally kept free of any Spring or Redis dependencies
 * so it can be tested with plain unit tests.
 */
public class TokenBucket {

    /** Maximum number of tokens this bucket can hold. */
    private final int capacity;

    /** Number of tokens currently available in the bucket. */
    private int tokens;

    /**
     * Unix epoch timestamp (seconds) at which the bucket was last refilled.
     * Used to determine whether a new refill window has started.
     */
    private long lastRefillTimestamp;

    /** Duration of the refill window in seconds. */
    private final long refillPeriodSeconds;

    /**
     * Creates a new, full token bucket.
     *
     * @param capacity            maximum tokens and initial token count
     * @param refillPeriodSeconds how many seconds before the bucket is fully refilled
     */
    public TokenBucket(int capacity, long refillPeriodSeconds) {
        this.capacity = capacity;
        this.tokens = capacity;                          // start full
        this.refillPeriodSeconds = refillPeriodSeconds;
        this.lastRefillTimestamp = currentEpochSeconds();
    }

    /**
     * Attempts to consume one token from the bucket.
     *
     * <p>Before consuming a token the method checks whether a new refill window
     * has elapsed since the last refill. If so the bucket is fully replenished.
     *
     * @return {@code true} if a token was consumed (request allowed);
     *         {@code false} if the bucket was empty (request rejected)
     */
    public boolean tryConsume() {
        refillIfWindowElapsed();

        if (tokens > 0) {
            tokens--;
            return true;    // request allowed
        }
        return false;       // bucket empty – rate limit exceeded
    }

    /**
     * Refills the bucket to full capacity if the refill window has elapsed.
     *
     * <p>This is a simple fixed-window approach: the bucket resets completely
     * at the start of each new window. For sub-second precision a sliding-window
     * or token-refill-per-second variant could be used instead.
     */
    private void refillIfWindowElapsed() {
        long now = currentEpochSeconds();
        long elapsed = now - lastRefillTimestamp;

        if (elapsed >= refillPeriodSeconds) {
            // A new window has started – replenish all tokens
            tokens = capacity;
            lastRefillTimestamp = now;
        }
    }

    /**
     * Returns the current Unix epoch time in seconds.
     * Extracted to a separate method so subclasses or tests can override it.
     *
     * @return current time in seconds since the Unix epoch
     */
    protected long currentEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    /** Returns the maximum token capacity of this bucket. */
    public int getCapacity() {
        return capacity;
    }

    /** Returns the number of tokens currently available. */
    public int getTokens() {
        return tokens;
    }

    /** Returns the Unix timestamp (seconds) of the last refill. */
    public long getLastRefillTimestamp() {
        return lastRefillTimestamp;
    }

    /** Returns the refill period in seconds. */
    public long getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    // ── Package-private setters used by the Redis-backed service ───────────────

    /**
     * Restores the token count. Used when deserialising the bucket state from Redis.
     *
     * @param tokens token count to restore
     */
    void setTokens(int tokens) {
        this.tokens = tokens;
    }

    /**
     * Restores the last-refill timestamp. Used when deserialising the bucket state
     * from Redis.
     *
     * @param lastRefillTimestamp Unix epoch seconds
     */
    void setLastRefillTimestamp(long lastRefillTimestamp) {
        this.lastRefillTimestamp = lastRefillTimestamp;
    }
}
