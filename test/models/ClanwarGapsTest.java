package models;

import org.junit.Test;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClanwarGapsTest {

    private static Score score(boolean demo, boolean screenshot) {
        Score score = new Score();
        score.setDemos(demo ? List.of(new ScoreDemo()) : List.of());
        score.setImages(screenshot ? List.of(new ScoreImage()) : List.of());
        return score;
    }

    private static Clanwar clanwar(int year, boolean lineup, Score... scores) {
        Clanwar clanwar = new Clanwar();
        Date date = new GregorianCalendar(year, 5, 15).getTime();
        clanwar.setDate(date);
        clanwar.setLineups(lineup ? List.of(new MatchLineup()) : List.of());
        clanwar.setScores(List.of(scores));
        return clanwar;
    }

    @Test
    public void countsMissingDemosScreenshotsAndLineups() {
        ClanwarGaps gaps = ClanwarGaps.from(List.of(
                clanwar(2004, true, score(true, true), score(true, false)),
                clanwar(2004, true, score(true, true)),
                clanwar(2001, false, score(false, false))), "");

        ClanwarGaps.Totals totals = gaps.getTotals();
        assertEquals(3, totals.getClanwars());
        assertEquals(1, totals.getComplete());
        assertEquals(4, totals.getMaps());
        assertEquals(1, totals.getMapsWithoutDemo());
        assertEquals(2, totals.getMapsWithoutScreenshot());
        assertEquals(1, totals.getMapsWithRenderableScreenshot());
        assertEquals(1, totals.getClanwarsWithoutLineup());
    }

    @Test
    public void groupsByYearNewestFirstAndListsOnlyIncompleteClanwars() {
        ClanwarGaps gaps = ClanwarGaps.from(List.of(
                clanwar(2001, false, score(false, false)),
                clanwar(2004, true, score(true, true)),
                clanwar(2004, true, score(true, false))), "");

        assertEquals(2004, gaps.getYears().get(0).year());
        assertEquals(2, gaps.getYears().get(0).totals().getClanwars());
        assertEquals(1, gaps.getYears().get(0).entries().size());
        assertEquals(2001, gaps.getYears().get(1).year());
    }

    @Test
    public void filterNarrowsTheListButNotTheTotals() {
        ClanwarGaps gaps = ClanwarGaps.from(List.of(
                clanwar(2004, true, score(true, false)),
                clanwar(2004, false, score(true, true))), ClanwarGaps.LINEUP);

        assertEquals(2, gaps.getTotals().getClanwars());
        assertEquals(1, gaps.getYears().get(0).entries().size());
        assertTrue(gaps.getYears().get(0).entries().get(0).lineupMissing());
        assertFalse(gaps.getYears().get(0).entries().get(0).isScreenshotMissing());
    }
}
