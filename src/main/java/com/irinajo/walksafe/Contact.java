package com.irinajo.walksafe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_safe_contacts")
public class Contact {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String name;
    private String phone;

    @Column(name = "is_default")
    private boolean isDefault;

    /** Long random token in the contact's personal link. Never changes unless the user resets it. */
    @Column(name = "link_token")
    private String linkToken;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Contact() {}

    public Contact(UUID userId, String name, String phone, String linkToken) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.linkToken = linkToken;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getPhone() { return phone; }
    public void setPhone(String p) { this.phone = p; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean d) { this.isDefault = d; }
    public String getLinkToken() { return linkToken; }
    public void setLinkToken(String t) { this.linkToken = t; }
    public Instant getCreatedAt() { return createdAt; }
}
