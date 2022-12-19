package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "maps")
public class Map extends Model {
  public static final Finder<Long, Map> find = new Finder<>(Map.class);

  @Id
  private Long id;
  @Constraints.Required
  private String map;
  @ManyToOne
  private Game game;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getMap() {
    return map;
  }

  public void setMap(String map) {
    this.map = map;
  }

  public Game getGame() {
    return game;
  }

  public void setGame(Game game) {
    this.game = game;
  }

  @Override
  public String toString() {
    return getMap();
  }
}