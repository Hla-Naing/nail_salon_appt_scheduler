package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderRepository {
    private final JdbcTemplate jdbc;
    public ProviderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Long> findProviderId(Long userId) {
        return jdbc.query("SELECT provider_id FROM providers WHERE user_id = ?",
                (rs, n) -> rs.getLong(1), userId).stream().findFirst();
    }

    public boolean serviceExists(Long serviceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM services WHERE service_id = ?)", Boolean.class, serviceId));
    }

    public Long createSlot(Long providerId, Long serviceId, OffsetDateTime start, OffsetDateTime end) {
        return jdbc.queryForObject("""
                INSERT INTO availability_slots(provider_id, service_id, start_at, end_at)
                VALUES (?, ?, ?, ?) RETURNING slot_id
                """, Long.class, providerId, serviceId, start, end);
    }

    public boolean lockOwnedSlot(Long slotId, Long providerId) {
        return !jdbc.query("""
                SELECT slot_id FROM availability_slots
                WHERE slot_id = ? AND provider_id = ? AND removed_at IS NULL FOR UPDATE
                """, (rs, n) -> rs.getLong(1), slotId, providerId).isEmpty();
    }

    public void removeSlot(Long slotId) {
        jdbc.update("UPDATE availability_slots SET removed_at = CURRENT_TIMESTAMP WHERE slot_id = ?", slotId);
    }

    public List<ProviderSlotView> slots(Long providerId) {
        return jdbc.query("""
                SELECT av.slot_id, s.name, av.start_at, av.end_at,
                       EXISTS(SELECT 1 FROM appointments a WHERE a.slot_id = av.slot_id AND a.status = 'BOOKED') AS booked
                FROM availability_slots av JOIN services s ON s.service_id = av.service_id
                WHERE av.provider_id = ? AND av.removed_at IS NULL AND av.start_at > CURRENT_TIMESTAMP
                ORDER BY av.start_at, av.slot_id
                """, (rs, n) -> new ProviderSlotView(rs.getLong("slot_id"), rs.getString("name"),
                rs.getObject("start_at", OffsetDateTime.class), rs.getObject("end_at", OffsetDateTime.class),
                rs.getBoolean("booked")), providerId);
    }

    public List<ProviderAppointmentView> appointments(Long providerId) {
        return jdbc.query("""
                SELECT a.appointment_id, u.name AS customer_name, s.name AS service_name,
                       av.start_at, av.end_at, a.status
                FROM appointments a JOIN users u ON u.user_id = a.customer_id
                JOIN availability_slots av ON av.slot_id = a.slot_id
                JOIN services s ON s.service_id = av.service_id
                WHERE av.provider_id = ? ORDER BY av.start_at, a.appointment_id
                """, (rs, n) -> new ProviderAppointmentView(rs.getLong("appointment_id"),
                rs.getString("customer_name"), rs.getString("service_name"),
                rs.getObject("start_at", OffsetDateTime.class), rs.getObject("end_at", OffsetDateTime.class),
                rs.getString("status")), providerId);
    }
}
