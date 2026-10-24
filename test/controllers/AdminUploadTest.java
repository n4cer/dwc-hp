package controllers;

import models.Role;
import models.User;
import models.UserRole;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static controllers.AdminUploadController.Target.BASEQ3;
import static controllers.AdminUploadController.Target.DOWNLOADS;
import static controllers.AdminUploadController.Target.OSP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminUploadTest {
    private static final Instant NOW = Instant.parse("2026-10-24T14:00:00Z");

    @Test
    public void readsTheSidecarsUploadList() {
        List<Quake3ControlClient.Upload> uploads = Quake3ControlClient.parseUploads("""
                [{"name": "zzz_dwc_bots.pk3", "size": 195801, "mtime": 1792850134,
                  "replaces": {"baseq3": {"size": 181788, "mtime": 1792843980}}},
                 {"name": "Quake3.zip", "size": 670674792, "mtime": 1792850200, "replaces": {}}]""");
        assertEquals(2, uploads.size());
        assertEquals("zzz_dwc_bots.pk3", uploads.get(0).name());
        assertEquals(195801, uploads.get(0).size());
        assertEquals(Instant.ofEpochSecond(1792850134), uploads.get(0).modified());
        assertEquals(new Quake3ControlClient.Existing(181788, Instant.ofEpochSecond(1792843980)),
                uploads.get(0).replaces().get("baseq3"));
        assertTrue(uploads.get(1).replaces().isEmpty());
    }

    @Test
    public void pk3sGoToTheServersAndServableFilesToTheDownloads() {
        assertTrue(BASEQ3.accepts("zzz_dwc_bots.pk3"));
        assertTrue(OSP.accepts("map_cpm18r.PK3"));
        assertTrue(DOWNLOADS.accepts("zzz_dwc_bots.pk3"));
        assertTrue(DOWNLOADS.accepts("Quake3.zip"));
        assertFalse(BASEQ3.accepts("Quake3.zip"));
        // Nothing the download section would refuse to serve anyway.
        assertFalse(DOWNLOADS.accepts("setup.exe"));
        // The retail paks are id's only in baseq3; osp ships its own pak0.pk3.
        assertFalse(BASEQ3.accepts("pak0.pk3"));
        assertFalse(BASEQ3.accepts("PAK8.PK3"));
        assertTrue(OSP.accepts("pak0.pk3"));
    }

    @Test
    public void offersOnlyTheTargetsOfTheMembersRoles() {
        Quake3ControlClient.Upload pk3 = upload("zzz_dwc_bots.pk3", NOW.minusSeconds(3600));
        Quake3ControlClient.Upload zip = upload("Quake3.zip", NOW.minusSeconds(3600));
        User quake3 = member(Role.QUAKE3);
        User media = member(Role.MEDIA);
        User admin = member(Role.ADMIN);

        assertEquals(List.of(BASEQ3, OSP), AdminUploadController.row(pk3, quake3, NOW).targets());
        assertEquals(List.of(DOWNLOADS), AdminUploadController.row(pk3, media, NOW).targets());
        assertEquals(List.of(BASEQ3, OSP, DOWNLOADS), AdminUploadController.row(pk3, admin, NOW).targets());
        assertEquals(List.of(), AdminUploadController.row(zip, quake3, NOW).targets());
        assertEquals(List.of(), AdminUploadController.row(upload("bad name.zip", NOW.minusSeconds(3600)), admin, NOW)
                .targets());
    }

    @Test
    public void flagsFilesThatMayStillBeUploading() {
        User admin = member(Role.ADMIN);
        assertTrue(AdminUploadController.row(upload("Quake3.zip", NOW.minusSeconds(10)), admin, NOW).settling());
        assertFalse(AdminUploadController.row(upload("Quake3.zip", NOW.minusSeconds(61)), admin, NOW).settling());
    }

    private static Quake3ControlClient.Upload upload(String name, Instant modified) {
        return new Quake3ControlClient.Upload(name, 1024, modified, Map.of());
    }

    private static User member(Role role) {
        UserRole entry = new UserRole();
        entry.setRole(role);
        User member = new User();
        member.setRoles(List.of(entry));
        return member;
    }
}
