package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReminderRepository {
    private final JdbcTemplate jdbc;

    ReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Claim> claim(OffsetDateTime now, OffsetDateTime until, Long providerUserId) {
        UUID claimId = UUID.randomUUID();
        String providerFilter = providerUserId == null ? "" : " AND p.user_id = ?";
        var args = new java.util.ArrayList<Object>(java.util.List.of(now, until, now));
        if (providerUserId != null) args.add(providerUserId);
        args.add("reminder-" + UUID.randomUUID());
        args.add(claimId);
        args.add(now.plusMinutes(5));
        args.add(now);

        return jdbc.query("""
                WITH candidate AS (
                    SELECT a.appointment_id, a.customer_id, s.name AS service_name, av.start_at
                    FROM appointments a
                    JOIN availability_slots av ON av.slot_id = a.slot_id
                    JOIN services s ON s.service_id = av.service_id
                    JOIN providers p ON p.provider_id = av.provider_id
                    LEFT JOIN appointment_reminders r ON r.appointment_id = a.appointment_id
                    WHERE a.status = 'BOOKED' AND av.start_at > ? AND av.start_at <= ?
                      AND (r.appointment_id IS NULL OR
                          (r.sent_at IS NULL AND r.attempts < 5 AND r.available_at <= ?))
                """ + providerFilter + """
                    ORDER BY av.start_at, a.appointment_id
                    LIMIT 1
                ), claimed AS (
                    INSERT INTO appointment_reminders
                        (appointment_id, notification_id, claim_id, available_at, attempts)
                    SELECT appointment_id, ?, ?, ?, 1 FROM candidate
                    ON CONFLICT (appointment_id) DO UPDATE
                    SET claim_id = EXCLUDED.claim_id, available_at = EXCLUDED.available_at,
                        attempts = appointment_reminders.attempts + 1
                    WHERE appointment_reminders.sent_at IS NULL
                      AND appointment_reminders.attempts < 5 AND appointment_reminders.available_at <= ?
                    RETURNING appointment_id, notification_id, claim_id, message
                )
                SELECT c.*, a.customer_id, a.service_name, a.start_at
                FROM claimed c JOIN candidate a USING (appointment_id)
                """, (rs, n) -> new Claim(rs.getLong("appointment_id"), rs.getLong("customer_id"),
                rs.getString("service_name"), rs.getObject("start_at", OffsetDateTime.class),
                rs.getString("notification_id"), rs.getObject("claim_id", UUID.class), rs.getString("message")),
                args.toArray()).stream().findFirst();
    }

    boolean persistPayloadIfUpcoming(Claim claim, String message, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE appointment_reminders r SET message = ?
                FROM appointments a JOIN availability_slots av ON a.slot_id = av.slot_id
                WHERE r.appointment_id = a.appointment_id AND r.appointment_id = ? AND r.claim_id = ?
                  AND r.available_at > ? AND r.sent_at IS NULL AND a.status = 'BOOKED' AND av.start_at > ?
                """, message, claim.appointmentId(), claim.claimId(), now, now) == 1;
    }

    void sent(Claim claim, OffsetDateTime now) {
        jdbc.update("""
                UPDATE appointment_reminders SET sent_at = ?, message = NULL
                WHERE appointment_id = ? AND claim_id = ?
                """, now, claim.appointmentId(), claim.claimId());
    }

    void retryLater(Claim claim, OffsetDateTime retryAt) {
        jdbc.update("""
                UPDATE appointment_reminders SET available_at = ?
                WHERE appointment_id = ? AND claim_id = ? AND sent_at IS NULL
                """, retryAt, claim.appointmentId(), claim.claimId());
    }

    record Claim(long appointmentId, long customerId, String service, OffsetDateTime startsAt,
                 String notificationId, UUID claimId, String message) {}
}
