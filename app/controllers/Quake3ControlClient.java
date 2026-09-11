package controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import play.api.Configuration;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Talks to the quake3ctl sidecar (deploy/quake3ctl.py), a small root-owned
 * process that is the only thing on the host allowed to start/stop the
 * Quake 3 docker-compose stacks. The dwc web process itself has no Docker
 * or systemd privileges; it only ever calls this fixed, token-authenticated
 * HTTP endpoint on localhost.
 */
@Singleton
public final class Quake3ControlClient {
    public enum Server {
        DUEL("duel", "Duel"), TDM("tdm", "TDM"), CTF("ctf", "CTF");

        public final String id;
        public final String label;

        Server(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static Server byId(String id) {
            for (Server server : values()) if (server.id.equals(id)) return server;
            return null;
        }
    }

    public record Status(Server server, String state, String error) {
        public boolean active() {
            return "active".equals(state);
        }
    }

    private final WSClient ws;
    private final String baseUrl;
    private final String token;

    @Inject
    Quake3ControlClient(WSClient ws, Configuration configuration) {
        this.ws = ws;
        this.baseUrl = configured(configuration, "quake3.control.baseUrl");
        this.token = configured(configuration, "quake3.control.token");
    }

    boolean configured() {
        return !baseUrl.isBlank() && !token.isBlank();
    }

    CompletionStage<List<Status>> statusAll() {
        List<CompletionStage<Status>> futures = List.of(
                status(Server.DUEL), status(Server.TDM), status(Server.CTF));
        return CompletableFuture.allOf(futures.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletionStage::toCompletableFuture)
                        .map(CompletableFuture::join).toList());
    }

    CompletionStage<Status> status(Server server) {
        return request("/status/" + server.id).get()
                .handle((response, error) -> toStatus(server, response, error));
    }

    CompletionStage<Status> start(Server server) {
        return request("/start/" + server.id).post("")
                .handle((response, error) -> toStatus(server, response, error));
    }

    CompletionStage<Status> stop(Server server) {
        return request("/stop/" + server.id).post("")
                .handle((response, error) -> toStatus(server, response, error));
    }

    private Status toStatus(Server server, WSResponse response, Throwable error) {
        if (error != null) return new Status(server, null, "Control service unreachable.");
        if (response.getStatus() != 200) return new Status(server, null, "Control service error: " + response.getBody().trim());
        return new Status(server, response.getBody().trim(), null);
    }

    private WSRequest request(String path) {
        return ws.url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + token)
                .setRequestTimeout(Duration.ofSeconds(65));
    }

    private static String configured(Configuration configuration, String path) {
        return configuration.underlying().hasPath(path) ? configuration.underlying().getString(path) : "";
    }
}
