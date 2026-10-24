package controllers;

import io.ebean.DB;
import models.Quake3Player;
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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.route;

/** Needs PostgreSQL, like every WithApplication test (runs in CI). */
public class Quake3PlayersAdminTest extends WithApplication {
    private static final String PREFIX = "q3test-";

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder().build();
    }

    @After
    public void removeTestData() {
        DB.sqlUpdate("delete from quake3_players where name like :prefix or member_id in "
                        + "(select id from lineup where nick like :prefix) or id in "
                        + "(select player_id from quake3_player_nicks where nick like :prefix)")
                .setParameter("prefix", PREFIX + "%").execute();
        DB.sqlUpdate("delete from lineup where nick like :prefix").setParameter("prefix", PREFIX + "%").execute();
    }

    @Test
    public void mergesNicksForMembersAndOthers() {
        User admin = member("admin", Role.QUAKE3);
        User lama = member("lama", null);

        Result created = save("/admin/quake3-players", admin,
                Map.of("member", String.valueOf(lama.getId()), "name", "", "nicks", PREFIX + "lamagra\n^1" + PREFIX + "LAMA\n\n"));
        assertEquals(SEE_OTHER, created.status());
        Quake3Player player = Quake3Player.find.query().where().eq("member.id", lama.getId()).findOne();
        assertEquals(List.of(PREFIX + "LAMA", PREFIX + "lamagra"), player.nickNames());
        assertEquals(lama.getNick(), player.displayName());

        assertEquals(SEE_OTHER, save("/admin/quake3-players", admin,
                Map.of("member", "", "name", PREFIX + "guest", "nicks", PREFIX + "guest2")).status());
        Quake3Player guest = Quake3Player.find.query().where().eq("name", PREFIX + "guest").findOne();
        assertNull(guest.getMember());

        // Every nick counts as one player only, whatever its case.
        assertEquals(BAD_REQUEST, save("/admin/quake3-players/" + guest.getId(), admin,
                Map.of("member", "", "name", PREFIX + "guest", "nicks", PREFIX + "Lamagra")).status());
        assertEquals(BAD_REQUEST, save("/admin/quake3-players", admin,
                Map.of("member", String.valueOf(lama.getId()), "name", "", "nicks", "")).status());
        assertEquals(BAD_REQUEST, save("/admin/quake3-players", admin,
                Map.of("member", "", "name", "", "nicks", PREFIX + "nobody")).status());

        assertEquals(SEE_OTHER, save("/admin/quake3-players/" + player.getId(), admin,
                Map.of("member", String.valueOf(lama.getId()), "name", "", "nicks", PREFIX + "lamagra")).status());
        assertEquals(List.of(PREFIX + "lamagra"), Quake3Player.find.byId(player.getId()).nickNames());

        assertEquals(OK, route(app, CSRFTokenHelper.addCSRFToken(signedIn(GET, "/admin/quake3-players", admin))).status());
        assertEquals(SEE_OTHER, save("/admin/quake3-players/" + guest.getId() + "/delete", admin, Map.of()).status());
        assertNull(Quake3Player.find.byId(guest.getId()));
    }

    @Test
    public void needsTheQuake3Role() {
        User other = member("other", Role.NEWS);
        assertEquals(FORBIDDEN, route(app, signedIn(GET, "/admin/quake3-players", other)).status());
        assertEquals(FORBIDDEN, save("/admin/quake3-players", other,
                Map.of("member", "", "name", PREFIX + "x", "nicks", "")).status());
    }

    private Result save(String uri, User member, Map<String, String> form) {
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
        member.save();
        if (role != null) {
            UserRole entry = new UserRole();
            entry.setMember(member);
            entry.setRole(role);
            entry.save();
        }
        return User.find.byId(member.getId());
    }
}
