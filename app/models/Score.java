package models;


import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "scores")
public class Score extends Model {

  public static Finder<Long, Score> find = new Finder<Long, Score>(Score.class);
  @Id
  public Long id;
  @Constraints.Required
  @ManyToOne
  public Clanwar match;
  public Integer dwcScore;
  public Integer enemyScore;
  @ManyToOne
  public Map map;
  @OneToMany(mappedBy="score", cascade=CascadeType.ALL)
  public List<ScoreImage> images;
}