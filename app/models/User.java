package models;

import play.data.validation.Constraints;
import play.data.validation.Constraints.MinLength;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.mindrot.jbcrypt.BCrypt;

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
  private String password;
  private String realname;
  @Constraints.Email
  @Column(unique = true)
  private String email;
  private Date birthDate;
  private String city;
  private String job;
  private String quote;
  private Date since;
  private String image;
  private Integer squad;
  @Column(columnDefinition = "boolean default false")
  private Boolean notits;
  private Integer type;
  @Column(columnDefinition = "boolean default true")
  private Boolean active;
  @OneToMany(mappedBy="username", cascade=CascadeType.ALL)
  private List<News> news;
  @OneToMany(mappedBy="member", cascade=CascadeType.ALL)
  private List<MatchLineup> lineups;

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

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
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

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public Integer getSquad() {
    return squad;
  }

  public void setSquad(Integer squad) {
    this.squad = squad;
  }

  public Boolean getNotits() {
    return notits;
  }

  public void setNotits(Boolean notits) {
    this.notits = notits;
  }

  public Integer getType() {
    return type;
  }

  public void setType(Integer type) {
    this.type = type;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
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
  
  /**
   * Checks username and password
   * 
   * @param userName
   * @param password
   * @return User
   */
  public static User authenticate(String userName, String password) {
    User user = User.find.query().where().eq("name", userName).findOne();
    if (user != null && BCrypt.checkpw(password, user.password)) {
      return user;
    } else {
      return null;
    }
  }

  /**
   * Create new user with password hash
   * 
   * @param userName
   * @param password
   * @return User
   */
  public static User create(String userName, String password) {
    User user = new User();
    user.nick = userName;
    user.password = BCrypt.hashpw(password, BCrypt.gensalt());
    user.active = true;
    user.save();
    
    return user;
  }
  
  public static User mockCreate(String userName, String password, String realname, String image, Integer squad) {
	    User user = new User();
	    user.nick = userName;
	    user.password = BCrypt.hashpw(password, BCrypt.gensalt());
	    user.realname = realname;
	    user.image = image;
	    user.squad = squad;
	    user.type = 0;
	    user.active = true;
	    user.save();
	    
	    return user;
	  }
  
  public void changePassword(String password) {
    this.password = BCrypt.hashpw(password, BCrypt.gensalt());
    this.save();
  }
}