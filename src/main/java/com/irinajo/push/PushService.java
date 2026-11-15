package com.irinajo.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Device push tokens + sending through Expo's push service (which handles FCM and APNs). */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String EXPO_URL = "https://exp.host/--/api/v2/push/send";

    private final JdbcTemplate jdbc;
    private final RestClient http;

    public PushService(JdbcTemplate jdbc, RestClient http) {
        this.jdbc = jdbc;
        this.http = http;
    }

    /** Idempotent: the same token re-registered just moves to the current user. */
    public void register(UUID userId, String token, String platform, String deviceId) {
        jdbc.update("""
                INSERT INTO push_tokens (token, user_id, platform, device_id, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform,
                    device_id = EXCLUDED.device_id, updated_at = now()
                """, token, userId, platform, deviceId);
    }

    public void unregister(UUID userId, String token) {
        jdbc.update("DELETE FROM push_tokens WHERE token = ? AND user_id = ?", token, userId);
    }

    @Async
    public void send(UUID userId, String title, String body, Map<String, Object> data) {
        List<String> tokens = jdbc.queryForList("SELECT token FROM push_tokens WHERE user_id = ?", String.class, userId);
        if (tokens.isEmpty()) return;
        List<Map<String, Object>> messages = new ArrayList<>();
        for (String t : tokens) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("to", t);
            m.put("title", title);
            m.put("body", body);
            m.put("sound", "default");
            m.put("priority", "high");
            m.put("channelId", "walk-safe");
            if (data != null) m.put("data", data);
            messages.add(m);
        }
        try {
            Map<?, ?> res = http.post().uri(EXPO_URL).contentType(MediaType.APPLICATION_JSON)
                    .body(messages).retrieve().body(Map.class);
            pruneDeadTokens(tokens, res);
        } catch (Exception e) {
            log.warn("Expo push failed: {}", e.getMessage());
        }
    }

    /** Removes tokens Expo reports as DeviceNotRegistered. */
    @SuppressWarnings("unchecked")
    private void pruneDeadTokens(List<String> tokens, Map<?, ?> res) {
        if (res == null || !(res.get("data") instanceof List<?> tickets)) return;
        for (int i = 0; i < tickets.size() && i < tokens.size(); i++) {
            if (tickets.get(i) instanceof Map<?, ?> t && "error".equals(t.get("status"))
                    && t.get("details") instanceof Map<?, ?> d && "DeviceNotRegistered".equals(d.get("error"))) {
                jdbc.update("DELETE FROM push_tokens WHERE token = ?", tokens.get(i));
            }
        }
    }
}
