package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "gametype")
public class GameType extends Model {

  public static Finder<Long, GameType> find = new Finder<Long, GameType>(GameType.class);
  @Id
  public Long id;
  @Constraints.Required
  public String gameType;

  @Override
  public String toString() {
    return gameType;
  }
}