package controllers;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginRateLimiterTest {
    @Test
    public void blocksAfterConfiguredNumberOfFailures() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(10), clock);

        assertFalse(limiter.recordFailure("client").blocked());
        assertFalse(limiter.recordFailure("client").blocked());
        assertTrue(limiter.recordFailure("client").blocked());
        assertTrue(limiter.status("client").blocked());
    }

    @Test
    public void permitsLoginAgainAfterWindowExpires() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(10), clock);

        assertTrue(limiter.recordFailure("client").blocked());
        clock.advance(Duration.ofMinutes(10));
        assertFalse(limiter.status("client").blocked());
    }

    @Test
    public void successfulLoginCanResetFailures() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(10), clock);

        limiter.recordFailure("client");
        limiter.reset("client");
        assertFalse(limiter.recordFailure("client").blocked());
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
