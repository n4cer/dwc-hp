package controllers;

import models.Map;
import models.Score;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class N4botDemoImporterTest {
    @Test
    public void takesTheOnlyPlayOfTheMap() {
        Score dm6 = score(1, "q3dm6", 10, 5);
        Score dm17 = score(2, "q3dm17", 3, 7);
        assertSame(dm6, N4botDemoImporter.pickScore(List.of(dm6, dm17), "Q3DM6", null, null));
        assertNull(N4botDemoImporter.pickScore(List.of(dm6, dm17), "q3dm7", 10, 5));
    }

    @Test
    public void tellsAMapPlayedTwiceApartByTheResult() {
        Score first = score(1, "q3dm6", 10, 5);
        Score second = score(2, "q3dm6", 8, 8);
        assertSame(second, N4botDemoImporter.pickScore(List.of(first, second), "q3dm6", 8, 8));
        // Without dwc's side, or with a result neither play had, it stays for the administration.
        assertNull(N4botDemoImporter.pickScore(List.of(first, second), "q3dm6", null, null));
        assertNull(N4botDemoImporter.pickScore(List.of(first, second), "q3dm6", 5, 10));
        assertNull(N4botDemoImporter.pickScore(List.of(score(1, "q3dm6", 8, 8), second), "q3dm6", 8, 8));
    }

    @Test
    public void fallsBackToMapsNobodyWroteDown() {
        Score unknown = score(1, "-", 10, 5);
        Score dm17 = score(2, "q3dm17", 3, 7);
        assertSame(unknown, N4botDemoImporter.pickScore(List.of(unknown, dm17), "q3dm6", 10, 5));
    }

    @Test
    public void namesTheRecorderAsTheSiteKnowsThem() {
        java.util.Map<String, Quake3Stats.Identity> aliases = Quake3Stats.aliases(List.of(
                Quake3Stats.Identity.member("n4cer", 7L, List.of())));
        assertEquals("n4cer", N4botDemoImporter.player("^1dwc.^7n4cer", aliases));
        assertEquals("XyZ|foo", N4botDemoImporter.player("^4XyZ^7|foo", aliases));
        assertEquals("Ex-Tr3me", N4botDemoImporter.player("dwc.Ex-Tr3me", aliases));
        assertEquals("unknown", N4botDemoImporter.player("", aliases));
    }

    @Test
    public void cleansUpOldFilesNoMapRefersTo() throws Exception {
        Path folder = Files.createTempDirectory("clanwar-demos");
        Instant now = Instant.now();
        // Change times cannot be set back, so the cutoff moves forward instead.
        Instant cutoff = now.plus(Duration.ofMinutes(1));
        for (String name : List.of("assigned.dm_68", "orphan.dm_68", "fresh.dm_68", ".n4bot-x.dm_68.partial", "notes.txt")) {
            Files.writeString(folder.resolve(name), "x");
        }
        Files.setLastModifiedTime(folder.resolve("fresh.dm_68"), FileTime.from(now.plus(Duration.ofDays(1))));

        assertEquals(2, N4botDemoImporter.cleanUp(folder, Set.of("assigned.dm_68"), cutoff));
        try (var left = Files.list(folder)) {
            assertEquals(Set.of("assigned.dm_68", "fresh.dm_68", "notes.txt"),
                    Set.copyOf(left.map(path -> path.getFileName().toString()).toList()));
        }
        assertEquals(0, N4botDemoImporter.cleanUp(folder.resolve("missing"), Set.of(), cutoff));
    }

    private static Score score(long id, String mapName, int dwc, int enemy) {
        Map map = new Map();
        map.setMap(mapName);
        Score score = new Score();
        score.setId(id);
        score.setMap(map);
        score.setDwcScore(dwc);
        score.setEnemyScore(enemy);
        return score;
    }
}
