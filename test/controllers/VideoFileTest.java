package controllers;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoFileTest {
    private static final Set<String> WEB = Set.of("mp4", "webm");
    private static final Set<String> DOWNLOAD = Set.of("mkv", "avi", "mpg", "mp4");

    @Test
    public void acceptsOnlyBrowserPlayableContainersForStreaming() {
        assertTrue(HomeController.hasExtension(Path.of("movie.mp4"), WEB));
        assertTrue(HomeController.hasExtension(Path.of("movie.MP4"), WEB));
        assertTrue(HomeController.hasExtension(Path.of("movie.webm"), WEB));
        // Originals must never be handed to a <video> element.
        assertFalse(HomeController.hasExtension(Path.of("VTS_01_1.mkv"), WEB));
        assertFalse(HomeController.hasExtension(Path.of("movie.mp4.exe"), WEB));
        assertFalse(HomeController.hasExtension(Path.of("no-extension"), WEB));
    }

    @Test
    public void acceptsArchiveContainersForDownload() {
        assertTrue(HomeController.hasExtension(Path.of("VTS_01_1.mkv"), DOWNLOAD));
        assertTrue(HomeController.hasExtension(Path.of("old.AVI"), DOWNLOAD));
        assertFalse(HomeController.hasExtension(Path.of("passwords.txt"), DOWNLOAD));
    }
}
