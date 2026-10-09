package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Lock an availability slot while booking.
    public Optional<OffsetDateTime> lockSlot(Long slotId) {

        String sql = """
                SELECT start_at
                FROM availability_slots
                WHERE slot_id = ?
                FOR UPDATE
                """;

        List<OffsetDateTime> slots = jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        rs.getObject("start_at", OffsetDateTime.class),
                slotId
        );

        return slots.stream().findFirst();
    }

    // Check whether a slot already has an active booking.
    public boolean isSlotBooked(Long slotId) {

        String sql = """
                SELECT COUNT(*)
                FROM appointments
                WHERE slot_id = ?
                  AND status = 'BOOKED'
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                slotId
        );

        return count != null && count > 0;
    }

    // Create a new appointment.
    public Long createAppointment(Long customerId, Long slotId) {

        String sql = """
                INSERT INTO appointments
                    (customer_id, slot_id, status, booked_at, fee_charged)
                VALUES (?, ?, 'BOOKED', CURRENT_TIMESTAMP, 0)
                RETURNING appointment_id
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                customerId,
                slotId
        );
    }

    // Get all appointments belonging to one customer.
    public List<AppointmentView> findByCustomerId(Long customerId) {

        String sql = """
                SELECT
                    a.appointment_id,
                    a.slot_id,
                    u.name AS provider_name,
                    s.name AS service_name,
                    s.price,
                    av.start_at,
                    av.end_at,
                    a.status,
                    a.fee_charged
                FROM appointments a
                JOIN availability_slots av
                    ON a.slot_id = av.slot_id
                JOIN providers p
                    ON av.provider_id = p.provider_id
                JOIN users u
                    ON p.user_id = u.user_id
                JOIN services s
                    ON av.service_id = s.service_id
                WHERE a.customer_id = ?
                ORDER BY av.start_at
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AppointmentView(
                        rs.getLong("appointment_id"),
                        rs.getLong("slot_id"),
                        rs.getString("provider_name"),
                        rs.getString("service_name"),
                        rs.getBigDecimal("price"),
                        rs.getObject("start_at", OffsetDateTime.class),
                        rs.getObject("end_at", OffsetDateTime.class),
                        rs.getString("status"),
                        rs.getBigDecimal("fee_charged")
                ),
                customerId
        );
    }

    // Lock an appointment before cancelling it.
    // customer_id makes cancellation owner-only.
    public Optional<AppointmentCancelInfo> lockCustomerAppointment(
            Long appointmentId,
            Long customerId) {

        String sql = """
                SELECT
                    a.appointment_id,
                    a.customer_id,
                    av.start_at,
                    a.status
                FROM appointments a
                JOIN availability_slots av
                    ON a.slot_id = av.slot_id
                WHERE a.appointment_id = ?
                  AND a.customer_id = ?
                FOR UPDATE OF a
                """;

        List<AppointmentCancelInfo> appointments =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> new AppointmentCancelInfo(
                                rs.getLong("appointment_id"),
                                rs.getLong("customer_id"),
                                rs.getObject(
                                        "start_at",
                                        OffsetDateTime.class
                                ),
                                rs.getString("status")
                        ),
                        appointmentId,
                        customerId
                );

        return appointments.stream().findFirst();
    }

    // Change appointment status to CANCELLED.
    public void cancelAppointment(
            Long appointmentId,
            BigDecimal fee) {

        String sql = """
                UPDATE appointments
                SET status = 'CANCELLED',
                    cancelled_at = CURRENT_TIMESTAMP,
                    fee_charged = ?
                WHERE appointment_id = ?
                """;

        jdbcTemplate.update(
                sql,
                fee,
                appointmentId
        );
    }
}