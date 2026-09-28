package models;

import jakarta.persistence.*;

import io.ebean.*;

@Entity
@Table(name = "lineup_roles")
public class UserRole extends Model {
  public static final Finder<Long, UserRole> find = new Finder<>(UserRole.class);

  @Id
  private Long id;
  @ManyToOne
  private User member;
  @Enumerated(EnumType.STRING)
  private Role role;

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

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }
}
