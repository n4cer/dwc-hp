package models;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class ScoreDemoTest {
    private static final Date PLAYED =
            Date.from(LocalDateTime.of(2026, 10, 28, 20, 30).atZone(ZoneId.systemDefault()).toInstant());

    @Test
    public void namesTheDownloadAfterClanwarMapAndRecorder() {
        assertEquals("dwc.n4cer-tdm-q3dm6-2026.10.28-2030-123.dm_68",
                ScoreDemo.downloadName("n4cer", "tdm", "q3dm6", PLAYED, 123L, null, "dm_68"));
    }

    @Test
    public void countsTheMapsPlayedTwice() {
        assertEquals("dwc.n4cer-ctf-q3wctf1-2026.10.28-2030-123-2.zip",
                ScoreDemo.downloadName("n4cer", "ctf", "q3wctf1", PLAYED, 123L, 2, "zip"));
    }

    @Test
    public void keepsTheNameApartFromTheSeparators() {
        // No doubled clan tag, no colour codes, nothing that reads as another field.
        assertEquals("dwc.n4cer-tdm-q3dm6-2026.10.28-2030-123.dm_68",
                ScoreDemo.downloadName("DWC.n4cer", "tdm", "q3dm6", PLAYED, 123L, null, "dm_68"));
        assertEquals("dwc.Ex_Tr3me-tdm-q3dm6-2026.10.28-2030-123.dm_68",
                ScoreDemo.downloadName("^1Ex-Tr3me ", "tdm", "q3dm6", PLAYED, 123L, null, "dm_68"));
        assertEquals("dwc.unknown-unknown-q3dm6-2026.10.28-2030-123.dm_68",
                ScoreDemo.downloadName("\"/\"", null, "q3dm6", PLAYED, 123L, null, "dm_68"));
    }
}
