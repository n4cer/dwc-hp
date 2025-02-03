package models;


import java.util.Date;
import java.util.List;

import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@MappedSuperclass
@Entity
@Table(name = "clanwars")
public class Clanwar extends Model {
  @Id
  private Long id;
  @Constraints.Required
  private Date date;
  private String enemy;
  private String url;
  @ManyToOne
  private League league;
  @Column(columnDefinition = "TEXT")
  private String report;
  @ManyToOne
  private Game game;
  @ManyToOne
  private GameType gametype;
  @ManyToOne
  private Country country;
  @OneToMany(mappedBy="match", cascade=CascadeType.ALL)
  private List<Score> scores;
  @OneToMany(mappedBy="match", cascade=CascadeType.ALL)
  private List<MatchLineup> lineups;

  public static final Finder<Long, Clanwar> find = new Finder<>(Clanwar.class);

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public String getEnemy() {
    return enemy;
  }

  public void setEnemy(String enemy) {
    this.enemy = enemy;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public League getLeague() {
    return league;
  }

  public void setLeague(League league) {
    this.league = league;
  }

  public String getReport() {
    return report;
  }

  public void setReport(String report) {
    this.report = report;
  }

  public Game getGame() {
    return game;
  }

  public void setGame(Game game) {
    this.game = game;
  }

  public GameType getGametype() {
    return gametype;
  }

  public void setGametype(GameType gametype) {
    this.gametype = gametype;
  }

  public Country getCountry() {
    return country;
  }

  public void setCountry(Country country) {
    this.country = country;
  }

  public List<Score> getScores() {
    return scores;
  }

  public void setScores(List<Score> scores) {
    this.scores = scores;
  }

  public List<MatchLineup> getLineups() {
    return lineups;
  }

  public void setLineups(List<MatchLineup> lineups) {
    this.lineups = lineups;
  }

  public String getUrl() {
    if(this.url != null && this.url.contains("http")) {
      return url;
    }
    
    return "http://" + this.url;
  }
  
  public String getFlag() {
    //https://github.com/gosquared/flags
    return this.getCountry().getFlag();
  }
  
  public String getResult() {
    List<Score> scores = this.scores;
    Integer dwc = 0;
    Integer enemy = 0;
    
    for(Score score : scores)
    {
        if (this.gametype != null && this.gametype.getGameType().equals("tdm")) {
            dwc += score.getDwcScore();
            enemy += score.getEnemyScore();
        }
        else if (this.gametype != null && (this.gametype.getGameType().equals("ctf") || this.gametype.getGameType().equals("sw"))) {
            if(score.getDwcScore() > score.getEnemyScore()) {
                dwc += 2;
            } else if (score.getDwcScore() < score.getEnemyScore()) {
                enemy += 2;
            } else {
                enemy += 1;
                dwc += 1;
            }
        }
        else
        {
            dwc += score.getDwcScore();
            enemy += score.getEnemyScore();
        }
    }
    
    return dwc + ":" + enemy;
  }
  
  public String getColor() {
    String result = this.getResult();
    if(result == null) {
      return "#FF5706";
    }
    
    String[] splits = result.split(":");
    Integer dwc = Integer.valueOf(splits[0]);
    Integer enemy = Integer.valueOf(splits[1]);
    
    if (dwc < enemy) {
      return "#C10000";
    } else if (dwc > enemy) {
      return "#00B900";
    }
    
    return "#FF5706";
  }
  
  public String toString() {
    return getEnemy();
  }
}