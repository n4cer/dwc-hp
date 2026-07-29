package controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import models.Clanwar;
import models.History;
import models.News;
import models.Squad;
import models.User;
import play.api.Configuration;
import play.cache.Cached;
import play.cache.SyncCacheApi;
import play.mvc.*;
import play.twirl.api.Html;

import static play.libs.Scala.asScala;

public class HomeController extends Controller {
    public static final String CONST_TIMESTAMP = "timestamp";
    private static final int NEWS_PAGE_SIZE = 10;
    private static final int CLANWAR_CACHE_DURATION = 1200;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    @Inject Configuration configuration;
    @Inject SyncCacheApi cache;
    
    @Cached(key = "index", duration = 600)
    public Result index() {
        List<Clanwar> clanwars = Clanwar.find.query().setMaxRows(2).orderBy().desc("date").findList();
        List<News> news = News.find.query().setMaxRows(2).orderBy().desc(CONST_TIMESTAMP).findList();
        
        return ok(views.html.index.render(asScala(clanwars), asScala(news)));
    }
    
    public Result news(Http.Request request, int page) {
      int newsCount = News.find.query().findCount();
      int pageCount = Math.max(1, (newsCount + NEWS_PAGE_SIZE - 1) / NEWS_PAGE_SIZE);
      int currentPage = Math.min(Math.max(page, 1), pageCount);
      List<News> news = News.find.query()
              .setFirstRow((currentPage - 1) * NEWS_PAGE_SIZE)
              .setMaxRows(NEWS_PAGE_SIZE)
              .orderBy(CONST_TIMESTAMP + " desc, id desc")
              .findList();
      boolean isAdmin = AdminAuth.isAuthenticated(request, configuration);

      return ok(views.html.news.render(news, currentPage, pageCount, isAdmin));
    }
    
    @Cached(key = "clanwars", duration = 1200)
    public Result clanwars() {
      List<Clanwar> clanwars = Clanwar.find.query().orderBy().desc("date").findList();
      
      return ok(views.html.clanwars.render(asScala(clanwars)));
    }
    
    public Result clanwar(Http.Request request, Long id) {
      boolean isAdmin = AdminAuth.isAuthenticated(request, configuration);
      if (!isAdmin) {
        Optional<Html> cached = cache.get(clanwarCacheKey(id));
        if (cached.isPresent()) return ok(cached.get());
      }

      Clanwar clanwar = Clanwar.find.byId(id);
      if (clanwar == null) return notFound("Clanwar nicht gefunden");
      Html html = views.html.clanwar.render(clanwar, isAdmin);
      if (!isAdmin) cache.set(clanwarCacheKey(id), html, CLANWAR_CACHE_DURATION);

      return ok(html);
    }

    public static String clanwarCacheKey(Long id) {
      return "clanwar_" + id;
    }
    
    @Cached(key = "lineup", duration = 600)
    public Result lineup() {
        List<Squad> squads = Squad.find.all();

        return ok(views.html.lineup.render(squads));
    }
    
    public Result player(Long id) {
        User player = User.find.byId(id);
        if (player == null) return notFound("Spieler nicht gefunden");

        return ok(views.html.player.render(player));
    }
    
    @Cached(key = "contact", duration = 2400)
    public Result contact() {
      return ok(views.html.contact.render());
    }
    
    @Cached(key = "history", duration = 2400)
    public Result history() {
      List<History> entries = History.find.query().orderBy().desc(CONST_TIMESTAMP).findList();
      
      return ok(views.html.history.render(entries));
    }
    
    @Cached(key = "imprint", duration = 2400)
    public Result imprint() {
      String name = configuration.underlying().getString("owner.name");
      String street = configuration.underlying().getString("owner.street");
      String city = configuration.underlying().getString("owner.city");
      String email = configuration.underlying().getString("owner.email");
      String emailEncoded = "";
      for (int i = 0; i < email.length(); i++) {
        emailEncoded += ("&#" + email.codePointAt(i) + ";");
      }
      
      return ok(views.html.imprint.render(name, street, city, emailEncoded));
    }
    
    @Cached(key = "privacy", duration = 2400)
    public Result privacy() {
      String name = configuration.underlying().getString("owner.name");
      String street = configuration.underlying().getString("owner.street");
      String city = configuration.underlying().getString("owner.city");
      String country = configuration.underlying().getString("privacy.country");
      String email = configuration.underlying().getString("privacy.email");
      String emailEncoded = "";
      for (int i = 0; i < email.length(); i++) {
        emailEncoded += ("&#" + email.codePointAt(i) + ";");
      }
      
      return ok(views.html.privacy.render(name, street, city, country, emailEncoded));
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
    
    @Cached(key = "pickup", duration = 2400)
    public Result pickup() {
      return ok(views.html.pickup.render());
    }
    
    @Cached(key = "todo", duration = 2400)
    public Result todo() {
        return ok(views.html.todo.render());
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
