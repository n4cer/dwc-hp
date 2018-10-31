package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "squads")
public class Squad extends Model {

  public static Finder<Long, Squad> find = new Finder<Long, Squad>(Squad.class);
  @Id
  public Long id;
  @Column(name = "short")
  public String shortText;
  @Constraints.Required
  public String description;
  public Integer game;

  @Override
  public String toString() {
    return shortText;
  }
}