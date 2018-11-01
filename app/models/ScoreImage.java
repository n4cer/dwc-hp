package models;


import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "score_images")
public class ScoreImage extends Model {

  public static Finder<Long, ScoreImage> find = new Finder<Long, ScoreImage>(ScoreImage.class);
  @Id
  public Long id;
  @Constraints.Required
  @ManyToOne
  public Score score;
  public String image;
}