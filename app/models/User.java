package models;

import play.data.validation.Constraints;
import play.data.validation.Constraints.MinLength;

import java.util.Date;
import java.util.List;

import jakarta.persistence.*;

import io.ebean.*;

@Entity
@Table(name = "lineup")
public class User extends Model {
  public static final Finder<Long, User> find = new Finder<>(User.class);

  @Id
  private Long id;
  @Constraints.Required
  @Column(unique = true)
  @MinLength(4)
  private String nick;
  private String realname;
  @Constraints.Email
  @Column(unique = true)
  private String email;
  private Date birthDate;
  private String city;
  private String job;
  private String quote;
  private Date since;
  private Date exitDate;
  private String image;
  @Column(columnDefinition = "boolean default true")
  private Boolean active;
  @Column(columnDefinition = "boolean default false")
  private Boolean founder;
  @Column(columnDefinition = "boolean default false")
  private Boolean clanLeader;
  @Column(columnDefinition = "boolean default false")
  private Boolean honoraryMember;
  @OneToMany(mappedBy="username", cascade=CascadeType.ALL)
  private List<News> news;
  @OneToMany(mappedBy="member", cascade=CascadeType.ALL)
  private List<MatchLineup> lineups;
  @OneToMany(mappedBy="member", cascade=CascadeType.ALL)
  private List<UserSquad> squads;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNick() {
    return nick;
  }

  public void setNick(String nick) {
    this.nick = nick;
  }

  public String getRealname() {
    return realname;
  }

  public void setRealname(String realname) {
    this.realname = realname;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Date getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(Date birthDate) {
    this.birthDate = birthDate;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getJob() {
    return job;
  }

  public void setJob(String job) {
    this.job = job;
  }

  public String getQuote() {
    return quote;
  }

  public void setQuote(String quote) {
    this.quote = quote;
  }

  public Date getSince() {
    return since;
  }

  public void setSince(Date since) {
    this.since = since;
  }

  public Date getExitDate() {
    return exitDate;
  }

  public void setExitDate(Date exitDate) {
    this.exitDate = exitDate;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public List<UserSquad> getSquads() {
    return squads;
  }

  public void setSquads(List<UserSquad> squads) {
    this.squads = squads;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Boolean getFounder() {
    return founder;
  }

  public void setFounder(Boolean founder) {
    this.founder = founder;
  }

  public Boolean getClanLeader() {
    return clanLeader;
  }

  public void setClanLeader(Boolean clanLeader) {
    this.clanLeader = clanLeader;
  }

  public Boolean getHonoraryMember() {
    return honoraryMember;
  }

  public void setHonoraryMember(Boolean honoraryMember) {
    this.honoraryMember = honoraryMember;
  }

  public List<News> getNews() {
    return news;
  }

  public void setNews(List<News> news) {
    this.news = news;
  }

  public List<MatchLineup> getLineups() {
    return lineups;
  }

  public void setLineups(List<MatchLineup> lineups) {
    this.lineups = lineups;
  }

  @Override
  public String toString() {
    return getNick();
  }
}
