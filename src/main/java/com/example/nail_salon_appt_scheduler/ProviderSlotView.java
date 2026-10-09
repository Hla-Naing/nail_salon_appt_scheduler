package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;

public record ProviderSlotView(Long slotId, String serviceName, OffsetDateTime startAt,
                               OffsetDateTime endAt, boolean booked) {}
