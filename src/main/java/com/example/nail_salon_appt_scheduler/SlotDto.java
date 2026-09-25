package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;

public record SlotDto(
    long slotId,
    String providerName,
    String serviceName,
    int durationMinutes,
    double price,
    OffsetDateTime startAt,
    OffsetDateTime endAt
) {}
