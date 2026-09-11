package controllers;

import jakarta.inject.Inject;
import play.api.Configuration;
import play.filters.csrf.AddCSRFToken;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class AdminQuake3Controller extends Controller {
    private final Configuration configuration;
    private final Quake3ControlClient control;
    private final MessagesApi messagesApi;

    @Inject
    public AdminQuake3Controller(Configuration configuration, Quake3ControlClient control, MessagesApi messagesApi) {
        this.configuration = configuration;
        this.control = control;
        this.messagesApi = messagesApi;
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

    private Result requireAdmin(Http.Request request) {
        return AdminAuth.isAuthenticated(request, configuration) ? null : redirect(routes.AdminController.login());
    }
}
