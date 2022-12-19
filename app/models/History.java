package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "history")
public class History extends Model {

  public static final Finder<Long, History> find = new Finder<>(History.class);
  @Id
  private Long id;
  @Constraints.Required
  private String content;
  private Date timestamp;
  @ManyToOne
  private User username;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public User getUsername() {
    return username;
  }

  public void setUsername(User username) {
    this.username = username;
  }

  @Override
  public String toString() {
    return getContent();
  }
}