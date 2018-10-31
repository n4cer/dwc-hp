package models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "countries")
public class Country extends Model {

  public static Finder<Long, Country> find = new Finder<Long, Country>(Country.class);
  @Id
  public Long id;
  @Constraints.Required
  public String country;
  @Column(name = "short")
  public String shortText;
  public String flag;

  @Override
  public String toString() {
    return country;
  }
}