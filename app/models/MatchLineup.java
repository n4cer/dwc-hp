package models;


import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "match_lineup")
public class MatchLineup extends Model {
  public static final Finder<Long, MatchLineup> find = new Finder<>(MatchLineup.class);

  @Id
  private Long id;
  @Constraints.Required
  @ManyToOne
  private Clanwar match;
  @ManyToOne
  private User member;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Clanwar getMatch() {
    return match;
  }

  public void setMatch(Clanwar match) {
    this.match = match;
  }

  public User getMember() {
    return member;
  }

  public void setMember(User member) {
    this.member = member;
  }
}