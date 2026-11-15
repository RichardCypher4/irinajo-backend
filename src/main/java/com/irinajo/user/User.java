package com.irinajo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One account; may have several sign-in methods linked (phone, Google, Apple). */
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    private String phone;

    @Column(name = "google_sub")
    private String googleSub;

    @Column(name = "apple_sub")
    private String appleSub;

    private String email;

    private String role = "USER";

    @Column(name = "member_since")
    private Instant memberSince;

    protected User() {}

    public static User create() {
        User u = new User();
        u.id = UUID.randomUUID();
        u.memberSince = Instant.now();
        return u;
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public String getAppleSub() { return appleSub; }
    public void setAppleSub(String appleSub) { this.appleSub = appleSub; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public Instant getMemberSince() { return memberSince; }
}
