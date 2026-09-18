package models;

import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "videos")
public class Video extends Model {
  public static final Finder<Long, Video> find = new Finder<>(Video.class);

  @Id
  private Long id;
  @Constraints.Required
  private String title;
  @Column(columnDefinition = "TEXT")
  private String description;
  private Integer releaseYear;
  /** File name of the web-playable MP4 inside the video folder's web/ subfolder. */
  @Constraints.Required
  private String file;
  /** File name of the preview image inside the video folder's posters/ subfolder. */
  private String poster;
  /** File name of the untouched original inside the video folder, offered as a download. */
  private String originalFile;
  private Integer durationSeconds;
  private Integer sortOrder;
  private boolean published;
  @ManyToOne
  private User author;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getReleaseYear() {
    return releaseYear;
  }

  public void setReleaseYear(Integer releaseYear) {
    this.releaseYear = releaseYear;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public String getPoster() {
    return poster;
  }

  public void setPoster(String poster) {
    this.poster = poster;
  }

  public String getOriginalFile() {
    return originalFile;
  }

  public void setOriginalFile(String originalFile) {
    this.originalFile = originalFile;
  }

  public Integer getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(Integer durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer sortOrder) {
    this.sortOrder = sortOrder;
  }

  public boolean isPublished() {
    return published;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public User getAuthor() {
    return author;
  }

  public void setAuthor(User author) {
    this.author = author;
  }

  /** Runtime as m:ss, or an empty string when no duration was entered. */
  public String getFormattedDuration() {
    if (durationSeconds == null || durationSeconds <= 0) return "";
    return durationSeconds / 60 + ":" + String.format("%02d", durationSeconds % 60);
  }

  @Override
  public String toString() {
    return getTitle();
  }
}
