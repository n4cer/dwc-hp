package models;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.ebean.Model;

public class ServerPlayersList extends Model {
  @JsonProperty("frags")
  public String frags;
  @JsonProperty("ping")
  public String ping;
  @JsonProperty("name")
  public String name;
}