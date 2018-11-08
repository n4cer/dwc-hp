package models;

import io.ebean.Model;

public class Server extends Model {

  public Boolean status;
  public String hostname;
  public String port;
  public String name;
  public String map;
  public Boolean passwordProtected;
  public String version;
  public String protocol;
  public Boolean cached;
  public ServerPlayers players;
  
  public String getAddress() {
    return this.hostname + ":" + this.port;
  }
  
  @Override
  public String toString() {
    return name;
  }
}