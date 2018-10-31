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

  public static Finder<Long, Map> find = new Finder<Long, Map>(Map.class);
  @Id
  public Long id;
  @Constraints.Required
  public String map;
  @ManyToOne
  public Game game;

  @Override
  public String toString() {
    return map;
  }
}