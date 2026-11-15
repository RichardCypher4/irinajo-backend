package com.irinajo.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A recorded voice pack (Opus, ~1.2 MB) hosted on the CDN. Re-recording bumps the version. */
@Entity
@Table(name = "voice_packs")
public class VoicePack {
    @Id
    private UUID id;
    private String speaker;     // ada | tunde
    private String language;    // en | pcm
    private int version;
    @Column(name = "object_key")
    private String objectKey;
    @Column(name = "size_bytes")
    private long sizeBytes;
    private String sha256;

    public VoicePack() { this.id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public String getSpeaker() { return speaker; }
    public void setSpeaker(String v) { this.speaker = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String v) { this.objectKey = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public String getSha256() { return sha256; }
    public void setSha256(String v) { this.sha256 = v; }
}
