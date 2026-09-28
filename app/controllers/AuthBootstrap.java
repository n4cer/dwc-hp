package controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.Role;
import models.User;
import models.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.Configuration;
import play.api.db.evolutions.ApplicationEvolutions;

import java.util.Date;

/**
 * Hands out the first administrator. As long as no active member holds ADMIN, the
 * member named by DWC_ADMIN_USERNAME gets it on startup, and DWC_ADMIN_PASSWORD
 * becomes their password if they do not have one yet. Once an administrator exists
 * this does nothing, and both variables can be removed.
 *
 * Depends on ApplicationEvolutions only so that Guice runs it after the evolutions.
 */
@Singleton
final class AuthBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(AuthBootstrap.class);

    @Inject
    AuthBootstrap(Configuration configuration, ApplicationEvolutions evolutions) {
        String nick = configured(configuration, "auth.bootstrap.nick");
        if (nick.isBlank()) return;
        try {
            run(nick, configured(configuration, "auth.bootstrap.password"));
        } catch (RuntimeException ex) {
            // The public site must still come up; the admin area simply stays closed.
            LOG.error("Could not bootstrap the administrator", ex);
        }
    }

    private static void run(String nick, String password) {
        boolean adminExists = UserRole.find.query().where()
                .eq("role", Role.ADMIN).eq("member.active", true).exists();
        if (adminExists) return;

        User member = User.findByNick(nick);
        if (member == null || !Boolean.TRUE.equals(member.getActive())) {
            LOG.warn("Bootstrap administrator '{}' is not an active lineup member; nobody can administrate", nick);
            return;
        }
        if (!member.hasRole(Role.ADMIN)) {
            UserRole role = new UserRole();
            role.setMember(member);
            role.setRole(Role.ADMIN);
            role.save();
        }
        boolean passwordSet = false;
        if (!member.hasPassword()) {
            if (password.isBlank()) {
                LOG.warn("Bootstrap administrator '{}' has no password and DWC_ADMIN_PASSWORD is not set", nick);
            } else {
                member.setPasswordHash(Passwords.hash(password));
                member.setPasswordChangedAt(new Date());
                member.update();
                passwordSet = true;
            }
        }
        AuthLog.event(AuthLog.Event.BOOTSTRAP_ADMIN).member(member)
                .field("password_set", Boolean.toString(passwordSet)).log();
    }

    private static String configured(Configuration configuration, String path) {
        return configuration.underlying().hasPath(path) ? configuration.underlying().getString(path).trim() : "";
    }
}
