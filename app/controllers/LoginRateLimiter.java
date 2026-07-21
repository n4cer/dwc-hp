package controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import play.api.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
final class LoginRateLimiter {
    private static final String MAX_ATTEMPTS_CONFIG = "admin.loginRateLimit.maxAttempts";
    private static final String WINDOW_CONFIG = "admin.loginRateLimit.window";

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    @Inject
    LoginRateLimiter(Configuration configuration) {
        this(configuration.underlying().hasPath(MAX_ATTEMPTS_CONFIG)
                        ? configuration.underlying().getInt(MAX_ATTEMPTS_CONFIG) : 5,
                configuration.underlying().hasPath(WINDOW_CONFIG)
                        ? configuration.underlying().getDuration(WINDOW_CONFIG) : Duration.ofMinutes(15),
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
