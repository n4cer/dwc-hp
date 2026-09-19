package controllers;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadFileTest {
    private static final Set<String> FILES = Set.of("zip", "pk3", "cfg", "dm_68");

    @Test
    public void acceptsOnlyWhitelistedDownloadTypes() {
        assertTrue(HomeController.hasExtension(Path.of("dwc-configs.zip"), FILES));
        assertTrue(HomeController.hasExtension(Path.of("DWC-CONFIGS.ZIP"), FILES));
        assertTrue(HomeController.hasExtension(Path.of("q3dm17.pk3"), FILES));
        assertTrue(HomeController.hasExtension(Path.of("final.dm_68"), FILES));
        // Anything else in the folder stays unreachable.
        assertFalse(HomeController.hasExtension(Path.of("setup.exe"), FILES));
        assertFalse(HomeController.hasExtension(Path.of("backup.sql"), FILES));
        assertFalse(HomeController.hasExtension(Path.of("archive.zip.exe"), FILES));
        assertFalse(HomeController.hasExtension(Path.of("no-extension"), FILES));
    }
}
