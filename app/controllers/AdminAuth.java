package controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import models.Role;
import models.User;
import play.i18n.Messages;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

/**
 * Member sessions. The Play session cookie is signed, so it only has to carry the
 * member id plus a password version: changing the password (or deactivating the
 * member) makes every existing session of that member invalid on its next request.
 */
public final class AdminAuth {
    static final String SESSION_MEMBER = "dwc-uid";
    static final String SESSION_VERSION = "dwc-pv";

    private AdminAuth() { }

    public static Optional<User> currentUser(Http.Request request) {
        Optional<String> id = request.session().get(SESSION_MEMBER);
        Optional<String> version = request.session().get(SESSION_VERSION);
        if (id.isEmpty() || version.isEmpty()) return Optional.empty();
        User user;
        try {
            user = User.find.byId(Long.valueOf(id.get()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (user == null || !user.canLogin() || !secureEquals(version.get(), sessionVersion(user))) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static boolean hasRole(Http.Request request, Role role) {
        return currentUser(request).map(user -> user.hasRole(role)).orElse(false);
    }

    /**
     * Null if the signed-in member may proceed, otherwise the response to send instead:
     * the login page for visitors, 403 for members lacking the role. A null role only
     * requires being signed in.
     */
    static Result require(Http.Request request, Role role, Messages messages) {
        Optional<User> user = currentUser(request);
        if (user.isEmpty()) return Results.redirect(routes.AdminController.login());
        if (role == null || user.get().hasRole(role)) return null;
        return forbidden(request, user.get(), role, messages);
    }

    static Result forbidden(Http.Request request, User user, Role role, Messages messages) {
        AuthLog.event(AuthLog.Event.ACCESS_DENIED).member(user).field("role", role.name()).request(request).log();
        return Results.forbidden(views.html.adminForbidden.render(user, role, messages));
    }

    /** Replaces the whole session, so a session id planted before login is worthless. */
    static Result signIn(Result result, User user) {
        return result.withSession(Map.of(SESSION_MEMBER, String.valueOf(user.getId()),
                SESSION_VERSION, sessionVersion(user)));
    }

    static String sessionVersion(User user) {
        return user.getPasswordChangedAt() == null ? "0" : Long.toString(user.getPasswordChangedAt().getTime());
    }

    private static boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
