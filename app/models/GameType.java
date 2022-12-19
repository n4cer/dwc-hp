package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "gametype")
public class GameType extends Model {
  @Id
  private Long id;
  @Constraints.Required
  private String gameType;

  public static final Finder<Long, GameType> find = new Finder<>(GameType.class);

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getGameType() {
    return gameType;
  }

  public void setGameType(String gameType) {
    this.gameType = gameType;
  }

  public String toString() {
    return getGameType();
  }
}