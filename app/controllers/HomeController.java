package controllers;

import java.util.List;

import javax.inject.Inject;

import models.Clanwar;
import models.History;
import models.News;
import models.Squad;
import models.User;
import play.api.Configuration;
import play.mvc.*;

public class HomeController extends Controller {
    @Inject Configuration configuration;
    
    public Result index() {
        List<Clanwar> clanwars = Clanwar.find.query().setMaxRows(2).order().desc("date").findList();
        List<News> news = News.find.query().setMaxRows(2).order().desc("timestamp").findList();
        
        return ok(views.html.index.render(clanwars, news));
    }
    
    public Result news() {
      List<News> news = News.find.query().setMaxRows(10).order().desc("timestamp").findList();
      
      return ok(views.html.news.render(news));
    }
    
    public Result clanwars() {
      List<Clanwar> clanwars = Clanwar.find.query().order().desc("date").findList();
      
      return ok(views.html.clanwars.render(clanwars));
    }
    
    public Result clanwar(Long id) {
      Clanwar clanwar = Clanwar.find.byId(id);
      
      return ok(views.html.clanwar.render(clanwar));
    }
    
    public Result lineup() {
        List<User> players = User.find.all();
        List<Squad> squads = Squad.find.all();
        
        return ok(views.html.lineup.render(squads, players));
    }
    
    public Result player(Long id) {
        User player = User.find.byId(id);
        
        return ok(views.html.player.render(player));
    }
    
    public Result contact() {
      return ok(views.html.contact.render());
    }
    
    public Result history() {
      List<History> entries = History.find.query().order().desc("timestamp").findList();
      
      return ok(views.html.history.render(entries));
    }
    
    public Result imprint() {
      String name = configuration.underlying().getString("owner.name");
      String street = configuration.underlying().getString("owner.street");
      String city = configuration.underlying().getString("owner.city");
      String email = configuration.underlying().getString("owner.email");
      String email_encoded = "";
      for (int i = 0; i < email.length(); i++) {
        email_encoded += ("&#" + email.codePointAt(i) + ";");
      }
      
      return ok(views.html.imprint.render(name, street, city, email_encoded));
    }
    
    public Result privacy() {
      String name = configuration.underlying().getString("owner.name");
      String street = configuration.underlying().getString("owner.street");
      String city = configuration.underlying().getString("owner.city");
      String country = configuration.underlying().getString("privacy.country");
      String email = configuration.underlying().getString("privacy.email");
      String email_encoded = "";
      for (int i = 0; i < email.length(); i++) {
        email_encoded += ("&#" + email.codePointAt(i) + ";");
      }
      
      return ok(views.html.privacy.render(name, street, city, country, email_encoded));
    }
    
    public Result todo() {
        return ok(views.html.todo.render());
    }
}