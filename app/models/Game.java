package models;

import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "games")
public class Game extends Model {
  public static final Finder<Long, Game> find = new Finder<>(Game.class);
  @Id
  private Long id;
  @Column(name = "short")
  private String shortText;
  @Constraints.Required
  private String description;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getShortText() {
    return shortText;
  }

  public void setShortText(String shortText) {
    this.shortText = shortText;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String toString() {
    return getShortText();
  }
}