package models;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import io.ebean.*;

/**
 * Someone who played on the Quake 3 servers under several nicks. The statistics
 * show them as one player: under the member's nick when they belong to the lineup,
 * otherwise under the name given here.
 */
@Entity
@Table(name = "quake3_players")
public class Quake3Player extends Model {
  public static final Finder<Long, Quake3Player> find = new Finder<>(Quake3Player.class);

  @Id
  private Long id;
  private String name;
  @ManyToOne
  private User member;
  @OneToMany(mappedBy = "player", cascade = CascadeType.ALL)
  @jakarta.persistence.OrderBy("nick")
  private List<Quake3PlayerNick> nicks = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public User getMember() {
    return member;
  }

  public void setMember(User member) {
    this.member = member;
  }

  public List<Quake3PlayerNick> getNicks() {
    return nicks;
  }

  public void setNicks(List<Quake3PlayerNick> nicks) {
    this.nicks = nicks;
  }

  /** The member's nick wins, then the name; a member that was deleted leaves only the nicks. */
  public String displayName() {
    if (member != null) return member.getNick();
    if (name != null && !name.isBlank()) return name;
    return nicks.isEmpty() ? "?" : nicks.get(0).getNick();
  }

  public List<String> nickNames() {
    return nicks.stream().map(Quake3PlayerNick::getNick).toList();
  }
}
