package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AppointmentView(
        Long appointmentId,
        Long slotId,
        String providerName,
        String serviceName,
        BigDecimal price,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String status,
        BigDecimal feeCharged
) {}

