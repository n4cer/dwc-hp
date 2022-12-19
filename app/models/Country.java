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

  public static final Finder<Long, Country> find = new Finder<Long, Country>(Country.class);
  @Id
  private Long id;
  @Constraints.Required
  private String country;
  @Column(name = "short")
  private String shortText;
  private String flag;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getShortText() {
    return shortText;
  }

  public void setShortText(String shortText) {
    this.shortText = shortText;
  }

  public String getFlag() {
    return flag;
  }

  public void setFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return country;
  }
}