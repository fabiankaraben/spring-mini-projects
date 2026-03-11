package com.example.ratelimitingfilter.service;

import com.example.ratelimitingfilter.config.RateLimitProperties;
import com.example.ratelimitingfilter.domain.TokenBucket;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service that enforces the token-bucket rate limit for a given client key.
 *
 * <h2>How the Redis state works</h2>
 * <p>For each client identifier (e.g. an IP address) we store two fields in Redis:
 * <ul>
 *   <li>{@code <prefix><id>:tokens}  – remaining token count (String)</li>
 *   <li>{@code <prefix><id>:ts}      – Unix epoch timestamp of the last refill (String)</li>
 * </ul>
 *
 * <p>All reads and writes to Redis are performed atomically using
 * {@link StringRedisTemplate} operations. Because a single decrement + conditional
 * reset cannot be expressed as a single Redis command without Lua scripting, we use
 * an optimistic approach protected by the expiry TTL of the keys:
 * <ol>
 *   <li>Read both fields.</li>
 *   <li>If missing, create them (bucket is full).</li>
 *   <li>If the refill window has elapsed, reset tokens to capacity.</li>
 *   <li>If tokens > 0, decrement and allow the request; otherwise reject it.</li>
 * </ol>
 *
 * <p>For a production system you would replace steps 1–4 with an atomic Lua script
 * executed via {@link StringRedisTemplate#execute(org.springframework.data.redis.core.script.RedisScript, java.util.List, Object...)}
 * to avoid the tiny race window. The simpler approach is used here to keep the
 * educational code readable.
 */
@Service
public class RateLimiterService {

    /** Redis key suffix for the token counter. */
    private static final String TOKENS_SUFFIX = ":tokens";

    /** Redis key suffix for the last-refill timestamp. */
    private static final String TIMESTAMP_SUFFIX = ":ts";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties props;

    public RateLimiterService(StringRedisTemplate redisTemplate, RateLimitProperties props) {
        this.redisTemplate = redisTemplate;
        this.props = props;
    }

    /**
     * Attempts to consume one token from the bucket associated with
     * {@code clientKey}.
     *
     * @param clientKey a string that uniquely identifies the caller
     *                  (e.g. an IP address or API key)
     * @return {@code true} if the request is allowed, {@code false} if it is
     *         rate-limited
     */
    public boolean tryConsume(String clientKey) {
        String tokensKey    = props.getKeyPrefix() + clientKey + TOKENS_SUFFIX;
        String timestampKey = props.getKeyPrefix() + clientKey + TIMESTAMP_SUFFIX;

        long now = currentEpochSeconds();

        // ── Step 1: read current state ─────────────────────────────────────────
        String rawTokens = redisTemplate.opsForValue().get(tokensKey);
        String rawTs     = redisTemplate.opsForValue().get(timestampKey);

        int  tokens;
        long lastRefill;

        if (rawTokens == null || rawTs == null) {
            // ── First request from this client: initialise a full bucket ───────
            tokens     = props.getCapacity();
            lastRefill = now;
            persist(tokensKey, timestampKey, tokens, lastRefill);
        } else {
            tokens     = Integer.parseInt(rawTokens);
            lastRefill = Long.parseLong(rawTs);
        }

        // ── Step 2: refill if the window has elapsed ───────────────────────────
        long elapsed = now - lastRefill;
        if (elapsed >= props.getRefillPeriodSeconds()) {
            tokens     = props.getCapacity();   // reset to full capacity
            lastRefill = now;
        }

        // ── Step 3: try to consume a token ────────────────────────────────────
        if (tokens > 0) {
            tokens--;
            persist(tokensKey, timestampKey, tokens, lastRefill);
            return true;    // request allowed
        }

        // Bucket is empty – persist the (unchanged) state with a fresh TTL and reject
        persist(tokensKey, timestampKey, tokens, lastRefill);
        return false;       // rate limit exceeded
    }

    /**
     * Returns the number of tokens remaining for a client without consuming one.
     * Useful for populating the {@code X-RateLimit-Remaining} response header.
     *
     * @param clientKey unique client identifier
     * @return remaining tokens, or {@link RateLimitProperties#getCapacity()} if
     *         no bucket exists yet
     */
    public int getRemainingTokens(String clientKey) {
        String tokensKey    = props.getKeyPrefix() + clientKey + TOKENS_SUFFIX;
        String timestampKey = props.getKeyPrefix() + clientKey + TIMESTAMP_SUFFIX;

        String rawTokens = redisTemplate.opsForValue().get(tokensKey);
        String rawTs     = redisTemplate.opsForValue().get(timestampKey);

        if (rawTokens == null || rawTs == null) {
            return props.getCapacity();
        }

        int  tokens     = Integer.parseInt(rawTokens);
        long lastRefill = Long.parseLong(rawTs);
        long now        = currentEpochSeconds();

        if ((now - lastRefill) >= props.getRefillPeriodSeconds()) {
            // Window has elapsed – the next request will reset the bucket
            return props.getCapacity();
        }

        return tokens;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Writes the bucket state back to Redis.
     *
     * <p>The TTL of each key is set to {@code refillPeriodSeconds * 2} so that
     * keys for inactive clients are automatically cleaned up.
     */
    private void persist(String tokensKey, String timestampKey, int tokens, long timestamp) {
        long ttl = props.getRefillPeriodSeconds() * 2;
        redisTemplate.opsForValue().set(tokensKey,    String.valueOf(tokens),    ttl, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(timestampKey, String.valueOf(timestamp), ttl, TimeUnit.SECONDS);
    }

    /**
     * Returns the current Unix epoch time in seconds.
     * Extracted for testability.
     *
     * @return seconds since the Unix epoch
     */
    protected long currentEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Deletes all Redis keys associated with the given client key.
     * Primarily used in tests to reset state between test cases.
     *
     * @param clientKey unique client identifier
     */
    public void resetBucket(String clientKey) {
        redisTemplate.delete(props.getKeyPrefix() + clientKey + TOKENS_SUFFIX);
        redisTemplate.delete(props.getKeyPrefix() + clientKey + TIMESTAMP_SUFFIX);
    }
}
