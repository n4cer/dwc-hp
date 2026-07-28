package models;

import jakarta.persistence.*;

import io.ebean.*;

@Entity
@Table(name = "lineup_squads")
public class UserSquad extends Model {
  public static final Finder<Long, UserSquad> find = new Finder<>(UserSquad.class);

  @Id
  private Long id;
  @ManyToOne
  private User member;
  @ManyToOne
  private Squad squad;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getMember() {
    return member;
  }

  public void setMember(User member) {
    this.member = member;
  }

  public Squad getSquad() {
    return squad;
  }

  public void setSquad(Squad squad) {
    this.squad = squad;
  }
}
