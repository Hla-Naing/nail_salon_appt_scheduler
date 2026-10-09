package com.example.nail_salon_appt_scheduler;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalonRepository {
    private final JdbcTemplate jdbc;

    public SalonRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Choice(Long id, String name) {}

    public List<Choice> providers() {
        return jdbc.query("SELECT p.provider_id, u.name FROM providers p JOIN users u ON u.user_id=p.user_id ORDER BY u.name",
                (rs, n) -> new Choice(rs.getLong(1), rs.getString(2)));
    }

    public List<Choice> services() {
        return jdbc.query("SELECT service_id, name FROM services ORDER BY name",
                (rs, n) -> new Choice(rs.getLong(1), rs.getString(2)));
    }

    public int countServices() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM services", Integer.class);
    }

    public int countProviders() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM providers", Integer.class);
    }

    public List<SlotDto> findAvailableSlots(
        Long providerId,
        Long serviceId,
        java.time.LocalDate date,
        int limit,
        long offset) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.slot_id,
                    u.name AS provider_name,
                    s.name AS service_name,
                    s.duration_minutes,
                    s.price,
                    a.start_at,
                    a.end_at
                FROM availability_slots a
                JOIN providers p
                    ON p.provider_id = a.provider_id
                JOIN users u
                    ON u.user_id = p.user_id
                JOIN services s
                    ON s.service_id = a.service_id
                WHERE a.start_at > CURRENT_TIMESTAMP AND a.removed_at IS NULL
                AND NOT EXISTS (
                        SELECT 1
                        FROM appointments ap
                        WHERE ap.slot_id = a.slot_id
                        AND ap.status = 'BOOKED'
                )
                """);

        List<Object> params = new java.util.ArrayList<>();

        if (providerId != null) {
            sql.append(" AND a.provider_id = ?");
            params.add(providerId);
        }

        if (serviceId != null) {
            sql.append(" AND a.service_id = ?");
            params.add(serviceId);
        }

        if (date != null) {
            sql.append(" AND (a.start_at AT TIME ZONE 'America/Los_Angeles')::date = ?");
            params.add(date);
        }

        sql.append("\n");
        sql.append("""
                ORDER BY a.start_at, a.slot_id
                LIMIT ?
                OFFSET ?
                """);

        params.add(limit);
        params.add(offset);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new SlotDto(
                        rs.getLong("slot_id"),
                        rs.getString("provider_name"),
                        rs.getString("service_name"),
                        rs.getInt("duration_minutes"),
                        rs.getDouble("price"),
                        rs.getObject(
                                "start_at",
                                java.time.OffsetDateTime.class
                        ),
                        rs.getObject(
                                "end_at",
                                java.time.OffsetDateTime.class
                        )
                ),
                params.toArray()
        );
    }

    
}
