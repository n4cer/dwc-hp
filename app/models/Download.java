package models;

import jakarta.persistence.*;

import io.ebean.*;
import play.data.validation.Constraints;

@Entity
@Table(name = "downloads")
public class Download extends Model {
  public static final Finder<Long, Download> find = new Finder<>(Download.class);

  @Id
  private Long id;
  @Constraints.Required
  private String title;
  @Column(columnDefinition = "TEXT")
  private String description;
  /** Free-text grouping shown as a heading on the overview, for example "Configs" or "Demos". */
  private String category;
  /** File name inside the download folder. The bytes themselves never live in the database. */
  @Constraints.Required
  private String file;
  private Integer sortOrder;
  private boolean published;
  /** Filled in by the controller from the file on disk, never stored. */
  @Transient
  private String sizeLabel = "";

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

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
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

  public String getSizeLabel() {
    return sizeLabel;
  }

  public void setSizeLabel(String sizeLabel) {
    this.sizeLabel = sizeLabel == null ? "" : sizeLabel;
  }

  /** Upper-case file extension used as a type badge on the overview, e.g. "PK3". */
  public String getFileType() {
    int separator = file == null ? -1 : file.lastIndexOf('.');
    if (separator <= 0 || separator == file.length() - 1) return "";
    return file.substring(separator + 1).toUpperCase(java.util.Locale.ROOT);
  }

  @Override
  public String toString() {
    return getTitle();
  }
}
