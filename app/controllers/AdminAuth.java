package controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import play.api.Configuration;
import play.mvc.Http;

public final class AdminAuth {
    public static final String ADMIN_SESSION = "dwc-admin";

    private AdminAuth() { }

    public static boolean isAuthenticated(Http.Request request, Configuration configuration) {
        return credentialsConfigured(configuration) && request.session().get(ADMIN_SESSION)
                .filter(value -> secureEquals(value, configured(configuration, "admin.username"))).isPresent();
    }

    public static boolean credentialsConfigured(Configuration configuration) {
        return !configured(configuration, "admin.username").isBlank() && !configured(configuration, "admin.password").isBlank();
    }

    private static String configured(Configuration configuration, String path) {
        return configuration.underlying().hasPath(path) ? configuration.underlying().getString(path) : "";
    }

    private static boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
