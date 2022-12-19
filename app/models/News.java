package models;

import java.util.Date;

import javax.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@MappedSuperclass
@Entity
@Table(name = "news")
public class News extends Model {
  private static final long serialVersionUID = 1L;

  @Id
  private Long id;
  @Constraints.Required
  private String topic;
  @Column(columnDefinition = "TEXT")
  private String content;
  private Date timestamp;
  @Constraints.Required
  @ManyToOne
  private User username;

  public static final Finder<Long, News> find = new Finder<Long, News>(News.class);

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
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

  public String toString() {
    return getTopic();
  }
}