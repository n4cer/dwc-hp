package controllers;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginThrottleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));

    private static LoginThrottle throttle(int perClient, int perNick) {
        return new LoginThrottle(new LoginRateLimiter(perClient, Duration.ofMinutes(15), CLOCK),
                new LoginRateLimiter(perNick, Duration.ofMinutes(15), CLOCK));
    }

    @Test
    public void blocksOneClientTryingManyNicks() {
        LoginThrottle throttle = throttle(3, 100);

        throttle.recordFailure("198.51.100.1", "a");
        throttle.recordFailure("198.51.100.1", "b");
        assertTrue(throttle.recordFailure("198.51.100.1", "c").blocked());
        assertTrue(throttle.clientStatus("198.51.100.1").blocked());
        assertFalse(throttle.clientStatus("198.51.100.2").blocked());
    }

    @Test
    public void blocksOneNickAttackedFromManyClients() {
        LoginThrottle throttle = throttle(100, 3);

        throttle.recordFailure("198.51.100.1", "Victim");
        throttle.recordFailure("198.51.100.2", "victim");
        assertTrue(throttle.recordFailure("198.51.100.3", " VICTIM ").blocked());
        assertTrue(throttle.nickStatus("victim").blocked());
        assertFalse(throttle.nickStatus("someone-else").blocked());
    }

    @Test
    public void successResetsBothCounters() {
        LoginThrottle throttle = throttle(2, 2);

        throttle.recordFailure("198.51.100.1", "member");
        throttle.recordSuccess("198.51.100.1", "Member");
        assertFalse(throttle.recordFailure("198.51.100.1", "member").blocked());
    }

    @Test
    public void tokenFailuresWithoutNickOnlyCountPerClient() {
        LoginThrottle throttle = throttle(2, 1);

        assertFalse(throttle.recordFailure("198.51.100.1", null).blocked());
        assertTrue(throttle.recordFailure("198.51.100.1", null).blocked());
        assertFalse(throttle.nickStatus("").blocked());
    }
}
