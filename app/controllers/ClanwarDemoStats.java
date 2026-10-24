package controllers;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Quake 3 demo statistics of one clan war, read from n4bot's
 * /api/q3/clanwars/{id} answer: one entry per map, each from the demo with the
 * most complete server statistics. Damage and weapon values are missing when
 * the server did not send them; the page then shows a dash.
 */
public record ClanwarDemoStats(long clanwarId, List<MapStats> maps) {
    private static final Pattern CLAN_TAG = Pattern.compile("^\\W*dwc\\W+", Pattern.CASE_INSENSITIVE);
    private static final int MATRIX_MAX_PLAYERS = 16;

    public record MapStats(String map, String scoreSummary, String duration, boolean hasMatchStats,
                           String demoUrl, List<Pov> povs, List<PlayerRow> players, List<String> weapons,
                           List<String> matrixNames, List<List<Integer>> matrix) {
        public boolean hasWeapons() {
            return !weapons.isEmpty();
        }

        public boolean hasMatrix() {
            return !matrixNames.isEmpty();
        }
    }

    public record Pov(String demoTaker, String url) { }

    public record Weapon(Integer accuracy, Integer hits, Integer shots, Integer kills) { }

    public record PlayerRow(String name, String shownName, Long memberId, String team, Integer score,
                            int kills, int deaths, Integer damageGiven, Integer damageReceived, Integer accuracy,
                            Integer impressives, Integer excellents, Integer captures, Map<String, Weapon> weapons) {
        public String kd() {
            return deaths == 0 ? String.format(Locale.ROOT, "%.2f", (double) kills)
                    : String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
        }

        public String net() {
            if (damageGiven == null || damageReceived == null) return null;
            int net = damageGiven - damageReceived;
            return (net > 0 ? "+" : "") + net;
        }

        public String teamClass() {
            return "red".equals(team) ? "q3-team-red" : "blue".equals(team) ? "q3-team-blue" : "";
        }
    }

    static ClanwarDemoStats fromJson(JsonNode node, Map<String, Quake3Stats.Identity> aliases) {
        List<MapStats> maps = new ArrayList<>();
        for (JsonNode entry : node.path("maps")) maps.add(map(entry, aliases));
        return new ClanwarDemoStats(node.path("clanwar_id").asLong(), maps);
    }

    private static MapStats map(JsonNode entry, Map<String, Quake3Stats.Identity> aliases) {
        JsonNode match = entry.path("match");
        List<PlayerRow> players = new ArrayList<>();
        List<String> weapons = new ArrayList<>();
        for (JsonNode player : match.path("players")) {
            PlayerRow row = player(player, aliases);
            for (String weapon : row.weapons().keySet()) if (!weapons.contains(weapon)) weapons.add(weapon);
            players.add(row);
        }
        players.sort((a, b) -> {
            int team = Integer.compare(teamOrder(a.team()), teamOrder(b.team()));
            if (team != 0) return team;
            return Integer.compare(b.score() == null ? 0 : b.score(), a.score() == null ? 0 : a.score());
        });

        List<Pov> povs = new ArrayList<>();
        for (JsonNode demo : entry.path("demos")) povs.add(new Pov(text(demo, "demo_taker"), text(demo, "url")));

        List<String> matrixNames = new ArrayList<>();
        List<List<Integer>> matrix = new ArrayList<>();
        buildMatrix(players, match.path("obituaries"), matrixNames, matrix);

        return new MapStats(text(entry, "map"), text(match, "score_summary"), duration(match.path("duration_seconds")),
                match.path("has_match_stats").asBoolean(false), text(entry.path("primary_demo"), "url"),
                povs, players, weapons, matrixNames, matrix);
    }

    private static PlayerRow player(JsonNode player, Map<String, Quake3Stats.Identity> aliases) {
        String name = text(player, "name");
        Quake3Stats.Identity identity = identity(name, aliases);
        Map<String, Weapon> weapons = new LinkedHashMap<>();
        for (JsonNode weapon : player.path("weapons")) {
            weapons.put(text(weapon, "weapon"), new Weapon(integer(weapon, "accuracy"), integer(weapon, "hits"),
                    integer(weapon, "shots"), integer(weapon, "kills")));
        }
        return new PlayerRow(name, identity == null ? name : identity.name(), identity == null ? null : identity.memberId(),
                text(player, "team"), integer(player, "score"), player.path("kills").asInt(0),
                player.path("deaths").asInt(0), integer(player, "damage_given"), integer(player, "damage_received"),
                integer(player, "accuracy"), integer(player, "impressives"), integer(player, "excellents"),
                integer(player, "captures"), weapons);
    }

    /** Looks the in-game name up as maintained, then without the clan tag ("dwc.n4cer" -> "n4cer"). */
    static Quake3Stats.Identity identity(String name, Map<String, Quake3Stats.Identity> aliases) {
        if (name == null) return null;
        String key = Quake3GamesLog.stripColors(name).toLowerCase(Locale.ROOT);
        Quake3Stats.Identity identity = aliases.get(key);
        return identity != null ? identity : aliases.get(CLAN_TAG.matcher(key).replaceFirst(""));
    }

    private static void buildMatrix(List<PlayerRow> players, JsonNode obituaries,
                                    List<String> names, List<List<Integer>> matrix) {
        for (PlayerRow player : players) if (!"spectators".equals(player.team())) names.add(player.name());
        if (names.isEmpty() || names.size() > MATRIX_MAX_PLAYERS || obituaries.isEmpty()) {
            names.clear();
            return;
        }
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < names.size(); i++) index.put(names.get(i), i);
        int[][] counts = new int[names.size()][names.size()];
        for (JsonNode obituary : obituaries) {
            Integer attacker = index.get(text(obituary, "attacker"));
            Integer target = index.get(text(obituary, "target"));
            if (attacker != null && target != null && !attacker.equals(target)) counts[attacker][target]++;
        }
        for (int[] row : counts) {
            List<Integer> values = new ArrayList<>();
            for (int value : row) values.add(value);
            matrix.add(values);
        }
    }

    private static int teamOrder(String team) {
        if ("red".equals(team)) return 0;
        if ("blue".equals(team)) return 1;
        if ("spectators".equals(team)) return 3;
        return 2;
    }

    private static String duration(JsonNode seconds) {
        if (!seconds.isNumber()) return null;
        int value = seconds.asInt();
        return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }
}
