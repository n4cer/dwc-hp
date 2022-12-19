package models;

public class Util {
  public static String q3ColorCodeToHtml(String q3Nick) {
    String htmlNick = "";
    
    if(!q3Nick.startsWith("^")) {
      q3Nick = "^0" + q3Nick;
    }
    
    String[] splits = q3Nick.split("\\^");
    if (splits.length > 1) {
      for (int i = 1; i <= splits.length - 1; i++) {
        String colorCode = splits[i].substring(0, 1);
        String text = splits[i].substring(1);
        if(text.length() > 0) {
          htmlNick += "<span class=\"q3 " + getQ3Color(colorCode) + "\">" + text + "</span>";
        }
      }
    } else {
      htmlNick = q3Nick;
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
