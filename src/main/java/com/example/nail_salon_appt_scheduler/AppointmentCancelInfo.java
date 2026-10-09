package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;

public record AppointmentCancelInfo(
        Long appointmentId,
        Long customerId,
        OffsetDateTime startAt,
        String status
) {}

