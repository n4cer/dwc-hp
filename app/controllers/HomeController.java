package controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
import play.mvc.*;

import static play.libs.Scala.asScala;

public class HomeController extends Controller {
    public static final String CONST_TIMESTAMP = "timestamp";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    @Inject Configuration configuration;
    
    @Cached(key = "index", duration = 600)
    public Result index() {
        List<Clanwar> clanwars = Clanwar.find.query().setMaxRows(2).orderBy().desc("date").findList();
        List<News> news = News.find.query().setMaxRows(2).orderBy().desc(CONST_TIMESTAMP).findList();
        
        return ok(views.html.index.render(asScala(clanwars), asScala(news)));
    }
    
    @Cached(key = "news", duration = 600)
    public Result news() {
      List<News> news = News.find.query().setMaxRows(10).orderBy().desc(CONST_TIMESTAMP).findList();
      
      return ok(views.html.news.render(news));
    }
    
    @Cached(key = "clanwars", duration = 1200)
    public Result clanwars() {
      List<Clanwar> clanwars = Clanwar.find.query().orderBy().desc("date").findList();
      
      return ok(views.html.clanwars.render(asScala(clanwars)));
    }
    
    public Result clanwar(Long id) {
      Clanwar clanwar = Clanwar.find.byId(id);
      
      return ok(views.html.clanwar.render(clanwar));
    }
    
    @Cached(key = "lineup", duration = 600)
    public Result lineup() {
        List<User> players = User.find.all();
        List<Squad> squads = Squad.find.all();
        
        return ok(views.html.lineup.render(squads, players));
    }
    
    public Result player(Long id) {
        User player = User.find.byId(id);
        
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
      Path folder = Path.of(configuration.underlying().getString("picture_folder"));
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
}
