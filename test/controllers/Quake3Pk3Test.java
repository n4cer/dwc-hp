package controllers;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Quake3Pk3Test {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path root;

    @Before
    public void createServerData() throws IOException {
        root = temp.getRoot().toPath();
        for (String file : new String[] {"baseq3/pak0.pk3", "baseq3/PAK8.PK3", "baseq3/q3dm14tmp.pk3",
                "osp/map_cpm18r.pk3", "osp/pak0.pk3", "osp/notes.txt", "secret.pk3"}) {
            Path path = root.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "x");
        }
    }

    @Test
    public void servesCustomPk3sFromTheModFolders() {
        assertEquals(root.resolve("osp/map_cpm18r.pk3"), HomeController.quake3Pk3Path(root, "osp", "map_cpm18r.pk3"));
        assertEquals(root.resolve("baseq3/q3dm14tmp.pk3"), HomeController.quake3Pk3Path(root, "baseq3", "q3dm14tmp.pk3"));
        // Only the retail paks in baseq3 are id's; a mod may ship its own pak0.pk3.
        assertEquals(root.resolve("osp/pak0.pk3"), HomeController.quake3Pk3Path(root, "osp", "pak0.pk3"));
    }

    @Test
    public void neverServesTheRetailIdPaks() {
        assertNull(HomeController.quake3Pk3Path(root, "baseq3", "pak0.pk3"));
        assertNull(HomeController.quake3Pk3Path(root, "baseq3", "PAK8.PK3"));
    }

    @Test
    public void rejectsEverythingElse() {
        assertNull(HomeController.quake3Pk3Path(root, "osp", "notes.txt"));
        assertNull(HomeController.quake3Pk3Path(root, "osp", "missing.pk3"));
        assertNull(HomeController.quake3Pk3Path(root, "osp", "..\\secret.pk3"));
        assertNull(HomeController.quake3Pk3Path(root, "osp", "../secret.pk3"));
        assertNull(HomeController.quake3Pk3Path(root, "..", "secret.pk3"));
        assertNull(HomeController.quake3Pk3Path(root, "missionpack", "map_cpm18r.pk3"));
    }
}
