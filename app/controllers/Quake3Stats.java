package controllers;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the Quake 3 statistics page shows: a leaderboard summed up over the
 * finished matches from {@link Quake3GamesLog}, and the latest matches.
 * Proof of concept: nothing is stored, it is recomputed from the logs.
 */
public final class Quake3Stats {
    public static final List<String> MODES = List.of("duel", "tdm", "ctf");
    private static final List<String> ACCURACY_WEAPONS = List.of("LightningGun", "Railgun", "R.Launcher");

    public record Player(String name, boolean bot, int matches, int wins, int kills, int deaths, int suicides,
                         int teamKills, long damageGiven, long damageReceived,
                         Map<String, Integer> killsByWeapon, Map<String, Quake3GamesLog.WeaponStat> weapons) {
        public String kd() {
            return deaths == 0 ? String.valueOf(kills) : String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
        }

        public String accuracy(String weapon) {
            Quake3GamesLog.WeaponStat stat = weapons.get(weapon);
            if (stat == null || stat.shots() == 0) return "–";
            return Math.round(100.0 * stat.hits() / stat.shots()) + "%";
        }

        public String favouriteWeapon() {
            return killsByWeapon.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("Other"))
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("–");
        }
    }

    public record MatchRow(LocalDateTime start, String gametype, String map, String duration, String result,
                           List<String> players) { }

    private final List<Player> leaderboard;
    private final List<MatchRow> recentMatches;
    private final int matchCount;

    private Quake3Stats(List<Player> leaderboard, List<MatchRow> recentMatches, int matchCount) {
        this.leaderboard = leaderboard;
        this.recentMatches = recentMatches;
        this.matchCount = matchCount;
    }

    public List<Player> leaderboard() {
        return leaderboard;
    }

    public List<MatchRow> recentMatches() {
        return recentMatches;
    }

    public int matchCount() {
        return matchCount;
    }

    public static List<String> accuracyWeapons() {
        return ACCURACY_WEAPONS;
    }

    /**
     * mode is one of {@link #MODES}, or anything else for all modes together. Without
     * includeBots a match a bot played in does not count at all, since frags against
     * bots would only inflate the numbers.
     */
    static Quake3Stats of(List<Quake3GamesLog.Match> allMatches, String mode, boolean includeBots, int recentLimit) {
        List<Quake3GamesLog.Match> matches = allMatches.stream()
                .filter(match -> !MODES.contains(mode) || match.gametype().equalsIgnoreCase(mode))
                .filter(match -> includeBots || !match.hasBots())
                .sorted(Comparator.comparing(Quake3GamesLog.Match::start,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();

        Map<String, Totals> totals = new LinkedHashMap<>();
        for (Quake3GamesLog.Match match : matches) {
            for (Quake3GamesLog.MatchPlayer player : match.players()) {
                totals.computeIfAbsent(player.name().toLowerCase(), key -> new Totals(player.name()))
                        .add(player, match.won(player));
            }
        }
        List<Player> leaderboard = totals.values().stream().map(Totals::toPlayer)
                .sorted(Comparator.comparingInt(Player::wins).reversed()
                        .thenComparing(Comparator.comparingInt(Player::kills).reversed()))
                .toList();
        List<MatchRow> recent = matches.stream().limit(recentLimit).map(Quake3Stats::row).toList();
        return new Quake3Stats(leaderboard, recent, matches.size());
    }

    static Quake3Stats empty() {
        return new Quake3Stats(List.of(), List.of(), 0);
    }

    private static MatchRow row(Quake3GamesLog.Match match) {
        String result;
        if (match.teamGame()) {
            result = "Red " + match.redScore() + " : " + match.blueScore() + " Blue";
        } else {
            Quake3GamesLog.MatchPlayer first = match.players().get(0);
            Quake3GamesLog.MatchPlayer second = match.players().get(1);
            result = first.name() + " " + first.score() + " : " + second.score() + " " + second.name();
        }
        List<String> players = match.players().stream()
                .map(player -> player.name() + " (" + player.score() + ")").toList();
        String duration = match.durationSeconds() / 60 + ":" + String.format("%02d", match.durationSeconds() % 60);
        return new MatchRow(match.start(), match.gametype(), match.map(), duration, result, players);
    }

    private static final class Totals {
        final String name;
        boolean bot;
        int matches, wins, kills, deaths, suicides, teamKills;
        long damageGiven, damageReceived;
        final Map<String, Integer> killsByWeapon = new LinkedHashMap<>();
        final Map<String, Quake3GamesLog.WeaponStat> weapons = new LinkedHashMap<>();

        Totals(String name) {
            this.name = name;
        }

        void add(Quake3GamesLog.MatchPlayer player, boolean won) {
            bot |= player.bot();
            matches++;
            if (won) wins++;
            kills += player.kills();
            deaths += player.deaths();
            suicides += player.suicides();
            teamKills += player.teamKills();
            damageGiven += player.damageGiven();
            damageReceived += player.damageReceived();
            player.killsByWeapon().forEach((weapon, count) -> killsByWeapon.merge(weapon, count, Integer::sum));
            player.weapons().forEach((weapon, stat) -> weapons.merge(weapon, stat, Quake3GamesLog.WeaponStat::plus));
        }

        Player toPlayer() {
            return new Player(name, bot, matches, wins, kills, deaths, suicides, teamKills, damageGiven,
                    damageReceived, Map.copyOf(killsByWeapon), Map.copyOf(weapons));
        }
    }
}
