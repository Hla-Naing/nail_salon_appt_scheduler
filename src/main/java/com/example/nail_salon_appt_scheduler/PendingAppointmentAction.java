package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

record PendingAppointmentAction(long customerId, Confirmation confirmation) {
    public enum Kind { BOOKING, CANCELLATION }

    public record Details(Kind kind, long targetId, String service, String provider,
                          OffsetDateTime startsAt, BigDecimal amount, String summary) {}

    public record Confirmation(UUID token, Instant expiresAt, Details details) {}
}
