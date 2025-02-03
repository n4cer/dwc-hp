package models;


import java.util.List;

import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "scores")
public class Score extends Model {
  public static final Finder<Long, Score> find = new Finder<>(Score.class);

  @Id
  private Long id;
  @Constraints.Required
  @ManyToOne
  private Clanwar match;
  private Integer dwcScore;
  private Integer enemyScore;
  @ManyToOne
  private Map map;
  @OneToMany(mappedBy="score", cascade=CascadeType.ALL)
  private List<ScoreImage> images;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Clanwar getMatch() {
    return match;
  }

  public void setMatch(Clanwar match) {
    this.match = match;
  }

  public Integer getDwcScore() {
    return dwcScore;
  }

  public void setDwcScore(Integer dwcScore) {
    this.dwcScore = dwcScore;
  }

  public Integer getEnemyScore() {
    return enemyScore;
  }

  public void setEnemyScore(Integer enemyScore) {
    this.enemyScore = enemyScore;
  }

  public Map getMap() {
    return map;
  }

  public void setMap(Map map) {
    this.map = map;
  }

  public List<ScoreImage> getImages() {
    return images;
  }

  public void setImages(List<ScoreImage> images) {
    this.images = images;
  }
}