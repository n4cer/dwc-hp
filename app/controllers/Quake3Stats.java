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
    private static final int MIN_SHOTS_FOR_BEST_AIM = 50;
    private static final Quake3GamesLog.WeaponStat NO_SHOTS = new Quake3GamesLog.WeaponStat(0, 0);

    public record Player(String name, boolean bot, int matches, int wins, int kills, int deaths, int suicides,
                         int teamKills, long damageGiven, long damageReceived,
                         Map<String, Integer> killsByWeapon, Map<String, Quake3GamesLog.WeaponStat> weapons) {
        public double kdValue() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        public String kd() {
            return String.format(Locale.ROOT, "%.2f", kdValue());
        }

        /** Hit ratio in percent, or -1 when the player never fired that weapon. */
        public double accuracyValue(String weapon) {
            Quake3GamesLog.WeaponStat stat = weapons.get(weapon);
            if (stat == null || stat.shots() == 0) return -1;
            return 100.0 * stat.hits() / stat.shots();
        }

        public String accuracy(String weapon) {
            double value = accuracyValue(weapon);
            return value < 0 ? "–" : Math.round(value) + "%";
        }

        /** The accuracy rounded to the pct-N width classes; the CSP rules out inline widths. */
        public int accuracyStep(String weapon) {
            double value = accuracyValue(weapon);
            return value < 0 ? 0 : (int) Math.min(100, Math.round(value / 5) * 5);
        }

        public String favouriteWeapon() {
            return killsByWeapon.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("Other"))
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("–");
        }
    }

    /**
     * One line in the latest matches: a duel shows the two players, a team game the two
     * teams. winner is 1 for the left side, 2 for the right side and 0 for a draw.
     */
    public record MatchRow(LocalDateTime start, String gametype, String map, int durationSeconds, boolean teamGame,
                           String leftName, int leftScore, String rightName, int rightScore, int winner,
                           List<String> players) {
        public String duration() {
            return durationSeconds / 60 + ":" + String.format("%02d", durationSeconds % 60);
        }

        public String result() {
            return leftName + " " + leftScore + " : " + rightScore + " " + rightName;
        }
    }

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

    public long totalKills() {
        return leaderboard.stream().mapToLong(Player::kills).sum();
    }

    /** Most frags; the leaderboard's own order already puts the most wins first. */
    public Player topFragger() {
        return leaderboard.stream().max(Comparator.comparingInt(Player::kills)).orElse(null);
    }

    /** Best hit ratio with the weapon, among players who fired it often enough to mean anything. */
    public Player bestAim(String weapon) {
        return leaderboard.stream()
                .filter(player -> player.weapons().getOrDefault(weapon, NO_SHOTS).shots() >= MIN_SHOTS_FOR_BEST_AIM)
                .max(Comparator.comparingDouble(player -> player.accuracyValue(weapon))).orElse(null);
    }

    public static List<String> accuracyWeapons() {
        return ACCURACY_WEAPONS;
    }

    /** The abbreviations players use; the full OSP name goes into the tooltip. */
    public static String shortWeapon(String weapon) {
        return switch (weapon) {
            case "Gauntlet" -> "GT";
            case "MachineGun" -> "MG";
            case "Shotgun" -> "SG";
            case "G.Launcher" -> "GL";
            case "R.Launcher" -> "RL";
            case "LightningGun" -> "LG";
            case "Railgun" -> "RG";
            case "Plasmagun" -> "PG";
            case "BFG10K" -> "BFG";
            default -> weapon;
        };
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
        List<String> players = match.players().stream()
                .map(player -> player.name() + " (" + player.score() + ")").toList();
        String leftName, rightName;
        int leftScore, rightScore;
        if (match.teamGame()) {
            leftName = "Red";
            rightName = "Blue";
            leftScore = match.redScore() == null ? 0 : match.redScore();
            rightScore = match.blueScore() == null ? 0 : match.blueScore();
        } else {
            // Players arrive sorted by score, so the first two are the ones that matter.
            leftName = match.players().get(0).name();
            leftScore = match.players().get(0).score();
            rightName = match.players().get(1).name();
            rightScore = match.players().get(1).score();
        }
        int winner = leftScore > rightScore ? 1 : rightScore > leftScore ? 2 : 0;
        return new MatchRow(match.start(), match.gametype(), match.map(), match.durationSeconds(), match.teamGame(),
                leftName, leftScore, rightName, rightScore, winner, players);
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
