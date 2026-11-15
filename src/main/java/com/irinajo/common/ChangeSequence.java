package com.irinajo.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hands out the next value of the global change counter used as the sync cursor. */
@Component
public class ChangeSequence {
    private final JdbcTemplate jdbc;

    public ChangeSequence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long next() {
        Long v = jdbc.queryForObject("SELECT nextval('change_seq')", Long.class);
        return v == null ? 0 : v;
    }

    public long nextContentVersion() {
        Long v = jdbc.queryForObject("SELECT nextval('content_version_seq')", Long.class);
        return v == null ? 0 : v;
    }
}
