package controllers;

import play.api.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failures per key within a fixed window. Keys are whatever the caller throttles
 * on - a client address or a nick - see {@link LoginThrottle}.
 */
final class LoginRateLimiter {
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    /** Reads {@code <prefix>.maxAttempts} and {@code <prefix>.window}, falling back to the given defaults. */
    static LoginRateLimiter fromConfig(Configuration configuration, String prefix, int defaultMaxAttempts,
            Duration defaultWindow) {
        String maxAttempts = prefix + ".maxAttempts";
        String window = prefix + ".window";
        return new LoginRateLimiter(configuration.underlying().hasPath(maxAttempts)
                        ? configuration.underlying().getInt(maxAttempts) : defaultMaxAttempts,
                configuration.underlying().hasPath(window)
                        ? configuration.underlying().getDuration(window) : defaultWindow,
                Clock.systemUTC());
    }

    LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (window.isZero() || window.isNegative()) throw new IllegalArgumentException("window must be positive");
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    LimitStatus status(String client) {
        Instant now = clock.instant();
        AttemptWindow current = attempts.get(client);
        if (current == null) return LimitStatus.allowed();
        if (!now.isBefore(current.expiresAt)) {
            attempts.remove(client, current);
            return LimitStatus.allowed();
        }
        return current.failures >= maxAttempts
                ? LimitStatus.blocked(Duration.between(now, current.expiresAt).toSeconds())
                : LimitStatus.allowed();
    }

    LimitStatus recordFailure(String client) {
        Instant now = clock.instant();
        AttemptWindow current = attempts.compute(client, (key, previous) -> {
            if (previous == null || !now.isBefore(previous.expiresAt)) {
                return new AttemptWindow(1, now.plus(window));
            }
            return new AttemptWindow(previous.failures + 1, previous.expiresAt);
        });
        return current.failures >= maxAttempts
                ? LimitStatus.blocked(Duration.between(now, current.expiresAt).toSeconds())
                : LimitStatus.allowed();
    }

    void reset(String client) {
        attempts.remove(client);
    }

    record LimitStatus(boolean blocked, long retryAfterSeconds) {
        static LimitStatus allowed() {
            return new LimitStatus(false, 0);
        }

        static LimitStatus blocked(long retryAfterSeconds) {
            return new LimitStatus(true, Math.max(1, retryAfterSeconds));
        }
    }

    private record AttemptWindow(int failures, Instant expiresAt) { }
}
