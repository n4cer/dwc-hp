package controllers;

import models.User;
import org.junit.Test;
import play.mvc.Http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthLogTest {
    @Test
    public void formatsOneLogfmtLineWithFixedPrefix() {
        Http.Request request = new Http.RequestBuilder()
                .uri("/admin/login")
                .remoteAddress("203.0.113.5")
                .header("User-Agent", "Mozilla/5.0 (X11)")
                .build();

        String line = AuthLog.event(AuthLog.Event.LOGIN_FAILED).reason("bad_password").nick("Foo").request(request).format();

        assertEquals("dwc-auth event=login_failed reason=bad_password nick=Foo src_ip=203.0.113.5"
                + " path=/admin/login ua=\"Mozilla/5.0 (X11)\"", line);
    }

    @Test
    public void includesMemberIdAndNick() {
        User member = new User();
        member.setId(12L);
        member.setNick("n4cer");

        String line = AuthLog.event(AuthLog.Event.LOGIN_SUCCESS).member(member).format();

        assertEquals("dwc-auth event=login_success member_id=12 nick=n4cer", line);
    }

    @Test
    public void escapesValuesSoAnAttackerCannotForgeFieldsOrLines() {
        String line = AuthLog.event(AuthLog.Event.LOGIN_FAILED)
                .nick("x\" event=login_success\nforged=1 \\").format();

        assertFalse(line.contains("\n"));
        assertEquals("dwc-auth event=login_failed nick=\"x\\\" event=login_success\\nforged=1 \\\\\"", line);
    }

    @Test
    public void quotesEmptyAndSpecialValues() {
        assertEquals("\"\"", AuthLog.quote(""));
        assertEquals("\"a=b\"", AuthLog.quote("a=b"));
        assertEquals("\"Müller\"", AuthLog.quote("Müller"));
        assertEquals("plain", AuthLog.quote("plain"));
    }

    @Test
    public void neverLogsThePasswordLinkToken() {
        Http.Request request = new Http.RequestBuilder().uri("/set-password/SECRETTOKEN").remoteAddress("192.0.2.9").build();

        String line = AuthLog.event(AuthLog.Event.PASSWORD_LINK_REJECTED).request(request).format();

        assertFalse(line.contains("SECRETTOKEN"));
        assertTrue(line.contains("path=/set-password/*"));
    }

    @Test
    public void truncatesLongNicks() {
        String line = AuthLog.event(AuthLog.Event.LOGIN_FAILED).nick("x".repeat(500)).format();

        assertTrue(line.endsWith("nick=" + "x".repeat(64)));
    }
}
