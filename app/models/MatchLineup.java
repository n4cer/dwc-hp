package models;


import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "match_lineup")
public class MatchLineup extends Model {

  public static Finder<Long, MatchLineup> find = new Finder<Long, MatchLineup>(MatchLineup.class);
  @Id
  public Long id;
  @Constraints.Required
  @ManyToOne
  public Clanwar match;
  @ManyToOne
  public User member;
}