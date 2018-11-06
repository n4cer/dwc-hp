package models;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ebean.Model;

public class ServerPlayers extends Model {
    @JsonProperty("online")
    public Integer online;
    @JsonProperty("max")
    public String max;
    @JsonProperty("list")
    public List<ServerPlayersList> list = null;
}