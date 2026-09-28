package controllers;

import io.ebean.DB;
import models.PasswordToken;
import models.Role;
import models.User;
import models.UserRole;
import org.junit.After;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.api.test.CSRFTokenHelper;
import play.test.WithApplication;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.mvc.Http.Status.TOO_MANY_REQUESTS;
import static play.mvc.Http.Status.UNAUTHORIZED;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.route;

/** Needs PostgreSQL, like every WithApplication test (runs in CI). */
public class AuthFlowTest extends WithApplication {
    private static final String PREFIX = "authtest-";
    private static final String PASSWORD = "a long enough password";

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder().build();
    }

    @After
    public void removeTestMembers() {
        DB.sqlUpdate("delete from lineup where nick like :prefix").setParameter("prefix", PREFIX + "%").execute();
    }

    @Test
    public void activeMemberWithPasswordCanSignIn() {
        User member = member("login", true, PASSWORD);

        Result result = login(member.getNick().toUpperCase(), PASSWORD, "192.0.2.1");

        assertEquals(SEE_OTHER, result.status());
        assertEquals("/admin", result.redirectLocation().orElse(""));
        assertEquals(String.valueOf(member.getId()), result.session().get(AdminAuth.SESSION_MEMBER).orElse(""));
        assertTrue(User.find.byId(member.getId()).getLastLoginAt() != null);
    }

    @Test
    public void inactiveMembersAndMembersWithoutPasswordCannotSignIn() {
        member("inactive", false, PASSWORD);
        member("nopassword", true, null);

        assertEquals(UNAUTHORIZED, login(PREFIX + "inactive", PASSWORD, "192.0.2.2").status());
        assertEquals(UNAUTHORIZED, login(PREFIX + "nopassword", "", "192.0.2.2").status());
        assertEquals(UNAUTHORIZED, login(PREFIX + "unknown", PASSWORD, "192.0.2.2").status());
    }

    @Test
    public void repeatedFailuresAreRateLimited() {
        member("bruteforce", true, PASSWORD);
        Result result = null;
        for (int i = 0; i < 5; i++) result = login(PREFIX + "bruteforce", "wrong password " + i, "192.0.2.3");

        assertEquals(TOO_MANY_REQUESTS, result.status());
        assertTrue(result.header("Retry-After").isPresent());
        // Even the right password is refused while the block lasts.
        assertEquals(TOO_MANY_REQUESTS, login(PREFIX + "bruteforce", PASSWORD, "192.0.2.3").status());
    }

    @Test
    public void membersOnlyReachWhatTheirRolesAllow() {
        User member = member("plain", true, PASSWORD);

        assertEquals(OK, route(app, signedIn(GET, "/admin", member)).status());
        assertEquals(OK, route(app, signedIn(GET, "/admin/profile", member)).status());
        assertEquals(FORBIDDEN, route(app, signedIn(GET, "/admin/news", member)).status());
        assertEquals(FORBIDDEN, route(app, signedIn(GET, "/admin/quake3", member)).status());

        grant(member, Role.NEWS);
        assertEquals(OK, route(app, signedIn(GET, "/admin/news", member)).status());
        assertEquals(FORBIDDEN, route(app, signedIn(GET, "/admin/clanwars", member)).status());
    }

    @Test
    public void visitorsAreSentToTheLogin() {
        Result result = route(app, new Http.RequestBuilder().method(GET).uri("/admin/news"));

        assertEquals(SEE_OTHER, result.status());
        assertEquals("/admin/login", result.redirectLocation().orElse(""));
    }

    @Test
    public void passwordChangeOrDeactivationEndsExistingSessions() {
        User member = member("session", true, PASSWORD);
        Http.RequestBuilder before = signedIn(GET, "/admin", member);

        member.setPasswordChangedAt(Date.from(Instant.now().plusSeconds(5)));
        member.update();
        assertEquals(SEE_OTHER, route(app, before).status());

        Http.RequestBuilder current = signedIn(GET, "/admin", member);
        member.setActive(false);
        member.update();
        assertEquals(SEE_OTHER, route(app, current).status());
    }

    @Test
    public void passwordLinkWorksExactlyOnce() {
        User member = member("token", true, null);
        String token = token(member, Instant.now().plus(1, ChronoUnit.HOURS));

        assertEquals(OK, route(app, new Http.RequestBuilder().method(GET).uri("/set-password/" + token)).status());
        Result redeemed = route(app, redeem(token, "a brand new password"));
        assertEquals(SEE_OTHER, redeemed.status());
        assertEquals(String.valueOf(member.getId()), redeemed.session().get(AdminAuth.SESSION_MEMBER).orElse(""));
        assertTrue(Passwords.verify("a brand new password", User.find.byId(member.getId()).getPasswordHash()));

        assertEquals(NOT_FOUND, route(app, redeem(token, "another new password")).status());
    }

    @Test
    public void expiredPasswordLinkIsRejected() {
        User member = member("expired", true, null);
        String token = token(member, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertEquals(NOT_FOUND, route(app, redeem(token, "a brand new password")).status());
        assertFalse(User.find.byId(member.getId()).hasPassword());
    }

    @Test
    public void lastAdministratorCannotDemoteThemselves() {
        User admin = member("admin", true, PASSWORD);
        grant(admin, Role.ADMIN);

        Http.RequestBuilder request = signedIn(POST, "/admin/lineup/" + admin.getId(), admin)
                .bodyForm(Map.of("nick", admin.getNick(), "active", "on"));
        Result result = route(app, CSRFTokenHelper.addCSRFToken(request));

        assertEquals(BAD_REQUEST, result.status());
        assertTrue(User.find.byId(admin.getId()).hasRole(Role.ADMIN));
    }

    @Test
    public void lineupTeamCannotEditAdministrators() {
        User admin = member("admin2", true, PASSWORD);
        grant(admin, Role.ADMIN);
        User lineup = member("lineup", true, PASSWORD);
        grant(lineup, Role.LINEUP);

        assertEquals(FORBIDDEN, route(app, signedIn(GET, "/admin/lineup/" + admin.getId() + "/edit", lineup)).status());
        assertEquals(OK, route(app, signedIn(GET, "/admin/lineup/" + lineup.getId() + "/edit", lineup)).status());
    }

    private Result login(String nick, String password, String client) {
        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).uri("/admin/login")
                .remoteAddress(client).bodyForm(Map.of("nick", nick, "password", password));
        return route(app, CSRFTokenHelper.addCSRFToken(request));
    }

    private static Http.RequestBuilder redeem(String token, String password) {
        return CSRFTokenHelper.addCSRFToken(new Http.RequestBuilder().method(POST).uri("/set-password/" + token)
                .bodyForm(Map.of("password", password, "passwordConfirmation", password)));
    }

    private static Http.RequestBuilder signedIn(String method, String uri, User member) {
        return new Http.RequestBuilder().method(method).uri(uri)
                .session(AdminAuth.SESSION_MEMBER, String.valueOf(member.getId()))
                .session(AdminAuth.SESSION_VERSION, AdminAuth.sessionVersion(member));
    }

    private static User member(String name, boolean active, String password) {
        User member = new User();
        member.setNick(PREFIX + name);
        member.setActive(active);
        if (password != null) {
            member.setPasswordHash(Passwords.hash(password));
            member.setPasswordChangedAt(new Date());
        }
        member.save();
        return User.find.byId(member.getId());
    }

    private static void grant(User member, Role role) {
        UserRole entry = new UserRole();
        entry.setMember(member);
        entry.setRole(role);
        entry.save();
        member.refresh();
    }

    private static String token(User member, Instant expiresAt) {
        String token = Passwords.newToken();
        PasswordToken entry = new PasswordToken();
        entry.setMember(member);
        entry.setTokenHash(Passwords.tokenHash(token));
        entry.setExpiresAt(Date.from(expiresAt));
        entry.save();
        return token;
    }
}
