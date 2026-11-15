package com.irinajo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
    @Id
    private String phone;

    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    private int attempts;

    private String channel;

    protected OtpChallenge() {}

    public OtpChallenge(String phone) { this.phone = phone; }

    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String h) { this.codeHash = h; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant t) { this.expiresAt = t; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant t) { this.lastSentAt = t; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int a) { this.attempts = a; }
    public String getChannel() { return channel; }
    public void setChannel(String c) { this.channel = c; }
}
