package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.Clanwar;
import models.Score;
import models.ScoreDemo;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Cancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.Configuration;
import play.cache.SyncCacheApi;
import play.inject.ApplicationLifecycle;
import scala.concurrent.ExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Takes the demo files n4bot linked to a clan war over as demo downloads of the
 * matching map, so a demo uploaded to the bot ends up on the clan war page
 * without anyone filing it by hand. Each n4bot demo is taken over once: one
 * removed in the clan war form afterwards stays removed.
 *
 * n4bot groups demos by map name only, so a map played twice is told apart by
 * the result from dwc's side. A demo whose map cannot be found in the clan war,
 * or whose play stays ambiguous, still has its file put into the demo folder,
 * for the administration to add by hand; n4bot deletes its own copy after a while.
 *
 * Each run also cleans the demo folder up: a file no map refers to goes once it
 * has been there for clanwar_demo_retention_days (default 30, 0 keeps all).
 */
@Singleton
final class N4botDemoImporter {
    private static final Logger LOG = LoggerFactory.getLogger("n4bot");
    private static final Duration INTERVAL = Duration.ofMinutes(10);
    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final Pattern N4BOT_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Set<String> PLACEHOLDER_MAPS = Set.of("", "-", "?");
    private static final Pattern CLAN_TAG = Pattern.compile("^dwc\\.", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final N4botClient n4bot;
    private final SyncCacheApi cache;
    private final Configuration configuration;

    @Inject
    N4botDemoImporter(N4botClient n4bot, SyncCacheApi cache, Configuration configuration, ActorSystem actorSystem,
                    ApplicationLifecycle lifecycle) {
        this.n4bot = n4bot;
        this.cache = cache;
        this.configuration = configuration;

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "n4bot-demo-import");
            thread.setDaemon(true);
            return thread;
        });
        Runnable run = () -> {
            try {
                importAll();
            } catch (Exception exception) {
                log("import_failed", null, null, exception.getClass().getSimpleName());
            }
            try {
                cleanUp();
            } catch (Exception exception) {
                log("cleanup_failed", null, null, exception.getClass().getSimpleName());
            }
        };
        Cancellable schedule = actorSystem.scheduler().scheduleWithFixedDelay(Duration.ofMinutes(1), INTERVAL, run,
                ExecutionContext.fromExecutor(executor));
        lifecycle.addStopHook(() -> {
            schedule.cancel();
            executor.shutdownNow();
            return CompletableFuture.completedFuture(null);
        });
    }

    /** Takes over every demo not yet taken over; returns how many. */
    int importAll() throws Exception {
        int imported = 0;
        for (long clanwarId : await(n4bot.clanwarIds())) {
            Clanwar clanwar = Clanwar.find.byId(clanwarId);
            if (clanwar == null) continue;
            Optional<JsonNode> json = await(n4bot.clanwar(clanwarId));
            if (json.isEmpty()) continue;
            int before = imported;
            for (JsonNode map : json.get().path("maps")) {
                for (JsonNode demo : map.path("demos")) {
                    if (importDemo(clanwar, map.path("map").asText(""), demo)) imported++;
                }
            }
            if (imported > before) forgetPages(clanwarId);
        }
        return imported;
    }

    private boolean importDemo(Clanwar clanwar, String groupMap, JsonNode demo) throws Exception {
        String id = demo.path("id").asText("");
        String fileName = demo.path("file_name").asText("");
        int dot = fileName.lastIndexOf('.');
        if (!N4BOT_ID.matcher(id).matches() || dot < 0 || demo.path("file_url").isNull()) return false;
        // "n4bot-" keeps the name valid even for IDs that start with "-" or "_".
        String file = "n4bot-" + id + fileName.substring(dot).toLowerCase(Locale.ROOT);
        if (!HomeController.isClanwarDemo(file) || isImported(id)) return false;

        String mapName = demo.path("map").asText(groupMap);
        Integer dwcScore = demo.path("dwc_score").isInt() ? demo.path("dwc_score").asInt() : null;
        Integer enemyScore = demo.path("enemy_score").isInt() ? demo.path("enemy_score").asInt() : null;
        Score score = pickScore(Score.find.query().where().eq("match.id", clanwar.getId()).orderBy("id").findList(),
                mapName, dwcScore, enemyScore);
        boolean present = Files.exists(HomeController.clanwarDemoFolder(configuration).resolve(file));
        if (score == null && present) return false;

        Optional<byte[]> bytes = await(n4bot.demoFile(clanwar.getId(), demo.path("file_url").asText()));
        if (bytes.isEmpty()) return false;
        store(file, bytes.get());
        if (score == null) {
            log("demo_unassigned", clanwar.getId(), id, "no single play of " + mapName + " fits; file kept as " + file);
            return false;
        }

        ScoreDemo scoreDemo = new ScoreDemo();
        scoreDemo.setScore(score);
        scoreDemo.setFile(file);
        scoreDemo.setPlayer(player(demo.path("demo_taker").asText("")));
        scoreDemo.save();
        models.N4botDemoImport imported = new models.N4botDemoImport();
        imported.setN4botId(id);
        imported.setClanwarId(clanwar.getId());
        imported.setImportedAt(Instant.now());
        imported.save();
        LOG.info(N4botClient.PREFIX + " event=demo_imported clanwar_id=" + clanwar.getId() + " demo=" + id
                + " score_id=" + score.getId());
        return true;
    }

    private static boolean isImported(String id) {
        return models.N4botDemoImport.find.byId(id) != null;
    }

    /**
     * The play of the clan war a demo belongs to: the only one on its map, otherwise the
     * one with its result. Maps recorded as "-" stand in for a map nobody wrote down.
     */
    static Score pickScore(List<Score> scores, String map, Integer dwcScore, Integer enemyScore) {
        String wanted = map == null ? "" : map.trim().toLowerCase(Locale.ROOT);
        List<Score> candidates = scores.stream().filter(score -> mapName(score).equals(wanted)).toList();
        if (candidates.isEmpty()) {
            candidates = scores.stream().filter(score -> PLACEHOLDER_MAPS.contains(mapName(score))).toList();
        }
        if (candidates.size() == 1) return candidates.get(0);

        List<Score> byResult = new ArrayList<>();
        for (Score score : candidates) {
            if (dwcScore != null && dwcScore.equals(score.getDwcScore())
                    && enemyScore != null && enemyScore.equals(score.getEnemyScore())) {
                byResult.add(score);
            }
        }
        return byResult.size() == 1 ? byResult.get(0) : null;
    }

    private static String mapName(Score score) {
        return score.getMap() == null || score.getMap().getMap() == null ? ""
                : score.getMap().getMap().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The demo taker as the clan war page shows its lineup: a maintained Quake 3 player's
     * name, else the in-game name, either without colour codes and clan tag.
     */
    static String player(String demoTaker, Map<String, Quake3Stats.Identity> aliases) {
        Quake3Stats.Identity identity = ClanwarDemoStats.identity(demoTaker, aliases);
        String name = identity != null && identity.name() != null ? identity.name() : demoTaker;
        String plain = CLAN_TAG.matcher(Quake3GamesLog.stripColors(name).trim()).replaceFirst("");
        return plain.isEmpty() ? "unknown" : plain;
    }

    private String player(String demoTaker) {
        return player(demoTaker, Quake3Stats.aliases(HomeController.quake3Identities()));
    }

    /** Deletes the demo files no map refers to that have been in the folder for the retention period. */
    int cleanUp() throws IOException {
        int days = retentionDays();
        if (days <= 0) return 0;
        Set<String> referenced = new java.util.HashSet<>();
        for (ScoreDemo demo : ScoreDemo.find.query().select("file").findList()) referenced.add(demo.getFile());
        int deleted = cleanUp(HomeController.clanwarDemoFolder(configuration), referenced,
                Instant.now().minus(Duration.ofDays(days)));
        if (deleted > 0) LOG.info(N4botClient.PREFIX + " event=demo_files_deleted count=" + deleted);
        return deleted;
    }

    static int cleanUp(Path folder, Set<String> referenced, Instant cutoff) throws IOException {
        if (!Files.isDirectory(folder)) return 0;
        int deleted = 0;
        try (var entries = Files.list(folder)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName().toString();
                boolean partial = name.startsWith(".") && name.endsWith(".partial");
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || (!partial && (!HomeController.isClanwarDemo(name) || referenced.contains(name)))
                        || !arrivedBefore(path, cutoff)) {
                    continue;
                }
                Files.deleteIfExists(path);
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * When the file came into the folder. An SFTP upload keeps its upload time when the
     * sidecar moves it in, but the move updates the change time, so the later one counts.
     */
    private static boolean arrivedBefore(Path path, Instant cutoff) throws IOException {
        Instant arrived = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        try {
            Instant changed = ((FileTime) Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS)).toInstant();
            if (changed.isAfter(arrived)) arrived = changed;
        } catch (UnsupportedOperationException | IllegalArgumentException exception) {
            // No change time outside Unix; the modification time has to do.
        }
        return arrived.isBefore(cutoff);
    }

    private int retentionDays() {
        String path = "clanwar_demo_retention_days";
        if (!configuration.underlying().hasPath(path)) return DEFAULT_RETENTION_DAYS;
        try {
            return Integer.parseInt(configuration.underlying().getString(path).trim());
        } catch (NumberFormatException exception) {
            return DEFAULT_RETENTION_DAYS;
        }
    }

    /** Written next to its final place first, so a download never sees half a file. */
    private void store(String file, byte[] bytes) throws IOException {
        Path folder = HomeController.clanwarDemoFolder(configuration);
        Files.createDirectories(folder);
        Path partial = folder.resolve("." + file + ".partial");
        Files.write(partial, bytes);
        Files.move(partial, folder.resolve(file), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void forgetPages(long clanwarId) {
        cache.remove("clanwarsJson");
        for (String lang : List.of("de", "en")) cache.remove(HomeController.clanwarCacheKey(clanwarId, lang));
    }

    private static <T> T await(java.util.concurrent.CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    private static void log(String event, Long clanwarId, String demo, String error) {
        List<String> parts = new ArrayList<>(List.of(N4botClient.PREFIX, "event=" + event));
        if (clanwarId != null) parts.add("clanwar_id=" + clanwarId);
        if (demo != null) parts.add("demo=" + demo);
        parts.add("error=" + AuthLog.quote(error));
        LOG.warn(String.join(" ", parts));
    }
}
