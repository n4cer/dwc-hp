package controllers;

import jakarta.inject.Inject;
import models.Clanwar;
import models.ContentSanitizer;
import models.Country;
import models.Download;
import models.Game;
import models.GameType;
import models.League;
import models.MatchLineup;
import models.News;
import models.PasswordToken;
import models.Role;
import models.Score;
import models.ScoreImage;
import models.Squad;
import models.User;
import models.UserRole;
import models.UserSquad;
import models.Video;
import play.api.Configuration;
import play.cache.AsyncCacheApi;
import play.filters.csrf.AddCSRFToken;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminController extends Controller {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String LOGIN_FAILED = "Invalid nick or password.";
    private static final String RATE_LIMITED = "Too many failed attempts. Please try again later.";

    // The admin area is only used by clan staff and is intentionally kept German-only,
    // regardless of the visiting browser's language preference.
    private static final List<String> PUBLIC_CACHE_LANGS = List.of("de", "en");

    private final Configuration configuration;
    private final AsyncCacheApi cache;
    private final LoginThrottle throttle;
    private final MessagesApi messagesApi;

    @Inject
    public AdminController(Configuration configuration, AsyncCacheApi cache, LoginThrottle throttle,
            MessagesApi messagesApi) {
        this.configuration = configuration;
        this.cache = cache;
        this.throttle = throttle;
        this.messagesApi = messagesApi;
    }

    private Messages messages() {
        return messagesApi.preferred(List.of(Lang.forCode("de")));
    }

    @AddCSRFToken
    public Result login(Http.Request request) {
        if (AdminAuth.currentUser(request).isPresent()) return redirect(routes.AdminController.index());
        return ok(views.html.adminLogin.render(request, null, messages()));
    }

    // Re-renders the form on failure. Requests without cookies skip the CSRF check and
    // arrive without a token (bots usually send none), so one has to be added here.
    @AddCSRFToken
    public Result authenticate(Http.Request request) {
        String client = request.remoteAddress();
        Map<String, String[]> data = form(request);
        String nick = value(data, "nick");
        String password = rawValue(data, "password");

        LoginRateLimiter.LimitStatus limit = throttle.clientStatus(client);
        String limitedBy = "client";
        if (!limit.blocked()) {
            limit = throttle.nickStatus(nick);
            limitedBy = "nick";
        }
        if (limit.blocked()) {
            AuthLog.event(AuthLog.Event.LOGIN_RATE_LIMITED).reason(limitedBy).nick(nick).request(request).log();
            return rateLimited(views.html.adminLogin.render(request, RATE_LIMITED, messages()), limit);
        }

        // Every branch runs exactly one BCrypt check, so the response time does not
        // reveal whether the nick exists, has a password or is active.
        User member = User.findByNick(nick);
        String reason = null;
        if (member == null) {
            Passwords.verifyDummy(password);
            reason = "unknown_user";
        } else if (!member.hasPassword()) {
            Passwords.verifyDummy(password);
            reason = "no_password";
        } else if (!Passwords.verify(password, member.getPasswordHash())) {
            reason = "bad_password";
        } else if (!Boolean.TRUE.equals(member.getActive())) {
            reason = "inactive";
        }
        if (reason != null) {
            limit = throttle.recordFailure(client, nick);
            AuthLog.Entry entry = AuthLog.event(AuthLog.Event.LOGIN_FAILED).reason(reason);
            (member == null ? entry.nick(nick) : entry.member(member)).request(request).log();
            if (limit.blocked()) return rateLimited(views.html.adminLogin.render(request, RATE_LIMITED, messages()), limit);
            return unauthorized(views.html.adminLogin.render(request, LOGIN_FAILED, messages()));
        }

        throttle.recordSuccess(client, nick);
        member.setLastLoginAt(new Date());
        member.update();
        AuthLog.event(AuthLog.Event.LOGIN_SUCCESS).member(member).request(request).log();
        return AdminAuth.signIn(redirect(routes.AdminController.index()), member);
    }

    private static Result rateLimited(play.twirl.api.Html page, LoginRateLimiter.LimitStatus limit) {
        return status(TOO_MANY_REQUESTS, page).withHeader("Retry-After", Long.toString(limit.retryAfterSeconds()));
    }

    public Result logout(Http.Request request) {
        AdminAuth.currentUser(request).ifPresent(member ->
                AuthLog.event(AuthLog.Event.LOGOUT).member(member).request(request).log());
        return redirect(routes.AdminController.login()).withNewSession();
    }

    @AddCSRFToken
    public Result profile(Http.Request request) {
        Result denied = require(request, null);
        if (denied != null) return denied;
        return ok(profilePage(request, AdminAuth.currentUser(request).orElseThrow(), null));
    }

    /** Every member may maintain their own basic profile, nothing else about their entry. */
    public Result updateProfile(Http.Request request) {
        Result denied = require(request, null);
        if (denied != null) return denied;
        User member = AdminAuth.currentUser(request).orElseThrow();
        Map<String, String[]> data = form(request);
        String email = value(data, "email");
        Date birthDate = parseDay(value(data, "birthDate"));
        if (!value(data, "birthDate").isBlank() && birthDate == null) {
            return badRequest(profilePage(request, member, "Please enter a valid birth date."));
        }
        User sameEmail = email.isBlank() ? null : User.find.query().where().eq("email", email).findOne();
        if (sameEmail != null && !sameEmail.getId().equals(member.getId())) {
            return badRequest(profilePage(request, member, "This email address is already used by another member."));
        }
        member.setRealname(value(data, "realname"));
        member.setEmail(email.isBlank() ? null : email);
        member.setBirthDate(birthDate);
        member.setCity(value(data, "city"));
        member.setJob(value(data, "job"));
        member.setQuote(value(data, "quote"));
        member.update();
        clearPublicCaches();
        return redirect(routes.AdminController.profile()).flashing("success", "Profile saved successfully.");
    }

    public Result changePassword(Http.Request request) {
        Result denied = require(request, null);
        if (denied != null) return denied;
        User member = AdminAuth.currentUser(request).orElseThrow();
        String client = request.remoteAddress();
        LoginRateLimiter.LimitStatus limit = throttle.clientStatus(client);
        if (!limit.blocked()) limit = throttle.nickStatus(member.getNick());
        if (limit.blocked()) {
            AuthLog.event(AuthLog.Event.LOGIN_RATE_LIMITED).reason("password_change").member(member).request(request).log();
            return rateLimited(profilePage(request, member, RATE_LIMITED), limit);
        }

        Map<String, String[]> data = form(request);
        // A hijacked session alone must not be enough to take over the account.
        if (!Passwords.verify(rawValue(data, "currentPassword"), member.getPasswordHash())) {
            limit = throttle.recordFailure(client, member.getNick());
            AuthLog.event(AuthLog.Event.PASSWORD_CHANGE_FAILED).reason("bad_current_password")
                    .member(member).request(request).log();
            if (limit.blocked()) return rateLimited(profilePage(request, member, RATE_LIMITED), limit);
            return badRequest(profilePage(request, member, "The current password is wrong."));
        }
        String password = rawValue(data, "password");
        String error = Passwords.validate(password, rawValue(data, "passwordConfirmation"), member.getNick());
        if (error != null) return badRequest(profilePage(request, member, error));

        setPassword(member, password);
        AuthLog.event(AuthLog.Event.PASSWORD_CHANGED).member(member).actor(member).request(request).log();
        // The new password version ends every other session; this one continues.
        return AdminAuth.signIn(redirect(routes.AdminController.profile())
                .flashing("success", "Password changed. All other sessions have been signed out."), member);
    }

    private play.twirl.api.Html profilePage(Http.Request request, User member, String error) {
        return views.html.adminProfile.render(request, member, error, messages());
    }

    /** Shows the link once; afterwards only its hash exists. */
    public Result createPasswordLink(Http.Request request, Long id) {
        Result denied = require(request, Role.ADMIN);
        if (denied != null) return denied;
        User actor = AdminAuth.currentUser(request).orElseThrow();
        User member = User.find.byId(id);
        if (member == null) return notFound("Lineup member not found.");
        if (!Boolean.TRUE.equals(member.getActive())) {
            return badRequest(lineupForm(request, member, "Inactive members cannot sign in, so they get no password link."));
        }
        PasswordToken.find.query().where().eq("member.id", member.getId()).delete();
        String token = Passwords.newToken();
        PasswordToken entry = new PasswordToken();
        entry.setMember(member);
        entry.setCreatedBy(actor);
        entry.setTokenHash(Passwords.tokenHash(token));
        entry.setExpiresAt(Date.from(Instant.now().plus(passwordTokenValidity())));
        entry.save();
        AuthLog.event(AuthLog.Event.PASSWORD_LINK_CREATED).member(member).actor(actor).request(request).log();
        String link = siteBaseUrl(request) + routes.AdminController.passwordTokenForm(token).url();
        return ok(lineupForm(request, member, null, link)).withHeader(CACHE_CONTROL, "no-store");
    }

    /**
     * Opening the link changes nothing, so chat link previews (Discord fetches every
     * URL it sees) cannot use it up. Only the POST consumes the token.
     */
    @AddCSRFToken
    public Result passwordTokenForm(Http.Request request, String token) {
        LoginRateLimiter.LimitStatus limit = throttle.clientStatus(request.remoteAddress());
        if (limit.blocked()) return tokenPage(rateLimited(setPasswordPage(request, token, null, RATE_LIMITED), limit));
        PasswordToken entry = validToken(token);
        if (entry == null) return tokenPage(notFound(setPasswordPage(request, token, null, null)));
        return tokenPage(ok(setPasswordPage(request, token, entry.getMember(), null)));
    }

    @AddCSRFToken
    public Result redeemPasswordToken(Http.Request request, String token) {
        String client = request.remoteAddress();
        LoginRateLimiter.LimitStatus limit = throttle.clientStatus(client);
        if (limit.blocked()) {
            AuthLog.event(AuthLog.Event.PASSWORD_LINK_REJECTED).reason("rate_limited").request(request).log();
            return tokenPage(rateLimited(setPasswordPage(request, token, null, RATE_LIMITED), limit));
        }
        PasswordToken entry = PasswordToken.find.query().where().eq("tokenHash", Passwords.tokenHash(token)).findOne();
        String reason = entry == null ? "unknown_token"
                : entry.isExpired() ? "expired"
                : !Boolean.TRUE.equals(entry.getMember().getActive()) ? "inactive" : null;
        if (reason != null) {
            limit = throttle.recordFailure(client, null);
            AuthLog.Entry log = AuthLog.event(AuthLog.Event.PASSWORD_LINK_REJECTED).reason(reason);
            (entry == null ? log : log.member(entry.getMember())).request(request).log();
            if (entry != null) entry.delete();
            if (limit.blocked()) return tokenPage(rateLimited(setPasswordPage(request, token, null, RATE_LIMITED), limit));
            return tokenPage(notFound(setPasswordPage(request, token, null, null)));
        }

        User member = entry.getMember();
        Map<String, String[]> data = form(request);
        String password = rawValue(data, "password");
        String error = Passwords.validate(password, rawValue(data, "passwordConfirmation"), member.getNick());
        if (error != null) return tokenPage(badRequest(setPasswordPage(request, token, member, error)));

        setPassword(member, password);
        member.setLastLoginAt(new Date());
        member.update();
        AuthLog.event(AuthLog.Event.PASSWORD_LINK_REDEEMED).member(member).request(request).log();
        return AdminAuth.signIn(redirect(routes.AdminController.index())
                .flashing("success", "Your password has been set. Welcome!"), member);
    }

    private static PasswordToken validToken(String token) {
        PasswordToken entry = PasswordToken.find.query().where().eq("tokenHash", Passwords.tokenHash(token)).findOne();
        if (entry == null || entry.isExpired() || !Boolean.TRUE.equals(entry.getMember().getActive())) return null;
        return entry;
    }

    private play.twirl.api.Html setPasswordPage(Http.Request request, String token, User member, String error) {
        return views.html.setPassword.render(request, token, member, error, messages());
    }

    /** The token sits in the URL: keep it out of caches and out of Referer headers. */
    private static Result tokenPage(Result result) {
        return result.withHeader(CACHE_CONTROL, "no-store").withHeader("Referrer-Policy", "no-referrer");
    }

    private static void setPassword(User member, String password) {
        member.setPasswordHash(Passwords.hash(password));
        member.setPasswordChangedAt(new Date());
        member.update();
        PasswordToken.find.query().where().eq("member.id", member.getId()).delete();
    }

    private Duration passwordTokenValidity() {
        String path = "auth.passwordToken.validity";
        return configuration.underlying().hasPath(path)
                ? configuration.underlying().getDuration(path) : Duration.ofHours(48);
    }

    private String siteBaseUrl(Http.Request request) {
        String configured = configured("site.baseUrl");
        if (!configured.isBlank()) return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        return (request.secure() ? "https://" : "http://") + request.host();
    }

    @AddCSRFToken
    public Result index(Http.Request request) {
        Result denied = require(request, null);
        if (denied != null) return denied;
        User member = AdminAuth.currentUser(request).orElseThrow();
        long newsCount = News.find.query().findCount();
        long clanwarCount = Clanwar.find.query().findCount();
        long lineupCount = User.find.query().findCount();
        long squadCount = Squad.find.query().findCount();
        long mapCount = models.Map.find.query().findCount();
        long leagueCount = League.find.query().findCount();
        long gameCount = Game.find.query().findCount();
        long gameTypeCount = GameType.find.query().findCount();
        long videoCount = Video.find.query().findCount();
        long downloadCount = Download.find.query().findCount();
        return ok(views.html.adminIndex.render(request, member, newsCount, clanwarCount, lineupCount, squadCount,
                mapCount, leagueCount, gameCount, gameTypeCount, videoCount, downloadCount, messages()));
    }

    @AddCSRFToken
    public Result newsIndex(Http.Request request) {
        Result denied = require(request, Role.NEWS);
        if (denied != null) return denied;
        List<News> news = News.find.query().orderBy().desc("timestamp").findList();
        return ok(views.html.adminNewsList.render(request, news, messages()));
    }

    @AddCSRFToken
    public Result clanwarIndex(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        List<Clanwar> clanwars = Clanwar.find.query().orderBy().desc("date").findList();
        return ok(views.html.adminClanwarList.render(request, clanwars, messages()));
    }

    @AddCSRFToken
    public Result lineupIndex(Http.Request request) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        List<User> lineup = User.find.query().orderBy().asc("nick").findList();
        return ok(views.html.adminLineupList.render(request, lineup, messages()));
    }

    @AddCSRFToken
    public Result squadIndex(Http.Request request) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        List<Squad> squads = Squad.find.query().orderBy().asc("description").findList();
        return ok(views.html.adminSquadList.render(request, squads, messages()));
    }

    @AddCSRFToken
    public Result newLineupMember(Http.Request request) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        return ok(lineupForm(request, null, null));
    }

    @AddCSRFToken
    public Result editLineupMember(Http.Request request, Long id) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        User member = User.find.byId(id);
        if (member == null) return notFound("Lineup member not found.");
        Result protectedAccount = protectAdminAccount(request, member);
        if (protectedAccount != null) return protectedAccount;
        return ok(lineupForm(request, member, null));
    }

    public Result createLineupMember(Http.Request request) {
        return saveLineupMember(request, new User());
    }

    public Result updateLineupMember(Http.Request request, Long id) {
        User member = User.find.byId(id);
        if (member == null) return notFound("Lineup member not found.");
        return saveLineupMember(request, member);
    }

    @AddCSRFToken
    public Result newNews(Http.Request request) {
        Result denied = require(request, Role.NEWS);
        if (denied != null) return denied;
        return ok(newsForm(request, null, null));
    }

    @AddCSRFToken
    public Result editNews(Http.Request request, Long id) {
        Result denied = require(request, Role.NEWS);
        if (denied != null) return denied;
        News news = News.find.byId(id);
        if (news == null) return notFound("News item not found.");
        return ok(newsForm(request, news, null));
    }

    public Result createNews(Http.Request request) {
        return saveNews(request, new News());
    }

    public Result updateNews(Http.Request request, Long id) {
        News news = News.find.byId(id);
        if (news == null) return notFound("News item not found.");
        return saveNews(request, news);
    }

    @AddCSRFToken
    public Result newClanwar(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        return ok(clanwarForm(request, null, null));
    }

    @AddCSRFToken
    public Result editClanwar(Http.Request request, Long id) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Clanwar clanwar = Clanwar.find.byId(id);
        if (clanwar == null) return notFound("Clanwar not found.");
        return ok(clanwarForm(request, clanwar, null));
    }

    public Result createClanwar(Http.Request request) {
        return saveClanwar(request, new Clanwar());
    }

    public Result updateClanwar(Http.Request request, Long id) {
        Clanwar clanwar = Clanwar.find.byId(id);
        if (clanwar == null) return notFound("Clanwar not found.");
        return saveClanwar(request, clanwar);
    }

    @AddCSRFToken
    public Result newSquad(Http.Request request) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        return ok(squadForm(request, null, null));
    }

    @AddCSRFToken
    public Result editSquad(Http.Request request, Long id) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        Squad squad = Squad.find.byId(id);
        if (squad == null) return notFound("Squad not found.");
        return ok(squadForm(request, squad, null));
    }

    public Result createSquad(Http.Request request) {
        return saveSquad(request, new Squad());
    }

    public Result updateSquad(Http.Request request, Long id) {
        Squad squad = Squad.find.byId(id);
        if (squad == null) return notFound("Squad not found.");
        return saveSquad(request, squad);
    }

    private Result saveSquad(Http.Request request, Squad squad) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String description = value(data, "description");
        Integer game = optionalInteger(value(data, "game"));
        if (description.isBlank() || (!value(data, "game").isBlank() && game == null)) {
            return badRequest(squadForm(request, squad.getId() == null ? null : squad,
                    "Please complete all required fields correctly."));
        }
        squad.setDescription(description);
        squad.setShortText(value(data, "shortText"));
        squad.setGame(game);
        if (squad.getId() == null) squad.save(); else squad.update();
        clearPublicCaches();
        return redirect(routes.AdminController.squadIndex()).flashing("success", "Squad saved successfully.");
    }

    private play.twirl.api.Html squadForm(Http.Request request, Squad squad, String error) {
        return views.html.adminSquadForm.render(request, squad, Game.find.all(), error, messages());
    }

    @AddCSRFToken
    public Result videoIndex(Http.Request request) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        List<Video> videos = Video.find.query().orderBy("sortOrder asc, id asc").findList();
        return ok(views.html.adminVideoList.render(request, videos, messages()));
    }

    @AddCSRFToken
    public Result newVideo(Http.Request request) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        return ok(videoForm(request, null, null));
    }

    @AddCSRFToken
    public Result editVideo(Http.Request request, Long id) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        Video video = Video.find.byId(id);
        if (video == null) return notFound("Video not found.");
        return ok(videoForm(request, video, null));
    }

    public Result createVideo(Http.Request request) {
        return saveVideo(request, new Video());
    }

    public Result updateVideo(Http.Request request, Long id) {
        Video video = Video.find.byId(id);
        if (video == null) return notFound("Video not found.");
        return saveVideo(request, video);
    }

    private Result saveVideo(Http.Request request, Video video) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String title = value(data, "title");
        String file = value(data, "file");
        Integer releaseYear = optionalInteger(value(data, "releaseYear"));
        Integer durationSeconds = optionalInteger(value(data, "durationSeconds"));
        Integer sortOrder = optionalInteger(value(data, "sortOrder"));

        if (title.isBlank() || file.isBlank()
                || (!value(data, "releaseYear").isBlank() && releaseYear == null)
                || (!value(data, "durationSeconds").isBlank() && durationSeconds == null)
                || (!value(data, "sortOrder").isBlank() && sortOrder == null)) {
            return badRequest(videoForm(request, video.getId() == null ? null : video,
                    "Please complete all required fields correctly."));
        }

        video.setTitle(title);
        video.setDescription(ContentSanitizer.sanitizeHtml(value(data, "description")));
        video.setReleaseYear(releaseYear);
        video.setFile(file);
        video.setPoster(emptyToNull(value(data, "poster")));
        video.setOriginalFile(emptyToNull(value(data, "originalFile")));
        video.setDurationSeconds(durationSeconds);
        video.setSortOrder(sortOrder == null ? 0 : sortOrder);
        video.setPublished(!value(data, "published").isBlank());
        video.setAuthor(find(User.find, value(data, "authorId")));
        if (video.getId() == null) video.save(); else video.update();
        clearPublicCaches();
        clearVideoCaches(video);
        return redirect(routes.AdminController.videoIndex()).flashing("success", "Video saved successfully.");
    }

    private play.twirl.api.Html videoForm(Http.Request request, Video video, String error) {
        return views.html.adminVideoForm.render(request, video, User.find.all(), error, messages());
    }

    private void clearVideoCaches(Video video) {
        for (String lang : PUBLIC_CACHE_LANGS) {
            cache.remove(HomeController.videoCacheKey(video.getId(), lang));
        }
    }

    @AddCSRFToken
    public Result downloadIndex(Http.Request request) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        List<Download> downloads = Download.find.query().orderBy("sortOrder asc, id asc").findList();
        for (Download download : downloads) download.setSizeLabel(HomeController.fileSizeLabel(configuration, download.getFile()));
        return ok(views.html.adminDownloadList.render(request, downloads, messages()));
    }

    @AddCSRFToken
    public Result newDownload(Http.Request request, String file) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        // Prefilled when coming from a file just put into the download folder (AdminUploadController).
        Download prefilled = null;
        if (!file.isBlank()) {
            prefilled = new Download();
            prefilled.setFile(file);
        }
        return ok(downloadForm(request, prefilled, null));
    }

    @AddCSRFToken
    public Result editDownload(Http.Request request, Long id) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        Download download = Download.find.byId(id);
        if (download == null) return notFound("Download not found.");
        return ok(downloadForm(request, download, null));
    }

    public Result createDownload(Http.Request request) {
        return saveDownload(request, new Download());
    }

    public Result updateDownload(Http.Request request, Long id) {
        Download download = Download.find.byId(id);
        if (download == null) return notFound("Download not found.");
        return saveDownload(request, download);
    }

    private Result saveDownload(Http.Request request, Download download) {
        Result denied = require(request, Role.MEDIA);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String title = value(data, "title");
        String file = value(data, "file");
        Integer sortOrder = optionalInteger(value(data, "sortOrder"));

        if (title.isBlank() || file.isBlank()
                || (!value(data, "sortOrder").isBlank() && sortOrder == null)) {
            return badRequest(downloadForm(request, download.getId() == null ? null : download,
                    "Please complete all required fields correctly."));
        }

        download.setTitle(title);
        download.setDescription(ContentSanitizer.sanitizeHtml(value(data, "description")));
        download.setCategory(emptyToNull(value(data, "category")));
        download.setFile(file);
        download.setSortOrder(sortOrder == null ? 0 : sortOrder);
        download.setPublished(!value(data, "published").isBlank());
        if (download.getId() == null) download.save(); else download.update();
        clearPublicCaches();
        return redirect(routes.AdminController.downloadIndex()).flashing("success", "Download saved successfully.");
    }

    private play.twirl.api.Html downloadForm(Http.Request request, Download download, String error) {
        return views.html.adminDownloadForm.render(request, download, error, messages());
    }

    @AddCSRFToken
    public Result mapIndex(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        List<models.Map> maps = models.Map.find.query().orderBy().asc("map").findList();
        return ok(views.html.adminMapList.render(request, maps, messages()));
    }

    @AddCSRFToken
    public Result newMap(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        return ok(mapForm(request, null, null));
    }

    @AddCSRFToken
    public Result editMap(Http.Request request, Long id) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        models.Map map = models.Map.find.byId(id);
        if (map == null) return notFound("Map not found.");
        return ok(mapForm(request, map, null));
    }

    public Result createMap(Http.Request request) {
        return saveMap(request, new models.Map());
    }

    public Result updateMap(Http.Request request, Long id) {
        models.Map map = models.Map.find.byId(id);
        if (map == null) return notFound("Map not found.");
        return saveMap(request, map);
    }

    private Result saveMap(Http.Request request, models.Map map) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String mapName = value(data, "map");
        Game game = find(Game.find, value(data, "game"));
        if (mapName.isBlank() || (!value(data, "game").isBlank() && game == null)) {
            return badRequest(mapForm(request, map.getId() == null ? null : map,
                    "Please complete all required fields correctly."));
        }
        map.setMap(mapName);
        map.setGame(game);
        if (map.getId() == null) map.save(); else map.update();
        clearPublicCaches();
        return redirect(routes.AdminController.mapIndex()).flashing("success", "Map saved successfully.");
    }

    private play.twirl.api.Html mapForm(Http.Request request, models.Map map, String error) {
        return views.html.adminMapForm.render(request, map, Game.find.all(), error, messages());
    }

    @AddCSRFToken
    public Result leagueIndex(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        List<League> leagues = League.find.query().orderBy().asc("league").findList();
        return ok(views.html.adminLeagueList.render(request, leagues, messages()));
    }

    @AddCSRFToken
    public Result newLeague(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        return ok(leagueForm(request, null, null));
    }

    @AddCSRFToken
    public Result editLeague(Http.Request request, Long id) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        League league = League.find.byId(id);
        if (league == null) return notFound("League not found.");
        return ok(leagueForm(request, league, null));
    }

    public Result createLeague(Http.Request request) {
        return saveLeague(request, new League());
    }

    public Result updateLeague(Http.Request request, Long id) {
        League league = League.find.byId(id);
        if (league == null) return notFound("League not found.");
        return saveLeague(request, league);
    }

    private Result saveLeague(Http.Request request, League league) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String leagueName = value(data, "league");
        if (leagueName.isBlank()) {
            return badRequest(leagueForm(request, league.getId() == null ? null : league,
                    "Please complete all required fields correctly."));
        }
        league.setLeague(leagueName);
        if (league.getId() == null) league.save(); else league.update();
        clearPublicCaches();
        return redirect(routes.AdminController.leagueIndex()).flashing("success", "League saved successfully.");
    }

    private play.twirl.api.Html leagueForm(Http.Request request, League league, String error) {
        return views.html.adminLeagueForm.render(request, league, error, messages());
    }

    @AddCSRFToken
    public Result gameIndex(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        List<Game> games = Game.find.query().orderBy().asc("description").findList();
        return ok(views.html.adminGameList.render(request, games, messages()));
    }

    @AddCSRFToken
    public Result newGame(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        return ok(gameForm(request, null, null));
    }

    @AddCSRFToken
    public Result editGame(Http.Request request, Long id) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Game game = Game.find.byId(id);
        if (game == null) return notFound("Game not found.");
        return ok(gameForm(request, game, null));
    }

    public Result createGame(Http.Request request) {
        return saveGame(request, new Game());
    }

    public Result updateGame(Http.Request request, Long id) {
        Game game = Game.find.byId(id);
        if (game == null) return notFound("Game not found.");
        return saveGame(request, game);
    }

    private Result saveGame(Http.Request request, Game game) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String description = value(data, "description");
        if (description.isBlank()) {
            return badRequest(gameForm(request, game.getId() == null ? null : game,
                    "Please complete all required fields correctly."));
        }
        game.setDescription(description);
        game.setShortText(value(data, "shortText"));
        if (game.getId() == null) game.save(); else game.update();
        clearPublicCaches();
        return redirect(routes.AdminController.gameIndex()).flashing("success", "Game saved successfully.");
    }

    private play.twirl.api.Html gameForm(Http.Request request, Game game, String error) {
        return views.html.adminGameForm.render(request, game, error, messages());
    }

    @AddCSRFToken
    public Result gameTypeIndex(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        List<GameType> gameTypes = GameType.find.query().orderBy().asc("gameType").findList();
        return ok(views.html.adminGameTypeList.render(request, gameTypes, messages()));
    }

    @AddCSRFToken
    public Result newGameType(Http.Request request) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        return ok(gameTypeForm(request, null, null));
    }

    @AddCSRFToken
    public Result editGameType(Http.Request request, Long id) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        GameType gameType = GameType.find.byId(id);
        if (gameType == null) return notFound("Game type not found.");
        return ok(gameTypeForm(request, gameType, null));
    }

    public Result createGameType(Http.Request request) {
        return saveGameType(request, new GameType());
    }

    public Result updateGameType(Http.Request request, Long id) {
        GameType gameType = GameType.find.byId(id);
        if (gameType == null) return notFound("Game type not found.");
        return saveGameType(request, gameType);
    }

    private Result saveGameType(Http.Request request, GameType gameType) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String gameTypeName = value(data, "gameType");
        if (gameTypeName.isBlank()) {
            return badRequest(gameTypeForm(request, gameType.getId() == null ? null : gameType,
                    "Please complete all required fields correctly."));
        }
        gameType.setGameType(gameTypeName);
        if (gameType.getId() == null) gameType.save(); else gameType.update();
        clearPublicCaches();
        return redirect(routes.AdminController.gameTypeIndex()).flashing("success", "Game type saved successfully.");
    }

    private play.twirl.api.Html gameTypeForm(Http.Request request, GameType gameType, String error) {
        return views.html.adminGameTypeForm.render(request, gameType, error, messages());
    }

    private play.twirl.api.Html newsForm(Http.Request request, News news, String error) {
        // New items default to the signed-in member as author.
        Long defaultAuthorId = AdminAuth.currentUser(request).map(User::getId).orElse(null);
        return views.html.adminNewsForm.render(request, news, User.find.query().orderBy().asc("nick").findList(),
                defaultAuthorId, error, messages());
    }

    private Result saveNews(Http.Request request, News news) {
        Result denied = require(request, Role.NEWS);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String topic = value(data, "topic");
        String content = value(data, "content");
        User author = find(User.find, value(data, "usernameId"));
        Date timestamp = parseDate(value(data, "timestamp"));
        if (topic.isBlank() || content.isBlank() || author == null || timestamp == null) {
            return badRequest(newsForm(request, news.getId() == null ? null : news,
                    "Please complete all required fields correctly."));
        }
        news.setTopic(topic);
        news.setContent(ContentSanitizer.sanitizeHtml(content));
        news.setUsername(author);
        news.setTimestamp(timestamp);
        if (news.getId() == null) news.save(); else news.update();
        clearPublicCaches();
        return redirect(routes.AdminController.newsIndex()).flashing("success", "News item saved successfully.");
    }

    private Result saveClanwar(Http.Request request, Clanwar clanwar) {
        Result denied = require(request, Role.CLANWARS);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String enemy = value(data, "enemy");
        Date date = parseDate(value(data, "date"));
        Game game = find(Game.find, value(data, "gameId"));
        GameType gameType = find(GameType.find, value(data, "gameTypeId"));
        League league = find(League.find, value(data, "leagueId"));
        Country country = find(Country.find, value(data, "countryId"));
        if (enemy.isBlank() || date == null || game == null || gameType == null || league == null || country == null) {
            return badRequest(clanwarForm(request, clanwar.getId() == null ? null : clanwar,
                    "Please complete all required fields correctly."));
        }
        String relationError = validateClanwarRelations(data, clanwar);
        if (relationError != null) {
            return badRequest(clanwarForm(request, clanwar.getId() == null ? null : clanwar, relationError));
        }
        clanwar.setEnemy(enemy);
        clanwar.setDate(date);
        clanwar.setUrl(value(data, "url"));
        clanwar.setReport(value(data, "report"));
        clanwar.setGame(game);
        clanwar.setGametype(gameType);
        clanwar.setLeague(league);
        clanwar.setCountry(country);
        if (clanwar.getId() == null) clanwar.save(); else clanwar.update();
        syncClanwarRelations(clanwar, data);
        clearPublicCaches();
        for (String lang : PUBLIC_CACHE_LANGS) {
            cache.remove(HomeController.clanwarCacheKey(clanwar.getId(), lang));
        }
        return redirect(routes.AdminController.clanwarIndex()).flashing("success", "Clanwar saved successfully.");
    }

    private Result saveLineupMember(Http.Request request, User member) {
        Result denied = require(request, Role.LINEUP);
        if (denied != null) return denied;
        Result protectedAccount = protectAdminAccount(request, member);
        if (protectedAccount != null) return protectedAccount;
        User actor = AdminAuth.currentUser(request).orElseThrow();
        boolean manageAccess = actor.hasRole(Role.ADMIN);
        Map<String, String[]> data = form(request);
        boolean creating = member.getId() == null;
        String nick = value(data, "nick");
        String email = value(data, "email");
        Date birthDate = parseDay(value(data, "birthDate"));
        Date since = parseDay(value(data, "since"));
        Date exitDate = parseDay(value(data, "exitDate"));

        if (nick.length() < 3
                || (!value(data, "birthDate").isBlank() && birthDate == null)
                || (!value(data, "since").isBlank() && since == null)
                || (!value(data, "exitDate").isBlank() && exitDate == null)) {
            return badRequest(lineupForm(request, creating ? null : member,
                    "Please enter a nick with at least three characters and check all entered values."));
        }
        // Case-insensitive, because the login looks nicks up that way.
        User sameNick = User.findByNick(nick);
        User sameEmail = email.isBlank() ? null : User.find.query().where().eq("email", email).findOne();
        if ((sameNick != null && !sameNick.getId().equals(member.getId()))
                || (sameEmail != null && !sameEmail.getId().equals(member.getId()))) {
            return badRequest(lineupForm(request, creating ? null : member, "Nick and email address must be unique."));
        }
        boolean active = data.containsKey("active");
        Set<Role> roles = manageAccess ? selectedRoles(data) : member.getRoleSet();
        if (!creating && member.hasRole(Role.ADMIN) && Boolean.TRUE.equals(member.getActive())
                && (!active || !roles.contains(Role.ADMIN)) && !otherActiveAdminExists(member)) {
            return badRequest(lineupForm(request, member,
                    "This is the last active administrator. Make someone else an administrator first."));
        }

        member.setNick(nick);
        member.setRealname(value(data, "realname"));
        member.setEmail(email.isBlank() ? null : email);
        member.setBirthDate(birthDate);
        member.setCity(value(data, "city"));
        member.setJob(value(data, "job"));
        member.setQuote(value(data, "quote"));
        member.setSince(since);
        member.setExitDate(exitDate);
        member.setImage(value(data, "image"));
        member.setActive(active);
        member.setFounder(data.containsKey("founder"));
        member.setClanLeader(data.containsKey("clanLeader"));
        member.setHonoraryMember(data.containsKey("honoraryMember"));
        if (creating) member.save(); else member.update();
        syncSquads(member, data);
        if (manageAccess) syncRoles(request, actor, member, roles);
        if (!active) PasswordToken.find.query().where().eq("member.id", member.getId()).delete();
        clearPublicCaches();
        return redirect(routes.AdminController.lineupIndex()).flashing("success", "Lineup member saved successfully.");
    }

    /**
     * Only administrators may touch administrator accounts. Otherwise LINEUP could
     * deactivate or rename an administrator, or edit their way into the account.
     */
    private Result protectAdminAccount(Http.Request request, User member) {
        if (member.getId() == null || !member.hasRole(Role.ADMIN)) return null;
        User actor = AdminAuth.currentUser(request).orElseThrow();
        return actor.hasRole(Role.ADMIN) ? null : AdminAuth.forbidden(request, actor, Role.ADMIN, messages());
    }

    private static boolean otherActiveAdminExists(User member) {
        return UserRole.find.query().where()
                .eq("role", Role.ADMIN)
                .eq("member.active", true)
                .ne("member.id", member.getId())
                .exists();
    }

    private static Set<Role> selectedRoles(Map<String, String[]> data) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String name : values(data, "role")) {
            try { roles.add(Role.valueOf(name)); } catch (IllegalArgumentException ignored) { }
        }
        return roles;
    }

    private void syncRoles(Http.Request request, User actor, User member, Set<Role> selected) {
        List<UserRole> entries = UserRole.find.query().where().eq("member.id", member.getId()).findList();
        Set<Role> previous = EnumSet.noneOf(Role.class);
        for (UserRole entry : entries) {
            previous.add(entry.getRole());
            if (!selected.contains(entry.getRole())) entry.delete();
        }
        for (Role role : selected) {
            if (previous.contains(role)) continue;
            UserRole entry = new UserRole();
            entry.setMember(member);
            entry.setRole(role);
            entry.save();
        }
        if (!previous.equals(selected)) {
            AuthLog.event(AuthLog.Event.ROLES_CHANGED).member(member).actor(actor)
                    .field("roles", roleList(selected)).field("previous", roleList(previous))
                    .request(request).log();
        }
    }

    private static String roleList(Set<Role> roles) {
        StringBuilder list = new StringBuilder();
        for (Role role : roles) {
            if (list.length() > 0) list.append(',');
            list.append(role.name().toLowerCase(java.util.Locale.ROOT));
        }
        return list.toString();
    }

    private void syncSquads(User member, Map<String, String[]> data) {
        Set<Long> selected = new HashSet<>();
        for (String id : values(data, "squadId")) {
            try { selected.add(Long.valueOf(id)); } catch (NumberFormatException ignored) { }
        }
        List<UserSquad> entries = UserSquad.find.query().where().eq("member.id", member.getId()).findList();
        Set<Long> existing = new HashSet<>();
        for (UserSquad entry : entries) {
            Long squadId = entry.getSquad().getId();
            if (!selected.contains(squadId)) entry.delete(); else existing.add(squadId);
        }
        for (Long squadId : selected) {
            if (existing.contains(squadId)) continue;
            Squad squad = Squad.find.byId(squadId);
            if (squad != null) {
                UserSquad entry = new UserSquad();
                entry.setMember(member);
                entry.setSquad(squad);
                entry.save();
            }
        }
    }

    private play.twirl.api.Html lineupForm(Http.Request request, User member, String error) {
        return lineupForm(request, member, error, null);
    }

    private play.twirl.api.Html lineupForm(Http.Request request, User member, String error, String passwordLink) {
        boolean manageAccess = AdminAuth.hasRole(request, Role.ADMIN);
        PasswordToken openToken = member == null || member.getId() == null ? null : PasswordToken.openFor(member);
        return views.html.adminLineupForm.render(request, member,
                Squad.find.query().orderBy().asc("description").findList(), error, manageAccess,
                openToken, passwordLink, messages());
    }

    private play.twirl.api.Html clanwarForm(Http.Request request, Clanwar clanwar, String error) {
        return views.html.adminClanwarForm.render(request, clanwar, Game.find.all(), GameType.find.all(),
                League.find.all(), Country.find.all(), models.Map.find.all(), User.find.all(), error, messages());
    }

    private String validateClanwarRelations(Map<String, String[]> data, Clanwar clanwar) {
        if (clanwar != null && clanwar.getId() != null) {
            for (Score score : Score.find.query().where().eq("match.id", clanwar.getId()).findList()) {
                if (data.containsKey("scoreDelete_" + score.getId())) continue;
                if (find(models.Map.find, value(data, "scoreMap_" + score.getId())) == null
                        || integer(value(data, "scoreDwc_" + score.getId())) == null
                        || integer(value(data, "scoreEnemy_" + score.getId())) == null) {
                    return "Each score requires a map and two valid numeric scores.";
                }
            }
        }
        for (String key : values(data, "newScoreKey")) {
            if (find(models.Map.find, value(data, "newScoreMap_" + key)) == null
                    || integer(value(data, "newScoreDwc_" + key)) == null
                    || integer(value(data, "newScoreEnemy_" + key)) == null) {
                return "Each new score requires a map and two valid numeric scores.";
            }
        }
        return null;
    }

    private void syncClanwarRelations(Clanwar clanwar, Map<String, String[]> data) {
        syncLineup(clanwar, data);
        List<Score> existingScores = Score.find.query().where().eq("match.id", clanwar.getId()).findList();
        for (Score score : existingScores) {
            if (data.containsKey("scoreDelete_" + score.getId())) {
                score.delete();
                continue;
            }
            score.setMap(find(models.Map.find, value(data, "scoreMap_" + score.getId())));
            score.setDwcScore(integer(value(data, "scoreDwc_" + score.getId())));
            score.setEnemyScore(integer(value(data, "scoreEnemy_" + score.getId())));
            score.update();
            syncImages(score, data);
        }
        for (String key : values(data, "newScoreKey")) {
            Score score = new Score();
            score.setMatch(clanwar);
            score.setMap(find(models.Map.find, value(data, "newScoreMap_" + key)));
            score.setDwcScore(integer(value(data, "newScoreDwc_" + key)));
            score.setEnemyScore(integer(value(data, "newScoreEnemy_" + key)));
            score.save();
            for (String imageName : values(data, "newScoreImage_" + key)) saveImage(score, imageName);
        }
    }

    private void syncLineup(Clanwar clanwar, Map<String, String[]> data) {
        Set<Long> selected = new HashSet<>();
        for (String id : values(data, "lineupMemberId")) {
            try { selected.add(Long.valueOf(id)); } catch (NumberFormatException ignored) { }
        }
        List<MatchLineup> entries = MatchLineup.find.query().where().eq("match.id", clanwar.getId()).findList();
        Set<Long> existing = new HashSet<>();
        for (MatchLineup entry : entries) {
            Long memberId = entry.getMember().getId();
            if (!selected.contains(memberId)) entry.delete(); else existing.add(memberId);
        }
        for (Long memberId : selected) {
            if (existing.contains(memberId)) continue;
            User member = User.find.byId(memberId);
            if (member != null) {
                MatchLineup entry = new MatchLineup();
                entry.setMatch(clanwar);
                entry.setMember(member);
                entry.save();
            }
        }
    }

    private void syncImages(Score score, Map<String, String[]> data) {
        for (ScoreImage image : ScoreImage.find.query().where().eq("score.id", score.getId()).findList()) {
            if (data.containsKey("imageDelete_" + image.getId())) {
                image.delete();
            } else {
                String name = value(data, "imageName_" + image.getId());
                if (name.isBlank()) image.delete(); else { image.setImage(name); image.update(); }
            }
        }
        for (String imageName : values(data, "scoreNewImage_" + score.getId())) saveImage(score, imageName);
    }

    private void saveImage(Score score, String imageName) {
        if (imageName == null || imageName.trim().isBlank()) return;
        ScoreImage image = new ScoreImage();
        image.setScore(score);
        image.setImage(imageName.trim());
        image.save();
    }

    private void clearPublicCaches() {
        for (String lang : PUBLIC_CACHE_LANGS) {
            cache.remove("index_" + lang);
            cache.remove("clanwars_" + lang);
            cache.remove("lineup_" + lang);
            cache.remove("halloffame_" + lang);
            cache.remove("videos_" + lang);
            cache.remove("downloads_" + lang);
        }
    }

    private Result require(Http.Request request, Role role) {
        return AdminAuth.require(request, role, messages());
    }

    private String configured(String path) {
        return configuration.underlying().hasPath(path) ? configuration.underlying().getString(path) : "";
    }

    private static Map<String, String[]> form(Http.Request request) {
        Map<String, String[]> values = request.body().asFormUrlEncoded();
        return values == null ? Map.of() : values;
    }

    private static String value(Map<String, String[]> data, String key) {
        String[] values = data.get(key);
        return values == null || values.length == 0 || values[0] == null ? "" : values[0].trim();
    }

    /** Like value(), but untrimmed: leading or trailing blanks are part of a password. */
    private static String rawValue(Map<String, String[]> data, String key) {
        String[] values = data.get(key);
        return values == null || values.length == 0 || values[0] == null ? "" : values[0];
    }

    private static String[] values(Map<String, String[]> data, String key) {
        String[] values = data.get(key);
        return values == null ? new String[0] : values;
    }

    private static Integer integer(String value) {
        try { return Integer.valueOf(value); } catch (NumberFormatException ex) { return null; }
    }

    private static Integer optionalInteger(String value) {
        return value.isBlank() ? null : integer(value);
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private static Date parseDay(String value) {
        if (value.isBlank()) return null;
        try {
            return Date.from(LocalDate.parse(value, DATE).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Date parseDate(String value) {
        try {
            return Date.from(LocalDateTime.parse(value, DATE_TIME).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static <T> T find(io.ebean.Finder<Long, T> finder, String id) {
        try { return finder.byId(Long.valueOf(id)); } catch (NumberFormatException ex) { return null; }
    }
}
