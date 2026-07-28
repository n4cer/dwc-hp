package controllers;

import jakarta.inject.Inject;
import models.Clanwar;
import models.ContentSanitizer;
import models.Country;
import models.Game;
import models.GameType;
import models.League;
import models.MatchLineup;
import models.News;
import models.Score;
import models.ScoreImage;
import models.Squad;
import models.User;
import play.api.Configuration;
import play.cache.AsyncCacheApi;
import play.filters.csrf.AddCSRFToken;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminController extends Controller {
    private static final String ADMIN_SESSION = AdminAuth.ADMIN_SESSION;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Configuration configuration;
    private final AsyncCacheApi cache;
    private final LoginRateLimiter loginRateLimiter;

    @Inject
    public AdminController(Configuration configuration, AsyncCacheApi cache, LoginRateLimiter loginRateLimiter) {
        this.configuration = configuration;
        this.cache = cache;
        this.loginRateLimiter = loginRateLimiter;
    }

    @AddCSRFToken
    public Result login(Http.Request request) {
        if (isAuthenticated(request)) return redirect(routes.AdminController.index());
        return ok(views.html.adminLogin.render(request, credentialsConfigured(), null));
    }

    public Result authenticate(Http.Request request) {
        String client = request.remoteAddress();
        LoginRateLimiter.LimitStatus limit = loginRateLimiter.status(client);
        if (limit.blocked()) return rateLimited(request, limit);

        Map<String, String[]> data = form(request);
        String username = value(data, "username");
        String password = value(data, "password");
        if (!credentialsConfigured() || !secureEquals(username, configured("admin.username"))
                || !secureEquals(password, configured("admin.password"))) {
            limit = loginRateLimiter.recordFailure(client);
            if (limit.blocked()) return rateLimited(request, limit);
            return unauthorized(views.html.adminLogin.render(request, credentialsConfigured(), "Invalid username or password."));
        }
        loginRateLimiter.reset(client);
        return redirect(routes.AdminController.index()).addingToSession(request, ADMIN_SESSION, username);
    }

    private Result rateLimited(Http.Request request, LoginRateLimiter.LimitStatus limit) {
        return status(TOO_MANY_REQUESTS, views.html.adminLogin.render(request, credentialsConfigured(),
                "Too many failed login attempts. Please try again later."))
                .withHeader("Retry-After", Long.toString(limit.retryAfterSeconds()));
    }

    public Result logout(Http.Request request) {
        return redirect(routes.AdminController.login()).removingFromSession(request, ADMIN_SESSION);
    }

    @AddCSRFToken
    public Result index(Http.Request request) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        List<News> news = News.find.query().orderBy().desc("timestamp").findList();
        List<Clanwar> clanwars = Clanwar.find.query().orderBy().desc("date").findList();
        List<User> lineup = User.find.query().orderBy().asc("nick").findList();
        return ok(views.html.adminIndex.render(request, news, clanwars, lineup));
    }

    @AddCSRFToken
    public Result newLineupMember(Http.Request request) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        return ok(lineupForm(request, null, null));
    }

    @AddCSRFToken
    public Result editLineupMember(Http.Request request, Long id) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        User member = User.find.byId(id);
        if (member == null) return notFound("Lineup member not found.");
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
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        return ok(views.html.adminNewsForm.render(request, null, User.find.all(), null));
    }

    @AddCSRFToken
    public Result editNews(Http.Request request, Long id) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        News news = News.find.byId(id);
        if (news == null) return notFound("News item not found.");
        return ok(views.html.adminNewsForm.render(request, news, User.find.all(), null));
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
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        return ok(clanwarForm(request, null, null));
    }

    @AddCSRFToken
    public Result editClanwar(Http.Request request, Long id) {
        Result denied = requireAdmin(request);
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

    private Result saveNews(Http.Request request, News news) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        String topic = value(data, "topic");
        String content = value(data, "content");
        User author = find(User.find, value(data, "usernameId"));
        Date timestamp = parseDate(value(data, "timestamp"));
        if (topic.isBlank() || content.isBlank() || author == null || timestamp == null) {
            return badRequest(views.html.adminNewsForm.render(request, news.getId() == null ? null : news,
                    User.find.all(), "Please complete all required fields correctly."));
        }
        news.setTopic(topic);
        news.setContent(ContentSanitizer.sanitizeHtml(content));
        news.setUsername(author);
        news.setTimestamp(timestamp);
        if (news.getId() == null) news.save(); else news.update();
        clearPublicCaches();
        return redirect(routes.AdminController.index()).flashing("success", "News item saved successfully.");
    }

    private Result saveClanwar(Http.Request request, Clanwar clanwar) {
        Result denied = requireAdmin(request);
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
        cache.remove(HomeController.clanwarCacheKey(clanwar.getId()));
        return redirect(routes.AdminController.index()).flashing("success", "Clanwar saved successfully.");
    }

    private Result saveLineupMember(Http.Request request, User member) {
        Result denied = requireAdmin(request);
        if (denied != null) return denied;
        Map<String, String[]> data = form(request);
        boolean creating = member.getId() == null;
        String nick = value(data, "nick");
        String email = value(data, "email");
        String password = value(data, "password");
        Date birthDate = parseDay(value(data, "birthDate"));
        Date since = parseDay(value(data, "since"));
        Date exitDate = parseDay(value(data, "exitDate"));
        Integer squad = optionalInteger(value(data, "squad"));
        Integer type = optionalInteger(value(data, "type"));

        if (nick.length() < 4 || (creating && password.isBlank())
                || (!value(data, "birthDate").isBlank() && birthDate == null)
                || (!value(data, "since").isBlank() && since == null)
                || (!value(data, "exitDate").isBlank() && exitDate == null)
                || (!value(data, "squad").isBlank() && squad == null)
                || (!value(data, "type").isBlank() && type == null)) {
            return badRequest(lineupForm(request, creating ? null : member,
                    "Please enter a nick with at least four characters and check all entered values. A password is required for new members."));
        }
        User sameNick = User.find.query().where().eq("nick", nick).findOne();
        User sameEmail = email.isBlank() ? null : User.find.query().where().eq("email", email).findOne();
        if ((sameNick != null && !sameNick.getId().equals(member.getId()))
                || (sameEmail != null && !sameEmail.getId().equals(member.getId()))) {
            return badRequest(lineupForm(request, creating ? null : member, "Nick and email address must be unique."));
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
        member.setSquad(squad);
        member.setType(type);
        member.setNotits(data.containsKey("notits"));
        member.setActive(data.containsKey("active"));
        if (!password.isBlank()) member.setPlainTextPassword(password);
        if (creating) member.save(); else member.update();
        clearPublicCaches();
        return redirect(routes.AdminController.index()).flashing("success", "Lineup member saved successfully.");
    }

    private play.twirl.api.Html lineupForm(Http.Request request, User member, String error) {
        return views.html.adminLineupForm.render(request, member,
                Squad.find.query().orderBy().asc("description").findList(), error);
    }

    private play.twirl.api.Html clanwarForm(Http.Request request, Clanwar clanwar, String error) {
        return views.html.adminClanwarForm.render(request, clanwar, Game.find.all(), GameType.find.all(),
                League.find.all(), Country.find.all(), models.Map.find.all(), User.find.all(), error);
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
        cache.remove("index");
        cache.remove("news");
        cache.remove("clanwars");
        cache.remove("lineup");
    }

    private Result requireAdmin(Http.Request request) {
        return isAuthenticated(request) ? null : redirect(routes.AdminController.login());
    }

    private boolean isAuthenticated(Http.Request request) {
        return AdminAuth.isAuthenticated(request, configuration);
    }

    private boolean credentialsConfigured() {
        return AdminAuth.credentialsConfigured(configuration);
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

    private static boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
