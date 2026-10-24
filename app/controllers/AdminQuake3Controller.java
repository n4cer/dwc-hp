package controllers;

import jakarta.inject.Inject;
import models.Quake3Player;
import models.Quake3PlayerNick;
import models.Role;
import models.User;
import play.api.Configuration;
import play.cache.AsyncCacheApi;
import play.filters.csrf.AddCSRFToken;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class AdminQuake3Controller extends Controller {
    private static final List<String> PUBLIC_CACHE_LANGS = List.of("de", "en");

    private final Quake3ControlClient control;
    private final MessagesApi messagesApi;
    private final Configuration configuration;
    private final AsyncCacheApi cache;

    @Inject
    public AdminQuake3Controller(Quake3ControlClient control, MessagesApi messagesApi, Configuration configuration,
            AsyncCacheApi cache) {
        this.control = control;
        this.messagesApi = messagesApi;
        this.configuration = configuration;
        this.cache = cache;
    }

    private Messages messages() {
        return messagesApi.preferred(List.of(Lang.forCode("de")));
    }

    @AddCSRFToken
    public CompletionStage<Result> index(Http.Request request) {
        Result denied = requireAdmin(request);
        if (denied != null) return CompletableFuture.completedFuture(denied);
        if (!control.configured()) {
            return CompletableFuture.completedFuture(ok(views.html.adminQuake3.render(request, null,
                    "Quake 3 control is not configured (missing quake3.control.token).", messages())));
        }
        return control.statusAll().thenApply(statuses ->
                ok(views.html.adminQuake3.render(request, statuses, null, messages())));
    }

    public CompletionStage<Result> start(Http.Request request, String name) {
        return act(request, name, control::start);
    }

    public CompletionStage<Result> stop(Http.Request request, String name) {
        return act(request, name, control::stop);
    }

    private CompletionStage<Result> act(Http.Request request, String name,
            Function<Quake3ControlClient.Server, CompletionStage<Quake3ControlClient.Status>> action) {
        Result denied = requireAdmin(request);
        if (denied != null) return CompletableFuture.completedFuture(denied);
        Quake3ControlClient.Server server = Quake3ControlClient.Server.byId(name);
        if (server == null) return CompletableFuture.completedFuture(notFound("Unknown server."));
        return action.apply(server).thenApply(status -> {
            Result redirect = redirect(routes.AdminQuake3Controller.index());
            return status.error() == null
                    ? redirect.flashing("success", server.label + " server updated.")
                    : redirect.flashing("error", server.label + ": " + status.error());
        });
    }

    /**
     * The players the statistics merge, and the nicks from the logs that belong to
     * nobody yet, so that an admin sees what there is to assign.
     */
    @AddCSRFToken
    public Result players(Http.Request request) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        List<Quake3Player> players = Quake3Player.find.query().fetch("member").fetch("nicks").findList().stream()
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName())).toList();
        Map<String, String> assigned = owners(players, null);
        Map<String, Integer> unassigned = new LinkedHashMap<>();
        Quake3GamesLog.readConfigured(configuration.underlying()).ifPresent(matches -> {
            Map<String, Integer> counts = new HashMap<>();
            Map<String, String> spelling = new HashMap<>();
            for (Quake3GamesLog.Match match : matches) {
                for (Quake3GamesLog.MatchPlayer player : match.players()) {
                    String key = player.name().toLowerCase();
                    if (player.bot() || assigned.containsKey(key)) continue;
                    counts.merge(key, 1, Integer::sum);
                    spelling.putIfAbsent(key, player.name());
                }
            }
            counts.keySet().stream().sorted().forEach(key -> unassigned.put(spelling.get(key), counts.get(key)));
        });
        return ok(views.html.adminQuake3Players.render(request, players, unassigned, messages()));
    }

    @AddCSRFToken
    public Result newPlayer(Http.Request request, String nick) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        return ok(playerForm(request, null, "", null, Quake3GamesLog.stripColors(nick), null));
    }

    @AddCSRFToken
    public Result editPlayer(Http.Request request, Long id) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        Quake3Player player = Quake3Player.find.byId(id);
        if (player == null) return notFound("Player not found.");
        return ok(playerForm(request, player, player.getName() == null ? "" : player.getName(),
                player.getMember() == null ? null : player.getMember().getId(),
                String.join("\n", player.nickNames()), null));
    }

    public Result createPlayer(Http.Request request) {
        return savePlayer(request, new Quake3Player());
    }

    public Result updatePlayer(Http.Request request, Long id) {
        Quake3Player player = Quake3Player.find.byId(id);
        if (player == null) return notFound("Player not found.");
        return savePlayer(request, player);
    }

    public Result deletePlayer(Http.Request request, Long id) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        Quake3Player player = Quake3Player.find.byId(id);
        if (player == null) return notFound("Player not found.");
        String name = player.displayName();
        player.delete();
        clearStatsCaches();
        return redirect(routes.AdminQuake3Controller.players()).flashing("success", name + " removed.");
    }

    private Result savePlayer(Http.Request request, Quake3Player player) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        Map<String, String[]> data = request.body().asFormUrlEncoded();
        if (data == null) data = Map.of();
        String name = value(data, "name");
        String memberValue = value(data, "member");
        String nicksValue = value(data, "nicks");
        Long memberId = parseId(memberValue);
        User member = memberId == null ? null : User.find.byId(memberId);
        Quake3Player existing = player.getId() == null ? null : player;

        String error = null;
        if (!memberValue.isBlank() && member == null) {
            error = "Unknown member.";
        } else if (member == null && Quake3GamesLog.stripColors(name).isEmpty()) {
            error = "Please choose a member or enter a name.";
        } else if (member != null && Quake3Player.find.query().where().eq("member.id", member.getId())
                .ne("id", player.getId() == null ? -1L : player.getId()).exists()) {
            error = member.getNick() + " already has an entry.";
        }
        List<String> nicks = parseNicks(nicksValue);
        if (error == null) {
            String shown = member != null ? member.getNick() : Quake3GamesLog.stripColors(name);
            List<Quake3Player> others = Quake3Player.find.query().fetch("member").fetch("nicks").findList();
            Map<String, String> taken = owners(others, player.getId());
            List<String> all = new ArrayList<>(nicks);
            all.add(shown);
            for (String nick : all) {
                String owner = taken.get(nick.toLowerCase());
                if (owner != null) {
                    error = "\"" + nick + "\" already counts as " + owner + ".";
                    break;
                }
            }
        }
        if (error != null) {
            return badRequest(playerForm(request, existing, name, memberId, nicksValue, error));
        }

        player.setName(name.isBlank() ? null : name);
        player.setMember(member);
        if (player.getId() == null) {
            player.setNicks(new ArrayList<>());
            player.save();
        } else {
            player.update();
            Quake3PlayerNick.find.query().where().eq("player.id", player.getId()).delete();
        }
        for (String nick : nicks) {
            Quake3PlayerNick entry = new Quake3PlayerNick();
            entry.setPlayer(player);
            entry.setNick(nick);
            entry.save();
        }
        clearStatsCaches();
        return redirect(routes.AdminQuake3Controller.players()).flashing("success", "Player saved successfully.");
    }

    /** One nick per line, without colour codes, each once regardless of case. */
    static List<String> parseNicks(String value) {
        Map<String, String> nicks = new LinkedHashMap<>();
        for (String line : value.split("\\R")) {
            String nick = Quake3GamesLog.stripColors(line);
            if (!nick.isEmpty()) nicks.putIfAbsent(nick.toLowerCase(), nick);
        }
        return List.copyOf(nicks.values());
    }

    /** Lower-case nick or shown name -> the player it counts as, for every player but skipId. */
    private static Map<String, String> owners(List<Quake3Player> players, Long skipId) {
        Map<String, String> owners = new HashMap<>();
        for (Quake3Player player : players) {
            if (player.getId().equals(skipId)) continue;
            String shown = player.displayName();
            owners.put(Quake3GamesLog.stripColors(shown).toLowerCase(), shown);
            for (String nick : player.nickNames()) owners.put(nick.toLowerCase(), shown);
        }
        return owners;
    }

    private play.twirl.api.Html playerForm(Http.Request request, Quake3Player player, String name, Long memberId,
            String nicks, String error) {
        List<User> members = User.find.query().orderBy().asc("nick").findList();
        return views.html.adminQuake3PlayerForm.render(request, player, name, memberId, nicks, members, error,
                messages());
    }

    private void clearStatsCaches() {
        for (String key : HomeController.quake3StatsCacheKeys(PUBLIC_CACHE_LANGS)) cache.remove(key);
    }

    private static String value(Map<String, String[]> data, String key) {
        String[] values = data.get(key);
        return values == null || values.length == 0 || values[0] == null ? "" : values[0].trim();
    }

    private static Long parseId(String value) {
        try { return Long.valueOf(value); } catch (NumberFormatException ex) { return null; }
    }

    private Result requireAdmin(Http.Request request) {
        return AdminAuth.require(request, Role.QUAKE3, messages());
    }
}
