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
  
  public static String q3ColorCodeToHtml(String q3Nick) {
    String htmlNick = "";
    
    String[] splits = q3Nick.split("\\^");
    if (splits.length > 0) {
      for (int i = 1; i <= splits.length - 1; i++) {
        // ^11
        String colorCode = splits[i].substring(0, 1);
        String text = splits[i].substring(1);
        if(text.length() > 0) {
          htmlNick += "<span class=\"q3 " + getQ3Color(colorCode) + "\">" + text + "</span>";
        }
      }
    }
    
    return htmlNick;
  }
  
  /*
   * 0: black
   * 1: red
   * 2: green
   * 3: yellow
   * 4: blue
   * 5: cyan
   * 6: pink
   * 7: white
   */
  private static String getQ3Color(String color) {
    switch (color) {
      case "0":
        return "black";
      case "1":
        return "red";
      case "2":
        return "green";
      case "3":
        return "yellow";
      case "4":
        return "blue";
      case "5":
        return "cyan";
      case "6":
        return "pink";
      case "7":
        return "white";
      default:
        return "black";
    }
  }
}