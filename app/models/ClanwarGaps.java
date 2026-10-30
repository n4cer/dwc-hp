package models;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** What is still missing from the Quake 3 clan wars: demos, screenshots and line-ups. */
public class ClanwarGaps {

  public static final String GAME = "Quake III Arena";
  public static final String DEMOS = "demos";
  public static final String SCREENSHOTS = "screenshots";
  public static final String LINEUP = "lineup";

  public record MapGap(Score score, boolean demoMissing, boolean screenshotMissing) {
    public boolean isComplete() {
      return !demoMissing && !screenshotMissing;
    }
  }

  public record Entry(Clanwar clanwar, List<MapGap> maps, boolean lineupMissing) {
    public boolean isDemoMissing() {
      return maps.stream().anyMatch(MapGap::demoMissing);
    }

    public boolean isScreenshotMissing() {
      return maps.stream().anyMatch(MapGap::screenshotMissing);
    }

    public boolean isComplete() {
      return !lineupMissing && !isDemoMissing() && !isScreenshotMissing();
    }

    public boolean has(String gap) {
      return switch (gap) {
        case DEMOS -> isDemoMissing();
        case SCREENSHOTS -> isScreenshotMissing();
        case LINEUP -> lineupMissing;
        default -> !isComplete();
      };
    }
  }

  public static class Totals {
    private int clanwars;
    private int complete;
    private int maps;
    private int mapsWithoutDemo;
    private int mapsWithoutScreenshot;
    private int mapsWithRenderableScreenshot;
    private int clanwarsWithoutLineup;

    private void add(Entry entry) {
      clanwars++;
      if (entry.isComplete()) complete++;
      if (entry.lineupMissing()) clanwarsWithoutLineup++;
      for (MapGap map : entry.maps()) {
        maps++;
        if (map.demoMissing()) mapsWithoutDemo++;
        if (map.screenshotMissing()) mapsWithoutScreenshot++;
        if (map.screenshotMissing() && !map.demoMissing()) mapsWithRenderableScreenshot++;
      }
    }

    public int getClanwars() {
      return clanwars;
    }

    public int getComplete() {
      return complete;
    }

    public int getMaps() {
      return maps;
    }

    public int getMapsWithoutDemo() {
      return mapsWithoutDemo;
    }

    public int getMapsWithoutScreenshot() {
      return mapsWithoutScreenshot;
    }

    public int getMapsWithRenderableScreenshot() {
      return mapsWithRenderableScreenshot;
    }

    public int getClanwarsWithoutLineup() {
      return clanwarsWithoutLineup;
    }
  }

  public record Year(int year, Totals totals, List<Entry> entries) {
  }

  private final Totals totals = new Totals();
  private final List<Year> years = new ArrayList<>();

  /** Totals always cover every clan war; only the listed entries are narrowed down to the gap. */
  public static ClanwarGaps from(List<Clanwar> clanwars, String gap) {
    ClanwarGaps gaps = new ClanwarGaps();
    TreeMap<Integer, Year> byYear = new TreeMap<>();
    for (Clanwar clanwar : clanwars) {
      Entry entry = entryOf(clanwar);
      int year = clanwar.getDate().toInstant().atZone(ZoneId.systemDefault()).getYear();
      Year group = byYear.computeIfAbsent(year, y -> new Year(y, new Totals(), new ArrayList<>()));
      gaps.totals.add(entry);
      group.totals().add(entry);
      if (entry.has(gap)) group.entries().add(entry);
    }
    gaps.years.addAll(byYear.descendingMap().values());
    return gaps;
  }

  private static Entry entryOf(Clanwar clanwar) {
    List<MapGap> maps = new ArrayList<>();
    if (clanwar.getScores() != null) {
      for (Score score : clanwar.getScores()) {
        maps.add(new MapGap(score, isEmpty(score.getDemos()), isEmpty(score.getImages())));
      }
    }
    // In the order the maps were entered, which is the order they were played in.
    maps.sort(Comparator.comparing(map -> map.score().getId(), Comparator.nullsFirst(Comparator.naturalOrder())));
    return new Entry(clanwar, maps, isEmpty(clanwar.getLineups()));
  }

  private static boolean isEmpty(List<?> list) {
    return list == null || list.isEmpty();
  }

  public Totals getTotals() {
    return totals;
  }

  public List<Year> getYears() {
    return years;
  }
}
