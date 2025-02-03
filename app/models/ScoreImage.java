package models;


import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "score_images")
public class ScoreImage extends Model {
  public static final Finder<Long, ScoreImage> find = new Finder<>(ScoreImage.class);

  @Id
  private Long id;
  @Constraints.Required
  @ManyToOne
  private Score score;
  private String image;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Score getScore() {
    return score;
  }

  public void setScore(Score score) {
    this.score = score;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }
}