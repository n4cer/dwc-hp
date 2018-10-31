package models;


import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "clanwars")
public class Clanwar extends Model {

  public static Finder<Long, Clanwar> find = new Finder<Long, Clanwar>(Clanwar.class);
  @Id
  public Long id;
  @Constraints.Required
  public Date date;
  public String enemy;
  public String url;
  @ManyToOne
  public League league;
  @Column(columnDefinition = "TEXT")
  public String report;
  @ManyToOne
  public Game game;
  @ManyToOne
  public GameType gametype;
  @ManyToOne
  public Country country;
  @OneToMany(mappedBy="match", cascade=CascadeType.ALL)
  public List<Score> scores;
  @OneToMany(mappedBy="match", cascade=CascadeType.ALL)
  public List<MatchLineup> lineups;
  
  public String getUrl() {
    if(this.url != null && this.url.contains("http")) {
      return url;
    }
    
    return "http://" + this.url;
  }
  
  public String getFlag() {
    //https://github.com/gosquared/flags
    return this.country.flag;
  }
  
  public String getResult() {
    List<Score> scores = this.scores;
    Integer dwc = 0;
    Integer enemy = 0;
    
    for(Score score : scores)
    {
        if (this.gametype != null && this.gametype.gameType.equals("tdm")) {
            dwc += score.dwcScore;
            enemy += score.enemyScore;
        }
        else if (this.gametype != null && (this.gametype.gameType.equals("ctf") || this.gametype.gameType.equals("sw"))) {
            if(score.dwcScore > score.enemyScore) {
                dwc += 2;
            } else if (score.dwcScore < score.enemyScore) {
                enemy += 2;
            } else {
                enemy += 1;
                dwc += 1;
            }
        }
        else
        {
            dwc += score.dwcScore;
            enemy += score.enemyScore;
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
  
  @Override
  public String toString() {
    return enemy;
  }
}