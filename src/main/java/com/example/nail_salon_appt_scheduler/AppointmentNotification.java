package com.example.nail_salon_appt_scheduler;

/** Immutable snapshot built inside the transaction, containing no account credentials. */
public record AppointmentNotification(NotificationRequest request) {}
