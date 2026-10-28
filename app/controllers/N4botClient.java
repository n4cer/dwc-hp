package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.Configuration;
import play.cache.SyncCacheApi;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Reads the Quake 3 demo statistics of clan wars from n4bot's result service
 * (the replay/demo bot on the same host). n4bot links uploaded demos to the
 * clan wars here and serves them under /api/q3/clanwars; this site only reads.
 *
 * Failures are logged as one logfmt line starting with "dwc-n4bot" and never
 * break a page: without n4bot a clan war simply has no demo statistics.
 */
@Singleton
public final class N4botClient {
    static final String PREFIX = "dwc-n4bot";
    private static final Logger LOG = LoggerFactory.getLogger("n4bot");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FILE_TIMEOUT = Duration.ofSeconds(60);
    private static final String IDS_CACHE_KEY = "n4bot_clanwar_ids";
    private static final int IDS_CACHE_SECONDS = 300;
    // A short-lived empty entry while n4bot is down, so not every page waits for the timeout.
    private static final int IDS_FAILURE_CACHE_SECONDS = 60;

    private final WSClient ws;
    private final SyncCacheApi cache;
    private final String baseUrl;

    @Inject
    N4botClient(WSClient ws, SyncCacheApi cache, Configuration configuration) {
        this.ws = ws;
        this.cache = cache;
        String path = "n4bot.baseUrl";
        String url = configuration.underlying().hasPath(path) ? configuration.underlying().getString(path) : "";
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        this.baseUrl = url;
    }

    boolean configured() {
        return !baseUrl.isBlank();
    }

    /** Whether n4bot has demos for this clan war. Blocks at most a few seconds, then remembers the answer. */
    boolean hasStats(long clanwarId) {
        if (!configured()) return false;
        Optional<ArrayList<Long>> cached = cache.get(IDS_CACHE_KEY);
        if (cached.isPresent()) return cached.get().contains(clanwarId);

        ArrayList<Long> ids = new ArrayList<>();
        int duration = IDS_CACHE_SECONDS;
        try {
            var response = request("/api/q3/clanwars").get().toCompletableFuture()
                    .get(TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS);
            if (response.getStatus() == 200) {
                for (JsonNode id : response.asJson().path("clanwar_ids")) ids.add(id.asLong());
            } else {
                log("list_failed", null, "status " + response.getStatus());
                duration = IDS_FAILURE_CACHE_SECONDS;
            }
        } catch (Exception exception) {
            log("list_failed", null, exception.getClass().getSimpleName());
            duration = IDS_FAILURE_CACHE_SECONDS;
        }
        cache.set(IDS_CACHE_KEY, ids, duration);
        return ids.contains(clanwarId);
    }

    /** The demo statistics of one clan war; empty when there are none or n4bot is unreachable. */
    CompletionStage<Optional<JsonNode>> clanwar(long clanwarId) {
        if (!configured()) return CompletableFuture.completedFuture(Optional.empty());
        return request("/api/q3/clanwars/" + clanwarId).get().handle((response, error) -> {
            if (error != null) {
                log("fetch_failed", clanwarId, error.getClass().getSimpleName());
                return Optional.empty();
            }
            if (response.getStatus() == 404) return Optional.empty();
            if (response.getStatus() != 200) {
                log("fetch_failed", clanwarId, "status " + response.getStatus());
                return Optional.empty();
            }
            return Optional.of(response.asJson());
        });
    }

    /** The clan wars n4bot has demos for; empty when it is unreachable. Uncached, for the demo import. */
    CompletionStage<List<Long>> clanwarIds() {
        if (!configured()) return CompletableFuture.completedFuture(List.of());
        return request("/api/q3/clanwars").get().handle((response, error) -> {
            if (error != null || response.getStatus() != 200) {
                log("list_failed", null, error != null ? error.getClass().getSimpleName() : "status " + response.getStatus());
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode id : response.asJson().path("clanwar_ids")) ids.add(id.asLong());
            return ids;
        });
    }

    /** A demo file as n4bot's clan war answer links it (file_url); empty when it cannot be fetched. */
    CompletionStage<Optional<byte[]>> demoFile(long clanwarId, String fileUrl) {
        if (!configured() || fileUrl == null || !fileUrl.startsWith("/api/q3/results/")) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return ws.url(baseUrl + fileUrl).setRequestTimeout(FILE_TIMEOUT).get().handle((response, error) -> {
            if (error != null || response.getStatus() != 200) {
                log("demo_fetch_failed", clanwarId,
                        error != null ? error.getClass().getSimpleName() : "status " + response.getStatus());
                return Optional.empty();
            }
            return Optional.of(response.asByteArray());
        });
    }

    private WSRequest request(String path) {
        return ws.url(baseUrl + path).setRequestTimeout(TIMEOUT);
    }

    private static void log(String event, Long clanwarId, String error) {
        List<String> parts = new ArrayList<>(List.of(PREFIX, "event=" + event));
        if (clanwarId != null) parts.add("clanwar_id=" + clanwarId);
        parts.add("error=" + AuthLog.quote(error));
        LOG.warn(String.join(" ", parts));
    }
}
