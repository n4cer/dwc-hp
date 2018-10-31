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

  public static Finder<Long, History> find = new Finder<Long, History>(History.class);
  @Id
  public Long id;
  @Constraints.Required
  public String content;
  public Date timestamp;
  @ManyToOne
  public User username;

  @Override
  public String toString() {
    return content;
  }
}