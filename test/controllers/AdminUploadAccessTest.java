package controllers;

import io.ebean.DB;
import models.Role;
import models.User;
import models.UserRole;
import org.junit.After;
import org.junit.Test;
import play.Application;
import play.api.test.CSRFTokenHelper;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.WithApplication;

import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

/** Needs PostgreSQL, like every WithApplication test (runs in CI). The sidecar is never reached here. */
public class AdminUploadAccessTest extends WithApplication {
    private static final String PREFIX = "uploadtest-";

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder().configure("quake3.control.token", "").build();
    }

    @After
    public void removeTestData() {
        DB.sqlUpdate("delete from lineup where nick like :prefix").setParameter("prefix", PREFIX + "%").execute();
    }

    @Test
    public void needsTheQuake3OrTheMediaRole() {
        User news = member("news", Role.NEWS);
        assertEquals(FORBIDDEN, route(app, signedIn(GET, "/admin/uploads", news)).status());
        assertEquals(FORBIDDEN, post("/admin/uploads/Quake3.zip/discard", news, Map.of()).status());

        Result page = route(app, CSRFTokenHelper.addCSRFToken(signedIn(GET, "/admin/uploads", member("media", Role.MEDIA))));
        assertEquals(OK, page.status());
        assertTrue(contentAsString(page).contains("not available"));
    }

    @Test
    public void eachTargetNeedsItsOwnRole() {
        User media = member("media", Role.MEDIA);
        assertEquals(FORBIDDEN, post("/admin/uploads/zzz_dwc_bots.pk3/install", media, Map.of("target", "baseq3")).status());
        User quake3 = member("quake3", Role.QUAKE3);
        assertEquals(FORBIDDEN, post("/admin/uploads/zzz_dwc_bots.pk3/install", quake3, Map.of("target", "downloads")).status());
    }

    @Test
    public void refusesTargetsThatDoNotFitTheFile() {
        User admin = member("admin", Role.ADMIN);
        Result result = post("/admin/uploads/Quake3.zip/install", admin, Map.of("target", "baseq3"));
        assertEquals(SEE_OTHER, result.status());
        assertEquals("Quake3.zip cannot go there.", result.flash().get("error").orElse(""));
        assertEquals("pak0.pk3 cannot go there.", post("/admin/uploads/pak0.pk3/install", admin,
                Map.of("target", "baseq3")).flash().get("error").orElse(""));
    }

    @Test
    public void prefillsTheDownloadFormWithTheInstalledFile() {
        Result form = route(app, CSRFTokenHelper.addCSRFToken(
                signedIn(GET, "/admin/downloads/new?file=Quake3.zip", member("media", Role.MEDIA))));
        assertEquals(OK, form.status());
        assertTrue(contentAsString(form).contains("value=\"Quake3.zip\""));
        assertTrue(contentAsString(form).contains("Add download"));
    }

    private Result post(String uri, User member, Map<String, String> form) {
        return route(app, CSRFTokenHelper.addCSRFToken(signedIn(POST, uri, member).bodyForm(form)));
    }

    private static Http.RequestBuilder signedIn(String method, String uri, User member) {
        return new Http.RequestBuilder().method(method).uri(uri)
                .session(AdminAuth.SESSION_MEMBER, String.valueOf(member.getId()))
                .session(AdminAuth.SESSION_VERSION, AdminAuth.sessionVersion(member));
    }

    private static User member(String name, Role role) {
        User member = new User();
        member.setNick(PREFIX + name);
        member.setActive(true);
        // Without a password nobody can be signed in.
        member.setPasswordHash(Passwords.hash("a long enough password"));
        member.setPasswordChangedAt(new Date());
        member.save();
        UserRole entry = new UserRole();
        entry.setMember(member);
        entry.setRole(role);
        entry.save();
        return User.find.byId(member.getId());
    }
}
