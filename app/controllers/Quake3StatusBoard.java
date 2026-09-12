package controllers;

import java.util.List;

/**
 * Latest known public status of the Quake 3 servers, refreshed periodically
 * by {@link Quake3StatusScheduler} and read by the site-wide layout
 * (main.scala.html) on every page render. Plain static state on purpose:
 * the layout template has no dependency injection, so this is the simplest
 * way to make the data available everywhere without threading a parameter
 * through every view.
 */
public final class Quake3StatusBoard {
    public record ServerStatus(String id, String label, String address, boolean online, Integer players, Integer maxPlayers, String map) { }

    private static volatile List<ServerStatus> current = List.of();

    private Quake3StatusBoard() { }

    public static List<ServerStatus> current() {
        return current;
    }

    static void set(List<ServerStatus> value) {
        current = value;
    }
}
