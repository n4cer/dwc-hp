package controllers;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RandomPictureTest {
    @Test
    public void acceptsOnlyKnownImageExtensions() {
        assertTrue(HomeController.hasAllowedImageExtension(Path.of("picture.JPG")));
        assertTrue(HomeController.hasAllowedImageExtension(Path.of("picture.webp")));
        assertFalse(HomeController.hasAllowedImageExtension(Path.of("secret.txt")));
        assertFalse(HomeController.hasAllowedImageExtension(Path.of("image.png.exe")));
        assertFalse(HomeController.hasAllowedImageExtension(Path.of("no-extension")));
    }
}
