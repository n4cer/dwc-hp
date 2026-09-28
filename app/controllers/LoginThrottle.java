package controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import play.api.Configuration;

import java.time.Duration;
import java.util.Locale;

/**
 * Two independent brute-force brakes for the login and the password link form.
 *
 * Per client address, so one attacker cannot try many nicks. Per nick, so a botnet
 * spreading its guesses over many addresses cannot hammer a single account. The nick
 * key is used whether or not the nick exists, so a lockout tells nothing about which
 * nicks are real. Both are temporary on purpose: a permanent lock would let anyone
 * lock members out of their accounts.
 */
@Singleton
final class LoginThrottle {
    private final LoginRateLimiter byClient;
    private final LoginRateLimiter byNick;

    @Inject
    LoginThrottle(Configuration configuration) {
        this(LoginRateLimiter.fromConfig(configuration, "auth.clientRateLimit", 5, Duration.ofMinutes(15)),
                LoginRateLimiter.fromConfig(configuration, "auth.accountRateLimit", 10, Duration.ofMinutes(15)));
    }

    LoginThrottle(LoginRateLimiter byClient, LoginRateLimiter byNick) {
        this.byClient = byClient;
        this.byNick = byNick;
    }

    LoginRateLimiter.LimitStatus clientStatus(String client) {
        return byClient.status(client);
    }

    LoginRateLimiter.LimitStatus nickStatus(String nick) {
        return byNick.status(nickKey(nick));
    }

    /** Records a failed attempt; blocked if either the client or the nick is now over its limit. */
    LoginRateLimiter.LimitStatus recordFailure(String client, String nick) {
        LoginRateLimiter.LimitStatus clientLimit = byClient.recordFailure(client);
        if (nick == null) return clientLimit;
        LoginRateLimiter.LimitStatus nickLimit = byNick.recordFailure(nickKey(nick));
        if (!clientLimit.blocked()) return nickLimit;
        if (!nickLimit.blocked()) return clientLimit;
        return clientLimit.retryAfterSeconds() >= nickLimit.retryAfterSeconds() ? clientLimit : nickLimit;
    }

    void recordSuccess(String client, String nick) {
        byClient.reset(client);
        byNick.reset(nickKey(nick));
    }

    static String nickKey(String nick) {
        return nick == null ? "" : nick.trim().toLowerCase(Locale.ROOT);
    }
}
