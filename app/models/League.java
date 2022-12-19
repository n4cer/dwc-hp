package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "leagues")
public class League extends Model {

  public static final Finder<Long, League> find = new Finder<>(League.class);

  @Id
  private Long id;
  @Constraints.Required
  private String league;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getLeague() {
    return league;
  }

  public void setLeague(String league) {
    this.league = league;
  }

  public String toString() {
    return getLeague();
  }
}