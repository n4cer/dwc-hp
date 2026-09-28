package models;

/**
 * Permissions a member can be granted on top of maintaining their own profile.
 * Stored by name in lineup_roles, so renaming a constant needs an evolution.
 */
public enum Role {
  ADMIN("Administrator", "Everything, including roles and password links"),
  NEWS("News", "Write and edit news"),
  CLANWARS("Clanwars", "Clanwars, maps, leagues, games and game types"),
  LINEUP("Lineup", "Members and squads, except roles and administrators"),
  QUAKE3("Quake 3 servers", "Start and stop the Quake 3 servers"),
  MEDIA("Media", "Videos and downloads");

  private final String label;
  private final String description;

  Role(String label, String description) {
    this.label = label;
    this.description = description;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  /** ADMIN implies every other role. */
  public boolean grants(Role required) {
    return this == ADMIN || this == required;
  }
}
