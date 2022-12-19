package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "squads")
public class Squad extends Model {
  public static final Finder<Long, Squad> find = new Finder<Long, Squad>(Squad.class);

  @Id
  private Long id;
  @Column(name = "short")
  private String shortText;
  @Constraints.Required
  private String description;
  private Integer game;

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

  public Integer getGame() {
    return game;
  }

  public void setGame(Integer game) {
    this.game = game;
  }

  public String toString() {
    return getShortText();
  }
}