package controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.inject.Inject;

import models.Clanwar;
import models.ClanwarStats;
import models.History;
import models.MatchLineup;
import models.News;
import models.Score;
import models.ScoreImage;
import models.Squad;
import models.User;
import models.UserSquad;
import play.api.Configuration;
import play.cache.Cached;
import play.cache.SyncCacheApi;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.libs.Json;
import play.mvc.*;
import play.twirl.api.Html;

import static play.libs.Scala.asScala;

public class HomeController extends Controller {
    public static final String CONST_TIMESTAMP = "timestamp";
    private static final int NEWS_PAGE_SIZE = 10;
    private static final int CLANWAR_CACHE_DURATION = 1200;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> SUPPORTED_LANGS = Set.of("de", "en");
    @Inject Configuration configuration;
    @Inject SyncCacheApi cache;
    @Inject MessagesApi messagesApi;

    public Result setLanguage(String code, String to) {
      Result result = redirect(isSafeRedirectTarget(to) ? to : "/");
      return SUPPORTED_LANGS.contains(code) ? messagesApi.setLang(result, Lang.forCode(code)) : result;
    }

    private static boolean isSafeRedirectTarget(String to) {
      return to != null && to.startsWith("/") && !to.startsWith("//") && !to.contains("://");
    }

    private Result cachedPage(String baseKey, Messages messages, int duration, java.util.function.Supplier<Html> render) {
      String key = baseKey + "_" + messages.lang().code();
      Optional<Html> hit = cache.get(key);
      if (hit.isPresent()) return ok(hit.get());

      Html html = render.get();
      cache.set(key, html, duration);
      return ok(html);
    }

    public Result index(Http.Request request) {
        Messages messages = messagesApi.preferred(request);
        List<Clanwar> clanwars = Clanwar.find.query().setMaxRows(2).orderBy().desc("date").findList();
        List<News> news = News.find.query().setMaxRows(2).orderBy().desc(CONST_TIMESTAMP).findList();

        return cachedPage("index", messages, 600,
                () -> views.html.index.render(asScala(clanwars), asScala(news), messages));
    }
    
    public Result news(Http.Request request, int page) {
      Messages messages = messagesApi.preferred(request);
      int newsCount = News.find.query().findCount();
      int pageCount = Math.max(1, (newsCount + NEWS_PAGE_SIZE - 1) / NEWS_PAGE_SIZE);
      int currentPage = Math.min(Math.max(page, 1), pageCount);
      List<News> news = News.find.query()
              .setFirstRow((currentPage - 1) * NEWS_PAGE_SIZE)
              .setMaxRows(NEWS_PAGE_SIZE)
              .orderBy(CONST_TIMESTAMP + " desc, id desc")
              .findList();
      boolean isAdmin = AdminAuth.isAuthenticated(request, configuration);

      return ok(views.html.news.render(news, currentPage, pageCount, isAdmin, messages));
    }

    public Result clanwars(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      List<Clanwar> clanwars = Clanwar.find.query().orderBy().desc("date").findList();
      ClanwarStats stats = ClanwarStats.from(clanwars);

      return cachedPage("clanwars", messages, 1200,
              () -> views.html.clanwars.render(asScala(clanwars), stats, messages));
    }

    public Result clanwar(Http.Request request, Long id) {
      Messages messages = messagesApi.preferred(request);
      boolean isAdmin = AdminAuth.isAuthenticated(request, configuration);
      if (!isAdmin) {
        Optional<Html> cached = cache.get(clanwarCacheKey(id, messages.lang().code()));
        if (cached.isPresent()) return ok(cached.get());
      }

      Clanwar clanwar = Clanwar.find.byId(id);
      if (clanwar == null) return notFound(messages.at("notFound.clanwar"));

      List<Clanwar> ordered = Clanwar.find.query().select("id, date, enemy").orderBy("date desc, id desc").findList();
      int index = -1;
      for (int i = 0; i < ordered.size(); i++) {
        if (ordered.get(i).getId().equals(id)) { index = i; break; }
      }
      Clanwar previous = index >= 0 && index + 1 < ordered.size() ? ordered.get(index + 1) : null;
      Clanwar next = index > 0 ? ordered.get(index - 1) : null;

      Html html = views.html.clanwar.render(clanwar, isAdmin, previous, next, messages);
      if (!isAdmin) cache.set(clanwarCacheKey(id, messages.lang().code()), html, CLANWAR_CACHE_DURATION);

      return ok(html);
    }

    public static String clanwarCacheKey(Long id, String langCode) {
      return "clanwar_" + id + "_" + langCode;
    }

    private static final DateTimeFormatter CLANWAR_JSON_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Cached(key = "clanwarsJson", duration = 1200)
    public Result clanwarsJson() {
      List<Clanwar> clanwars = Clanwar.find.query().orderBy().desc("date").findList();

      ArrayNode result = Json.newArray();
      for (Clanwar clanwar : clanwars) {
        result.add(clanwarToJson(clanwar));
      }

      return ok(result);
    }

    private ObjectNode clanwarToJson(Clanwar clanwar) {
      ObjectNode node = Json.newObject();
      node.put("id", clanwar.getId());
      node.put("date", clanwar.getDate().toInstant().atZone(ZoneId.systemDefault()).format(CLANWAR_JSON_DATE));
      node.put("enemy", clanwar.getEnemy());
      node.put("country", clanwar.getCountry() != null ? clanwar.getCountry().getCountry() : null);
      node.put("league", clanwar.getLeague() != null ? clanwar.getLeague().getLeague() : null);
      node.put("game", clanwar.getGame() != null ? clanwar.getGame().getDescription() : null);
      node.put("gametype", clanwar.getGametype() != null ? clanwar.getGametype().getGameType() : null);
      node.put("url", clanwar.hasUrl() ? clanwar.getUrl() : null);
      node.put("result", clanwar.getResult());
      node.put("report", clanwar.getReport());

      ArrayNode scores = Json.newArray();
      if (clanwar.getScores() != null) {
        for (Score score : clanwar.getScores()) {
          ObjectNode scoreNode = Json.newObject();
          scoreNode.put("map", score.getMap() != null ? score.getMap().getMap() : null);
          scoreNode.put("dwcScore", score.getDwcScore());
          scoreNode.put("enemyScore", score.getEnemyScore());

          ArrayNode screenshots = Json.newArray();
          if (score.getImages() != null) {
            for (ScoreImage image : score.getImages()) {
              screenshots.add(routes.HomeController.clanwarImage(image.getImage()).url());
            }
          }
          scoreNode.set("screenshots", screenshots);

          scores.add(scoreNode);
        }
      }
      node.set("scores", scores);

      ArrayNode lineup = Json.newArray();
      if (clanwar.getLineups() != null) {
        for (MatchLineup entry : clanwar.getLineups()) {
          if (entry.getMember() != null) lineup.add(entry.getMember().getNick());
        }
      }
      node.set("lineup", lineup);

      return node;
    }

    public Result lineup(Http.Request request) {
        Messages messages = messagesApi.preferred(request);
        List<Squad> squads = Squad.find.all();
        for (Squad squad : squads) {
            List<UserSquad> members = squad.getMembers();
            if (members != null) members.sort((a, b) -> compareMembershipDurationDesc(a.getMember(), b.getMember()));
        }

        return cachedPage("lineup", messages, 600, () -> views.html.lineup.render(squads, messages));
    }

    private static final int LONG_TENURE_YEARS = 5;
    private static final int LONG_TENURE_MIN_CLANWARS = 25;

    public Result hallOfFame(Http.Request request) {
        Messages messages = messagesApi.preferred(request);
        List<User> founders = new ArrayList<>();
        List<User> leaders = new ArrayList<>();
        List<User> honorary = new ArrayList<>();
        List<User> longTenure = new ArrayList<>();
        for (User member : User.find.all()) {
            if (Boolean.TRUE.equals(member.getFounder())) {
                founders.add(member);
            }
            if (Boolean.TRUE.equals(member.getClanLeader())) {
                leaders.add(member);
            }
            if (Boolean.TRUE.equals(member.getHonoraryMember())) {
                honorary.add(member);
            }
            if (isLongTenureMember(member)) {
                longTenure.add(member);
            }
        }
        founders.sort(HomeController::compareMembershipDurationDesc);
        leaders.sort(HomeController::compareMembershipDurationDesc);
        honorary.sort(HomeController::compareMembershipDurationDesc);
        longTenure.sort(HomeController::compareMembershipDurationDesc);

        return cachedPage("halloffame", messages, 600,
                () -> views.html.hallOfFame.render(founders, leaders, honorary, longTenure, messages));
    }

    private static long membershipYears(User member) {
        if (member.getSince() == null) return 0;
        LocalDate start = toLocalDate(member.getSince());
        LocalDate end = member.getExitDate() != null ? toLocalDate(member.getExitDate()) : LocalDate.now();
        return ChronoUnit.YEARS.between(start, end);
    }

    private static int clanwarCount(User member) {
        return member.getLineups() != null ? member.getLineups().size() : 0;
    }

    private static boolean isLongTenureMember(User member) {
        return membershipYears(member) >= LONG_TENURE_YEARS && clanwarCount(member) >= LONG_TENURE_MIN_CLANWARS;
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static int compareMembershipDurationDesc(User a, User b) {
        return Long.compare(membershipDurationMillis(b), membershipDurationMillis(a));
    }

    private static long membershipDurationMillis(User member) {
        if (member.getSince() == null) return 0;
        Date end = member.getExitDate() != null ? member.getExitDate() : new Date();
        return end.getTime() - member.getSince().getTime();
    }

    public Result player(Http.Request request, Long id) {
        Messages messages = messagesApi.preferred(request);
        User player = User.find.byId(id);
        if (player == null) return notFound(messages.at("notFound.player"));

        int wins = 0;
        int draws = 0;
        int losses = 0;
        if (player.getLineups() != null) {
            for (MatchLineup entry : player.getLineups()) {
                Clanwar match = entry.getMatch();
                String outcome = match != null ? match.getOutcome() : null;
                if ("win".equals(outcome)) {
                    wins++;
                } else if ("draw".equals(outcome)) {
                    draws++;
                } else if ("loss".equals(outcome)) {
                    losses++;
                }
            }
        }

        int total = wins + draws + losses;
        int winPct = roundToStep(wins, total);
        int drawPct = roundToStep(draws, total);
        int lossPct = roundToStep(losses, total);
        int winRatio = total > 0 ? Math.round(wins * 100f / total) : 0;

        return ok(views.html.player.render(player, wins, draws, losses, winPct, drawPct, lossPct, winRatio,
                isLongTenureMember(player), messages));
    }

    private static final int STAT_BAR_STEP = 5;

    private static int roundToStep(int value, int total) {
        if (total == 0) return 0;
        return Math.round(value * 100f / total / STAT_BAR_STEP) * STAT_BAR_STEP;
    }
    
    public Result contact(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      return cachedPage("contact", messages, 2400, () -> views.html.contact.render(messages));
    }

    public Result history(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      List<History> entries = History.find.query().orderBy().desc(CONST_TIMESTAMP).findList();

      return cachedPage("history", messages, 2400, () -> views.html.history.render(entries, messages));
    }

    public Result imprint(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      String name = configuration.underlying().getString("owner.name");
      String street = configuration.underlying().getString("owner.street");
      String city = configuration.underlying().getString("owner.city");
      String email = configuration.underlying().getString("owner.email");
      String emailEncoded = htmlEncodeEmail(email);

      return cachedPage("imprint", messages, 2400,
              () -> views.html.imprint.render(name, street, city, emailEncoded, messages));
    }

    private static String htmlEncodeEmail(String email) {
      StringBuilder encoded = new StringBuilder();
      for (int i = 0; i < email.length(); i++) {
        encoded.append("&#").append(email.codePointAt(i)).append(";");
      }
      return encoded.toString();
    }

    public Result privacy(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      String name = configuration.underlying().getString("owner.name");
      String street = configuration.underlying().getString("owner.street");
      String city = configuration.underlying().getString("owner.city");
      String country = configuration.underlying().getString("privacy.country");
      String email = configuration.underlying().getString("privacy.email");
      String emailEncoded = htmlEncodeEmail(email);

      return cachedPage("privacy", messages, 2400,
              () -> views.html.privacy.render(name, street, city, country, emailEncoded, messages));
    }
    
    @Cached(key = "randomPic", duration = 300)
    public Result randomPic() {
      Path folder = pictureSubfolder("random");
      if (!Files.isDirectory(folder)) return notFound("no image found");

      try (Stream<Path> entries = Files.list(folder)) {
        List<Path> images = entries
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(HomeController::hasAllowedImageExtension)
                .toList();
        if (images.isEmpty()) return notFound("no image found");
        Path image = images.get(ThreadLocalRandom.current().nextInt(images.size()));
        return ok(image.toFile());
      } catch (IOException ex) {
        return internalServerError("image directory unavailable");
      }
    }

    public Result clanwarImage(String file) {
      return pictureFile("clanwars", file);
    }

    public Result lineupImage(String file) {
      return pictureFile("lineup", file);
    }

    private Result pictureFile(String subfolder, String file) {
      if (file.contains("/") || file.contains("\\")) return notFound("image not found");

      Path folder = pictureSubfolder(subfolder);
      Path image = folder.resolve(file).normalize();
      if (!image.startsWith(folder)
              || !hasAllowedImageExtension(image)
              || !Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS)) {
        return notFound("image not found");
      }
      return ok(image.toFile());
    }

    private Path pictureSubfolder(String subfolder) {
      return Path.of(configuration.underlying().getString("picture_folder")).resolve(subfolder);
    }

    static boolean hasAllowedImageExtension(Path path) {
      String name = path.getFileName().toString();
      int separator = name.lastIndexOf('.');
      return separator > 0 && separator < name.length() - 1
              && IMAGE_EXTENSIONS.contains(name.substring(separator + 1).toLowerCase(Locale.ROOT));
    }
    
    public Result pickup(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      return cachedPage("pickup", messages, 2400, () -> views.html.pickup.render(messages));
    }

    public Result todo(Http.Request request) {
      Messages messages = messagesApi.preferred(request);
      return cachedPage("todo", messages, 2400, () -> views.html.todo.render(messages));
    }

    @Cached(key = "robots", duration = 86400)
    public Result robots() {
      String baseUrl = siteBaseUrl();
      String content = "User-agent: *\n"
              + "Allow: /\n"
              + "Disallow: /admin\n"
              + "Sitemap: " + baseUrl + "/sitemap.xml\n";
      return ok(content).as("text/plain; charset=utf-8");
    }

    @Cached(key = "sitemap", duration = 3600)
    public Result sitemap() {
      String baseUrl = siteBaseUrl();
      StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

      addSitemapUrl(xml, baseUrl, routes.HomeController.index().url());
      addSitemapUrl(xml, baseUrl, routes.HomeController.news(1).url());
      int newsCount = News.find.query().findCount();
      int pageCount = Math.max(1, (newsCount + NEWS_PAGE_SIZE - 1) / NEWS_PAGE_SIZE);
      for (int page = 2; page <= pageCount; page++) {
        addSitemapUrl(xml, baseUrl, routes.HomeController.news(page).url());
      }
      addSitemapUrl(xml, baseUrl, routes.HomeController.clanwars().url());
      addSitemapUrl(xml, baseUrl, routes.HomeController.history().url());
      addSitemapUrl(xml, baseUrl, routes.HomeController.lineup().url());
      addSitemapUrl(xml, baseUrl, routes.HomeController.hallOfFame().url());
      addSitemapUrl(xml, baseUrl, routes.HomeController.pickup().url());
      addSitemapUrl(xml, baseUrl, routes.HomeController.contact().url());

      Clanwar.find.query().select("id").findEach(
              clanwar -> addSitemapUrl(xml, baseUrl, routes.HomeController.clanwar(clanwar.getId()).url()));
      User.find.query().select("id").findEach(
              player -> addSitemapUrl(xml, baseUrl, routes.HomeController.player(player.getId()).url()));

      xml.append("</urlset>\n");
      return ok(xml.toString()).as("application/xml; charset=utf-8");
    }

    private String siteBaseUrl() {
      String value = configuration.underlying().getString("site.baseUrl");
      return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static void addSitemapUrl(StringBuilder xml, String baseUrl, String path) {
      String location = (baseUrl + path)
              .replace("&", "&amp;")
              .replace("<", "&lt;")
              .replace(">", "&gt;")
              .replace("\"", "&quot;")
              .replace("'", "&apos;");
      xml.append("  <url><loc>").append(location).append("</loc></url>\n");
    }
}
