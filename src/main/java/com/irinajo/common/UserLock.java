package com.irinajo.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Serialises writes per user so sync sequence numbers commit in order, and makes the
 * "changes since" reader wait for in-flight writes. Without this, a change could be skipped
 * by a phone whose cursor had already moved past it. Must be called inside a transaction.
 */
@Component
public class UserLock {
    private final JdbcTemplate jdbc;

    public UserLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void forWrite(UUID userId) {
        jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE", rs -> {}, userId);
    }

    public void forRead(UUID userId) {
        jdbc.query("SELECT id FROM users WHERE id = ? FOR SHARE", rs -> {}, userId);
    }
}
