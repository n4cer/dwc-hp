package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "games")
public class Game extends Model {

  public static Finder<Long, Game> find = new Finder<Long, Game>(Game.class);
  @Id
  public Long id;
  @Column(name = "short")
  public String shortText;
  @Constraints.Required
  public String description;

  @Override
  public String toString() {
    return shortText;
  }
}