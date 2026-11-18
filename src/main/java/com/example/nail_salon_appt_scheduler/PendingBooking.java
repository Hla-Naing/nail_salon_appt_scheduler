package com.example.nail_salon_appt_scheduler;

import java.time.Instant;

public record PendingBooking(
        Long customerId,
        Long slotId,
        Instant createdAt
) {
}