package models;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "news")
public class News extends Model {

  public static Finder<Long, News> find = new Finder<Long, News>(News.class);
  @Id
  public Long id;
  @Constraints.Required
  public String topic;
  @Column(columnDefinition = "TEXT")
  public String content;
  public Date timestamp;
  @Constraints.Required
  @ManyToOne
  public User username;

  @Override
  public String toString() {
    return topic;
  }
}