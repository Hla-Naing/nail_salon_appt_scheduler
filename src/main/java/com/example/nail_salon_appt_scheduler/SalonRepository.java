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

    public int countServices() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM services", Integer.class);
    }

    public int countProviders() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM providers", Integer.class);
    }

    public List<SlotDto> findAvailableSlots() {
        String sql = """
            SELECT a.slot_id, u.name AS provider_name, s.name AS service_name,
                   s.duration_minutes, s.price, a.start_at, a.end_at
            FROM availability_slots a
            JOIN providers p ON p.provider_id = a.provider_id
            JOIN users u ON u.user_id = p.user_id
            JOIN services s ON s.service_id = a.service_id
            WHERE NOT EXISTS (
                SELECT 1 FROM appointments ap
                WHERE ap.slot_id = a.slot_id AND ap.status = 'BOOKED'
            )
            ORDER BY a.start_at
            """;

        return jdbc.query(sql, (rs, rowNum) -> new SlotDto(
            rs.getLong("slot_id"),
            rs.getString("provider_name"),
            rs.getString("service_name"),
            rs.getInt("duration_minutes"),
            rs.getDouble("price"),
            rs.getObject("start_at", java.time.OffsetDateTime.class),
            rs.getObject("end_at", java.time.OffsetDateTime.class)
        ));
    }
}
