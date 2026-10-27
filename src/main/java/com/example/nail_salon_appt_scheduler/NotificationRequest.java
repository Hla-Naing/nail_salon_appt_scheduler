package com.example.nail_salon_appt_scheduler;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One complete operation; retries reuse the same ID and payload. */
public record NotificationRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String notificationId,
        @NotBlank @Size(max = 100) String recipient,
        @NotBlank @Pattern(regexp = "BOOKING_CONFIRMATION|CANCELLATION_CONFIRMATION") String type,
        @NotBlank @Size(max = 1000) String message) {}
