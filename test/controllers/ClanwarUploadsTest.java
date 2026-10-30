package controllers;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClanwarUploadsTest {
    @Test
    public void namesAreMadeSafe() {
        assertEquals("My_Demo_1_.dm_68", ClanwarUploads.safeName("My Demo (1).dm_68", HomeController::isClanwarDemo));
        assertEquals("final.dm_68", ClanwarUploads.safeName("C:\\Users\\n4cer\\demos\\final.dm_68", HomeController::isClanwarDemo));
        assertEquals("shot.png", ClanwarUploads.safeName("../shot.png", ClanwarUploads::isScreenshot));
        assertEquals("q3dm6.jpg", ClanwarUploads.safeName("..q3dm6.jpg", ClanwarUploads::isScreenshot));
    }

    @Test
    public void namesTheTargetRefusesAreRejected() {
        assertNull(ClanwarUploads.safeName("notes.txt", HomeController::isClanwarDemo));
        assertNull(ClanwarUploads.safeName("shot.svg", ClanwarUploads::isScreenshot));
        assertNull(ClanwarUploads.safeName("demo.dm_68", ClanwarUploads::isScreenshot));
        assertNull(ClanwarUploads.safeName("....", ClanwarUploads::isScreenshot));
        assertNull(ClanwarUploads.safeName(null, ClanwarUploads::isScreenshot));
    }

    @Test
    public void longNamesKeepTheirExtensionAndRoomForACounter() {
        String name = ClanwarUploads.safeName("a".repeat(200) + ".dm_68", HomeController::isClanwarDemo);
        assertEquals(120, name.length());
        assertTrue(name.endsWith(".dm_68"));
        assertTrue(HomeController.isClanwarDemo(name.replace(".dm_68", "-99.dm_68")));
    }

    @Test
    public void storedFilesNeverReplaceExistingOnes() throws IOException {
        Path folder = Files.createTempDirectory("clanwar-uploads");
        Path source = Files.createTempFile("upload", ".tmp");
        try {
            Files.writeString(source, "new");
            Files.writeString(folder.resolve("final.dm_68"), "old");

            assertEquals("final-1.dm_68", ClanwarUploads.store(source, folder, "final.dm_68"));
            assertEquals("final-2.dm_68", ClanwarUploads.store(source, folder, "final.dm_68"));
            assertEquals("old", Files.readString(folder.resolve("final.dm_68")));
            assertEquals("new", Files.readString(folder.resolve("final-1.dm_68")));
            try (Stream<Path> files = Files.list(folder)) {
                assertFalse("no .partial file is left behind",
                        files.anyMatch(path -> path.getFileName().toString().endsWith(".partial")));
            }
        } finally {
            Files.deleteIfExists(source);
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @Test
    public void storeCreatesTheFolder() throws IOException {
        Path root = Files.createTempDirectory("clanwar-uploads");
        Path source = Files.createTempFile("upload", ".tmp");
        try {
            assertEquals("shot.png", ClanwarUploads.store(source, root.resolve("clanwars"), "shot.png"));
            assertEquals(List.of("shot.png"), Files.list(root.resolve("clanwars")).map(path -> path.getFileName().toString()).toList());
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(root.resolve("clanwars").resolve("shot.png"));
            Files.deleteIfExists(root.resolve("clanwars"));
            Files.deleteIfExists(root);
        }
    }
}
