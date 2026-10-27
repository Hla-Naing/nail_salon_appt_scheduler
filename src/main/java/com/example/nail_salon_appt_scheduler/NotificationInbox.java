package com.example.nail_salon_appt_scheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class NotificationInbox {
    private final JdbcTemplate jdbc;
    public NotificationInbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** PostgreSQL's unique key arbitrates concurrent retries, including across app instances. */
    @Transactional
    public boolean accept(NotificationRequest request) {
        String hash = fingerprint(request);
        int inserted = jdbc.update("""
                INSERT INTO notification_receipts(notification_id, payload_hash)
                VALUES (?, ?) ON CONFLICT (notification_id) DO NOTHING
                """, request.notificationId(), hash);
        if (inserted == 1) return true;
        String existing = jdbc.queryForObject("SELECT payload_hash FROM notification_receipts WHERE notification_id = ?",
                String.class, request.notificationId());
        if (!hash.equals(existing)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Notification ID already used for a different payload");
        }
        return false;
    }

    private String fingerprint(NotificationRequest request) {
        // Length prefixes avoid ambiguous field boundaries without retaining recipient/message text.
        String payload = request.recipient().length() + ":" + request.recipient()
                + request.type().length() + ":" + request.type() + request.message().length() + ":" + request.message();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
