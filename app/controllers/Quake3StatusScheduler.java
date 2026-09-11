package controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Cancellable;
import play.api.Configuration;
import play.inject.ApplicationLifecycle;
import scala.concurrent.ExecutionContext;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Periodically queries the Quake 3 servers' native UDP "getinfo" protocol
 * directly (not through the quake3ctl admin sidecar) so the public status
 * shown to every visitor reflects whether a player could actually connect,
 * including player counts, and doesn't depend on the admin-only control
 * path being up.
 */
@Singleton
final class Quake3StatusScheduler {
    private static final byte[] GETINFO = concat(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
            "getinfo".getBytes(StandardCharsets.US_ASCII));
    private static final int TIMEOUT_MILLIS = 750;

    private record ServerConfig(String id, String label, String host, int port) { }

    @Inject
    Quake3StatusScheduler(Configuration configuration, ActorSystem actorSystem, ApplicationLifecycle lifecycle) {
        String host = configured(configuration, "quake3.public.host");
        List<ServerConfig> servers = host.isBlank() ? List.of() : List.of(
                new ServerConfig("duel", "Duel", host, intConfigured(configuration, "quake3.public.duel.port")),
                new ServerConfig("tdm", "TDM", host, intConfigured(configuration, "quake3.public.tdm.port")),
                new ServerConfig("ctf", "CTF", host, intConfigured(configuration, "quake3.public.ctf.port")))
                .stream().filter(server -> server.port() > 0).toList();
        if (servers.isEmpty()) return;

        ExecutorService executor = Executors.newFixedThreadPool(servers.size(), runnable -> {
            Thread thread = new Thread(runnable, "quake3-status");
            thread.setDaemon(true);
            return thread;
        });
        Runnable refresh = () -> Quake3StatusBoard.set(servers.stream().map(this::query).toList());
        Cancellable schedule = actorSystem.scheduler().scheduleWithFixedDelay(Duration.ofSeconds(2),
                Duration.ofSeconds(20), refresh, ExecutionContext.fromExecutor(executor));

        lifecycle.addStopHook(() -> {
            schedule.cancel();
            executor.shutdownNow();
            return CompletableFuture.completedFuture(null);
        });
    }

    private Quake3StatusBoard.ServerStatus query(ServerConfig config) {
        String address = config.host() + ":" + config.port();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.send(new DatagramPacket(GETINFO, GETINFO.length, InetAddress.getByName(config.host()), config.port()));
            byte[] buffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            Map<String, String> info = parseInfoResponse(buffer, response.getLength());
            return new Quake3StatusBoard.ServerStatus(config.id(), config.label(), address, true,
                    parseInt(info.get("clients")), parseInt(info.get("sv_maxclients")));
        } catch (IOException e) {
            return new Quake3StatusBoard.ServerStatus(config.id(), config.label(), address, false, null, null);
        }
    }

    private static Map<String, String> parseInfoResponse(byte[] buffer, int length) {
        // Response is 0xFFFFFFFF + "infoResponse\n" + "\key\value\key\value...".
        String body = new String(buffer, 4, Math.max(0, length - 4), StandardCharsets.ISO_8859_1);
        int newline = body.indexOf('\n');
        String keyValues = newline >= 0 ? body.substring(newline + 1) : body;
        String[] parts = keyValues.split("\\\\", -1);
        Map<String, String> info = new LinkedHashMap<>();
        for (int i = 1; i + 1 < parts.length; i += 2) info.put(parts[i], parts[i + 1]);
        return info;
    }

    private static Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static String configured(Configuration configuration, String path) {
        return configuration.underlying().hasPath(path) ? configuration.underlying().getString(path) : "";
    }

    private static int intConfigured(Configuration configuration, String path) {
        return configuration.underlying().hasPath(path) ? configuration.underlying().getInt(path) : 0;
    }
}
