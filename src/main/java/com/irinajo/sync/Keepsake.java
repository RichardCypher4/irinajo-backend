package com.irinajo.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Earned on the phone. Add-only: the server never removes an earned keepsake. */
@Entity
@Table(name = "keepsakes")
@IdClass(Keepsake.Key.class)
public class Keepsake {

    public static class Key implements Serializable {
        private UUID userId;
        private String keepsakeId;

        public Key() {}
        public Key(UUID userId, String keepsakeId) { this.userId = userId; this.keepsakeId = keepsakeId; }

        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(keepsakeId, k.keepsakeId);
        }
        @Override public int hashCode() { return Objects.hash(userId, keepsakeId); }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "keepsake_id")
    private String keepsakeId;

    @Column(name = "earned_at")
    private long earnedAt;

    private long seq;

    protected Keepsake() {}

    public UUID getUserId() { return userId; }
    public String getKeepsakeId() { return keepsakeId; }
    public long getEarnedAt() { return earnedAt; }
    public long getSeq() { return seq; }
}
