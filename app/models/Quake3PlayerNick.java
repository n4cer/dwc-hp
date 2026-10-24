package models;

import jakarta.persistence.*;

import io.ebean.*;

/** One nick from the games.log, without colour codes; each nick belongs to one player at most. */
@Entity
@Table(name = "quake3_player_nicks")
public class Quake3PlayerNick extends Model {
  public static final Finder<Long, Quake3PlayerNick> find = new Finder<>(Quake3PlayerNick.class);

  @Id
  private Long id;
  @ManyToOne
  private Quake3Player player;
  private String nick;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Quake3Player getPlayer() {
    return player;
  }

  public void setPlayer(Quake3Player player) {
    this.player = player;
  }

  public String getNick() {
    return nick;
  }

  public void setNick(String nick) {
    this.nick = nick;
  }
}
