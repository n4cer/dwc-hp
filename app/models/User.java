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

  public static Finder<Long, User> find = new Finder<Long, User>(User.class);
  @Id
  public Long id;
  @Constraints.Required
  @Column(unique = true)
  @MinLength(4)
  public String nick;
  public String password;
  public String realname;
  @Constraints.Email
  @Column(unique = true)
  public String email;
  public Date birthDate;
  public String city;
  public String job;
  public String quote;
  public Date since;
  public String image;
  public Integer squad;
  @Column(columnDefinition = "boolean default false")
  public Boolean notits;
  public Integer type;
  @Column(columnDefinition = "boolean default true")
  public Boolean active;
  @OneToMany(mappedBy="username", cascade=CascadeType.ALL)
  public List<News> news;
  @OneToMany(mappedBy="member", cascade=CascadeType.ALL)
  public List<MatchLineup> lineups;

  @Override
  public String toString() {
    return nick;
  }
  
  /**
   * Checks username and password
   * 
   * @param userName
   * @param password
   * @return User
   */
  public static User authenticate(String userName, String password) {
    User user = User.find.query().where().eq("name", userName).findUnique();
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