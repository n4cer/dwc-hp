package models;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import play.api.Play;
import play.cache.SyncCacheApi;

import com.typesafe.config.Config;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

public class Util {
  
  @SuppressWarnings({"unchecked", "deprecation"})
  public static List<Server> getServers() {
    SyncCacheApi cache = Play.current().injector().instanceOf(SyncCacheApi.class);
    List<Server> servers = (List<Server>) cache.get("serverObjects");
    if (servers == null) {
      servers = new ArrayList<>();
      Config config = Play.current().injector().instanceOf(Config.class);
      
      List<String> serverUrls = (List<String>) config.getStringList("servers");
      
      for (String serverUrl : serverUrls) {
        servers.add(Util.getServer(serverUrl));
      }
      cache.set("serverObjects", servers, 30);
    }
    
    return servers;
  }
  
  private static Server getServer(String url) {
    Server server = null;
    WSClient ws = Play.current().injector().instanceOf(WSClient.class);
    WSRequest holder = ws.url(url);
    holder.setContentType("application/json");
    WSResponse response;
    try {
      CompletionStage<WSResponse> responsePromise = holder.get();
      response = responsePromise.toCompletableFuture().get(5, TimeUnit.SECONDS);
      JsonNode json = response.asJson();
      server = Json.fromJson(json, Server.class);
      
    } catch(Exception e) {
      
    }
    
    return server;
  }
}