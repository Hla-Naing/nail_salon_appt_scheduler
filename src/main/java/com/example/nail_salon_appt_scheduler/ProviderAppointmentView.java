package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;

public record ProviderAppointmentView(Long appointmentId, String customerName, String serviceName,
                                      OffsetDateTime startAt, OffsetDateTime endAt, String status) {}
