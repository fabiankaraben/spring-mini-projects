package com.example.ratelimitingfilter.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TokenBucket}.
 *
 * <p>These tests exercise the pure domain logic of the token-bucket algorithm
 * without any Spring context, Redis, or other infrastructure. They run fast and
 * in total isolation.
 *
 * <p>To control time-dependent behaviour we use a subclass of {@link TokenBucket}
 * that overrides {@link TokenBucket#currentEpochSeconds()} to return a fixed
 * timestamp. This pattern avoids Thread.sleep() and makes the tests deterministic.
 */
@DisplayName("TokenBucket domain logic")
class TokenBucketTest {

    // ── Helper: a TokenBucket whose clock can be controlled in tests ───────────

    /**
     * Testable subclass that exposes a mutable clock so tests can simulate the
     * passage of time without real sleeping.
     */
    private static class ControlledClockBucket extends TokenBucket {

        /** The value returned by {@link #currentEpochSeconds()}. */
        private long fakeNow;

        ControlledClockBucket(int capacity, long refillPeriodSeconds, long initialNow) {
            super(capacity, refillPeriodSeconds);
            this.fakeNow = initialNow;
            // Align the lastRefillTimestamp with the initial fake time
            setLastRefillTimestamp(initialNow);
        }

        /** Advances the fake clock by {@code seconds}. */
        void advanceSeconds(long seconds) {
            fakeNow += seconds;
        }

        @Override
        protected long currentEpochSeconds() {
            return fakeNow;
        }
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bucket starts full – initial token count equals capacity")
    void bucketStartsFull() {
        TokenBucket bucket = new TokenBucket(5, 60);
        assertThat(bucket.getTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("tryConsume returns true when tokens are available")
    void consumeSucceedsWhileTokensAvailable() {
        TokenBucket bucket = new TokenBucket(3, 60);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    @DisplayName("Each successful consume decrements the token count")
    void consumeDecrementsTokenCount() {
        TokenBucket bucket = new TokenBucket(3, 60);

        bucket.tryConsume();
        assertThat(bucket.getTokens()).isEqualTo(2);

        bucket.tryConsume();
        assertThat(bucket.getTokens()).isEqualTo(1);
    }

    @Test
    @DisplayName("tryConsume returns false when bucket is empty")
    void consumeFailsWhenBucketEmpty() {
        TokenBucket bucket = new TokenBucket(2, 60);

        bucket.tryConsume(); // token 1
        bucket.tryConsume(); // token 2
        // bucket is now empty
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("Token count does not drop below zero")
    void tokenCountNeverGoesBelowZero() {
        TokenBucket bucket = new TokenBucket(1, 60);

        bucket.tryConsume(); // empties the bucket
        bucket.tryConsume(); // should not go negative
        assertThat(bucket.getTokens()).isEqualTo(0);
    }

    @Test
    @DisplayName("Bucket refills to full capacity after the refill window elapses")
    void bucketRefillsAfterWindow() {
        long initialTime = 1_000_000L;
        ControlledClockBucket bucket = new ControlledClockBucket(5, 60, initialTime);

        // Exhaust all tokens
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume()).isTrue();
        }
        assertThat(bucket.tryConsume()).isFalse();   // bucket is empty

        // Advance time past the refill window (60 seconds)
        bucket.advanceSeconds(61);

        // Bucket should be refilled – first consume succeeds again
        assertThat(bucket.tryConsume()).isTrue();
        // After refill and one consume, 4 tokens remain
        assertThat(bucket.getTokens()).isEqualTo(4);
    }

    @Test
    @DisplayName("Bucket does NOT refill before the window has fully elapsed")
    void bucketDoesNotRefillBeforeWindow() {
        long initialTime = 1_000_000L;
        ControlledClockBucket bucket = new ControlledClockBucket(5, 60, initialTime);

        // Exhaust all tokens
        for (int i = 0; i < 5; i++) {
            bucket.tryConsume();
        }

        // Advance only 59 seconds (window is 60 s)
        bucket.advanceSeconds(59);

        // Bucket should still be empty
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("Bucket with capacity 1 allows exactly one request per window")
    void capacityOneAllowsOneRequestPerWindow() {
        long initialTime = 1_000_000L;
        ControlledClockBucket bucket = new ControlledClockBucket(1, 30, initialTime);

        assertThat(bucket.tryConsume()).isTrue();    // first request: allowed
        assertThat(bucket.tryConsume()).isFalse();   // second request: blocked

        // After 30 seconds the window resets
        bucket.advanceSeconds(31);
        assertThat(bucket.tryConsume()).isTrue();    // allowed again
    }

    @Test
    @DisplayName("getCapacity returns the value provided at construction")
    void getCapacityReturnsConstructorValue() {
        TokenBucket bucket = new TokenBucket(7, 120);
        assertThat(bucket.getCapacity()).isEqualTo(7);
    }

    @Test
    @DisplayName("getRefillPeriodSeconds returns the value provided at construction")
    void getRefillPeriodSecondsReturnsConstructorValue() {
        TokenBucket bucket = new TokenBucket(5, 90);
        assertThat(bucket.getRefillPeriodSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("Multiple refill windows are each applied independently")
    void multipleWindowsEachRefillFully() {
        long initialTime = 1_000_000L;
        ControlledClockBucket bucket = new ControlledClockBucket(3, 60, initialTime);

        // First window: exhaust
        bucket.tryConsume(); bucket.tryConsume(); bucket.tryConsume();
        assertThat(bucket.tryConsume()).isFalse();

        // Second window
        bucket.advanceSeconds(61);
        bucket.tryConsume(); bucket.tryConsume(); bucket.tryConsume();
        assertThat(bucket.tryConsume()).isFalse();

        // Third window: bucket refills again
        bucket.advanceSeconds(61);
        assertThat(bucket.tryConsume()).isTrue();
    }
}
