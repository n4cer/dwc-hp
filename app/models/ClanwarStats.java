package models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClanwarStats {

  public static class Entry {
    private final String label;
    private int wins;
    private int losses;
    private int draws;

    public Entry(String label) {
      this.label = label;
    }

    private void add(String outcome) {
      if ("win".equals(outcome)) {
        wins++;
      } else if ("loss".equals(outcome)) {
        losses++;
      } else if ("draw".equals(outcome)) {
        draws++;
      }
    }

    public String getLabel() {
      return label;
    }

    public int getWins() {
      return wins;
    }

    public int getLosses() {
      return losses;
    }

    public int getDraws() {
      return draws;
    }

    public int getTotal() {
      return wins + losses + draws;
    }

    public int getWinRate() {
      int total = getTotal();
      return total == 0 ? 0 : Math.round(wins * 100f / total);
    }
  }

  private final Entry overall = new Entry("overall");
  private final Map<String, Entry> byGame = new LinkedHashMap<>();
  private final Map<String, Entry> byGametype = new LinkedHashMap<>();

  public static ClanwarStats from(List<Clanwar> clanwars) {
    ClanwarStats stats = new ClanwarStats();
    for (Clanwar clanwar : clanwars) {
      String outcome = clanwar.getOutcome();
      if (outcome == null) {
        continue;
      }

      stats.overall.add(outcome);

      String gameLabel = clanwar.getGame() != null ? clanwar.getGame().toString() : "-";
      stats.byGame.computeIfAbsent(gameLabel, Entry::new).add(outcome);

      String gametypeLabel = clanwar.getGametype() != null ? clanwar.getGametype().toString() : "-";
      stats.byGametype.computeIfAbsent(gametypeLabel, Entry::new).add(outcome);
    }
    return stats;
  }

  public Entry getOverall() {
    return overall;
  }

  public List<Entry> getByGame() {
    return new ArrayList<>(byGame.values());
  }

  public List<Entry> getByGametype() {
    return new ArrayList<>(byGametype.values());
  }
}
