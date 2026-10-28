package models;


import java.time.Instant;

import jakarta.persistence.*;

import io.ebean.*;

/** A demo n4bot linked to a clan war, recorded once its file was taken over as a {@link ScoreDemo}. */
@Entity
@Table(name = "n4bot_demo_imports")
public class N4botDemoImport extends Model {
  public static final Finder<String, N4botDemoImport> find = new Finder<>(N4botDemoImport.class);

  @Id
  @Column(name = "n4bot_id")
  private String n4botId;
  private Long clanwarId;
  private Instant importedAt;

  public String getN4botId() {
    return n4botId;
  }

  public void setN4botId(String n4botId) {
    this.n4botId = n4botId;
  }

  public Long getClanwarId() {
    return clanwarId;
  }

  public void setClanwarId(Long clanwarId) {
    this.clanwarId = clanwarId;
  }

  public Instant getImportedAt() {
    return importedAt;
  }

  public void setImportedAt(Instant importedAt) {
    this.importedAt = importedAt;
  }
}
