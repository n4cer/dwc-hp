package controllers;

import models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Security audit trail. Every event becomes exactly one logfmt line starting with
 * "dwc-auth ", written without level or logger prefix (see logback.xml) so the
 * monitoring stack can parse it straight out of journald.
 *
 * Passwords and tokens are never passed in here.
 */
final class AuthLog {
    static final String PREFIX = "dwc-auth";
    private static final Logger LOG = LoggerFactory.getLogger("auth");
    private static final int MAX_NICK = 64;
    private static final int MAX_USER_AGENT = 200;
    private static final int MAX_VALUE = 200;
    private static final String TOKEN_PATH = "/set-password/";

    enum Event {
        LOGIN_SUCCESS, LOGIN_FAILED, LOGIN_RATE_LIMITED, LOGOUT,
        PASSWORD_CHANGED, PASSWORD_CHANGE_FAILED, PASSWORD_LINK_CREATED, PASSWORD_LINK_REDEEMED, PASSWORD_LINK_REJECTED,
        ROLES_CHANGED, ACCESS_DENIED, BOOTSTRAP_ADMIN;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private AuthLog() { }

    static Entry event(Event event) {
        return new Entry(event);
    }

    static final class Entry {
        private final Map<String, String> fields = new LinkedHashMap<>();

        private Entry(Event event) {
            fields.put("event", event.key());
        }

        Entry reason(String reason) {
            return field("reason", reason);
        }

        Entry nick(String nick) {
            return field("nick", truncate(nick, MAX_NICK));
        }

        Entry member(User member) {
            if (member == null) return this;
            field("member_id", String.valueOf(member.getId()));
            return nick(member.getNick());
        }

        Entry actor(User actor) {
            return actor == null ? this : field("actor_id", String.valueOf(actor.getId()));
        }

        Entry request(Http.Request request) {
            field("src_ip", request.remoteAddress());
            field("path", redactPath(request.path()));
            return field("ua", truncate(request.header("User-Agent").orElse(""), MAX_USER_AGENT));
        }

        Entry field(String key, String value) {
            if (value != null) fields.put(key, truncate(value, MAX_VALUE));
            return this;
        }

        String format() {
            StringBuilder line = new StringBuilder(PREFIX);
            for (Map.Entry<String, String> field : fields.entrySet()) {
                line.append(' ').append(field.getKey()).append('=').append(quote(field.getValue()));
            }
            return line.toString();
        }

        void log() {
            LOG.info(format());
        }
    }

    static String quote(String value) {
        boolean plain = !value.isEmpty();
        for (int i = 0; i < value.length() && plain; i++) {
            char c = value.charAt(i);
            plain = c > ' ' && c != '"' && c != '=' && c != '\\' && c < 127;
        }
        if (plain) return value;
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < ' ' || c == 127) quoted.append(' '); else quoted.append(c);
                }
            }
        }
        return quoted.append('"').toString();
    }

    /** The password link carries its token in the path; it must not end up in the log store. */
    static String redactPath(String path) {
        return path != null && path.startsWith(TOKEN_PATH) ? TOKEN_PATH + "*" : path;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
