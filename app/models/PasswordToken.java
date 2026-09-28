package models;

import java.util.Date;

import jakarta.persistence.*;

import io.ebean.*;

/**
 * One-time link that lets a member set their password. Only the SHA-256 of the
 * token is stored, so a database dump does not hand out working links.
 */
@Entity
@Table(name = "password_tokens")
public class PasswordToken extends Model {
  public static final Finder<Long, PasswordToken> find = new Finder<>(PasswordToken.class);

  @Id
  private Long id;
  @ManyToOne
  private User member;
  private String tokenHash;
  private Date expiresAt;
  @ManyToOne
  private User createdBy;

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

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Date getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Date expiresAt) {
    this.expiresAt = expiresAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public boolean isExpired() {
    return expiresAt == null || !expiresAt.after(new Date());
  }

  public static PasswordToken openFor(User member) {
    return find.query().where().eq("member.id", member.getId()).findOne();
  }
}
