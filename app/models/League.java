package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "leagues")
public class League extends Model {

  public static Finder<Long, League> find = new Finder<Long, League>(League.class);
  @Id
  public Long id;
  @Constraints.Required
  public String league;

  @Override
  public String toString() {
    return league;
  }
}