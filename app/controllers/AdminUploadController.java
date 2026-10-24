package controllers;

import jakarta.inject.Inject;
import models.Download;
import models.Role;
import models.User;
import play.filters.csrf.AddCSRFToken;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Files uploaded over SFTP wait in the server's upload folder until a member puts
 * them into place here: pk3 files for the Quake 3 servers (role Quake 3) and files
 * for the download section (role Media). The quake3ctl sidecar does the actual
 * moving, since the web process may write to neither folder.
 */
public class AdminUploadController extends Controller {
    /** Mirrors UPLOAD_NAME in deploy/quake3ctl.py, which enforces it. */
    static final Pattern UPLOAD_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    /** Mirrors SETTLE_SECONDS in deploy/quake3ctl.py: younger files are probably still being uploaded. */
    static final Duration SETTLE = Duration.ofSeconds(60);
    private static final Pattern ID_PAK = Pattern.compile("pak[0-8]\\.pk3", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Where an upload can go, as named by the sidecar. */
    public enum Target {
        BASEQ3("baseq3", "Quake 3 servers (baseq3)", Role.QUAKE3),
        OSP("osp", "Quake 3 servers (osp)", Role.QUAKE3),
        DOWNLOADS("downloads", "Download section", Role.MEDIA);

        public final String id;
        public final String label;
        final Role role;

        Target(String id, String label, Role role) {
            this.id = id;
            this.label = label;
            this.role = role;
        }

        boolean accepts(String name) {
            if (this == DOWNLOADS) return HomeController.isDownloadable(name);
            // Only the retail paks in baseq3 are id's; a mod may ship its own pak0.pk3.
            return name.toLowerCase(Locale.ROOT).endsWith(".pk3") && !(this == BASEQ3 && ID_PAK.matcher(name).matches());
        }

        static Target byId(String id) {
            return Arrays.stream(values()).filter(target -> target.id.equals(id)).findFirst().orElse(null);
        }
    }

    /** One upload as the page shows it, with the targets the signed-in member may send it to. */
    public record Row(String name, String size, String modified, boolean settling, List<String> replaces,
            List<Target> targets) { }

    private final Quake3ControlClient control;
    private final MessagesApi messagesApi;

    @Inject
    public AdminUploadController(Quake3ControlClient control, MessagesApi messagesApi) {
        this.control = control;
        this.messagesApi = messagesApi;
    }

    private Messages messages() {
        return messagesApi.preferred(List.of(Lang.forCode("de")));
    }

    @AddCSRFToken
    public CompletionStage<Result> index(Http.Request request) {
        Optional<User> member = AdminAuth.currentUser(request);
        Result denied = requireAnyTarget(request, member);
        if (denied != null) return CompletableFuture.completedFuture(denied);
        if (!control.configured()) {
            return CompletableFuture.completedFuture(ok(views.html.adminUploads.render(request, List.of(),
                    "Uploads are not available (missing quake3.control.token).", messages())));
        }
        return control.uploads().thenApply(uploads -> ok(views.html.adminUploads.render(request,
                uploads.uploads().stream().map(upload -> row(upload, member.get(), Instant.now())).toList(),
                uploads.error(), messages())));
    }

    public CompletionStage<Result> install(Http.Request request, String name) {
        Optional<User> member = AdminAuth.currentUser(request);
        Result denied = requireAnyTarget(request, member);
        if (denied != null) return CompletableFuture.completedFuture(denied);
        Map<String, String[]> data = request.body().asFormUrlEncoded();
        String[] values = data == null ? null : data.get("target");
        Target target = values == null || values.length == 0 ? null : Target.byId(values[0]);
        if (!UPLOAD_NAME.matcher(name).matches() || target == null || !target.accepts(name)) {
            return CompletableFuture.completedFuture(back().flashing("error", name + " cannot go there."));
        }
        if (!member.get().hasRole(target.role)) {
            return CompletableFuture.completedFuture(AdminAuth.forbidden(request, member.get(), target.role, messages()));
        }
        return control.installUpload(name, target.id).thenApply(error -> {
            if (error != null) return back().flashing("error", name + ": " + error);
            if (target != Target.DOWNLOADS) {
                return back().flashing("success", name + " is now in " + target.id
                        + ". Restart the Quake 3 servers so that they load it.");
            }
            Download entry = Download.find.query().where().eq("file", name).setMaxRows(1).findOne();
            if (entry == null) {
                return redirect(routes.AdminController.newDownload(name))
                        .flashing("success", name + " is now in the download folder. Describe it to offer it on the site.");
            }
            return back().flashing("success", name + " is now in the download folder; \"" + entry.getTitle()
                    + "\" offers the new file.");
        });
    }

    public CompletionStage<Result> discard(Http.Request request, String name) {
        Optional<User> member = AdminAuth.currentUser(request);
        Result denied = requireAnyTarget(request, member);
        if (denied != null) return CompletableFuture.completedFuture(denied);
        if (!UPLOAD_NAME.matcher(name).matches()) {
            return CompletableFuture.completedFuture(back().flashing("error", "Invalid file name."));
        }
        return control.discardUpload(name).thenApply(error -> error == null
                ? back().flashing("success", name + " deleted.")
                : back().flashing("error", name + ": " + error));
    }

    static Row row(Quake3ControlClient.Upload upload, User member, Instant now) {
        String name = upload.name();
        List<Target> targets = new ArrayList<>();
        if (UPLOAD_NAME.matcher(name).matches()) {
            for (Target target : Target.values()) {
                if (target.accepts(name) && member.hasRole(target.role)) targets.add(target);
            }
        }
        List<String> replaces = new ArrayList<>();
        upload.replaces().forEach((id, existing) -> replaces.add(id + ": " + HomeController.formatSize(existing.size())
                + " from " + TIMESTAMP.format(existing.modified())));
        return new Row(name, HomeController.formatSize(upload.size()), TIMESTAMP.format(upload.modified()),
                upload.modified().isAfter(now.minus(SETTLE)), replaces, targets);
    }

    private Result back() {
        return redirect(routes.AdminUploadController.index());
    }

    /** The page is for members who may send uploads somewhere; which targets they get is decided per file. */
    private Result requireAnyTarget(Http.Request request, Optional<User> member) {
        if (member.isEmpty()) return redirect(routes.AdminController.login());
        if (member.get().hasRole(Role.QUAKE3) || member.get().hasRole(Role.MEDIA)) return null;
        return AdminAuth.forbidden(request, member.get(), Role.MEDIA, messages());
    }
}
