package com.irinajo.content;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tips")
public class Tip {
    @Id
    private UUID id;
    private String language;   // en | pcm
    private String category;
    private String title;
    private String body;
    /** Content version at the last change; the app asks for everything newer than what it has. */
    private long version;
    private boolean deleted;

    public Tip() { this.id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }
    public long getVersion() { return version; }
    public void setVersion(long v) { this.version = v; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean v) { this.deleted = v; }
}
