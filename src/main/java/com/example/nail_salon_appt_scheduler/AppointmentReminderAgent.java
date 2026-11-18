package com.example.nail_salon_appt_scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AppointmentReminderAgent {
    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderAgent.class);
    private static final int BATCH_SIZE = 10;
    private final ReminderRepository reminders;
    private final ReminderTextGenerator generator;
    private final NotificationGateway gateway;
    private final Clock clock;
    private final Duration window;

    AppointmentReminderAgent(ReminderRepository reminders, ReminderTextGenerator generator,
                             NotificationGateway gateway, Clock clock,
                             @Value("${salon.ai.reminder.window}") Duration window) {
        this.reminders = reminders;
        this.generator = generator;
        this.gateway = gateway;
        this.clock = clock;
        this.window = window;
    }

    @Scheduled(fixedDelayString = "${salon.ai.reminder.interval}",
               initialDelayString = "${salon.ai.reminder.interval}")
    public void scheduledRun() {
        run(null);
    }

    RunResult run(Long providerUserId) {
        if (!generator.available()) return new RunResult(0, 0, 0, false);

        int attempted = 0;
        int delivered = 0;
        int failed = 0;
        while (attempted < BATCH_SIZE) {
            var now = OffsetDateTime.now(clock);
            var next = reminders.claim(now, now.plus(window), providerUserId);
            if (next.isEmpty()) break;
            attempted++;
            var claim = next.orElseThrow();
            try {
                String message = claim.message() == null ? generator.generate(claim) : claim.message();
                if (!reminders.persistPayloadIfUpcoming(claim, message, OffsetDateTime.now(clock))) continue;
                if (gateway.send(new NotificationRequest(claim.notificationId(), "customer:" + claim.customerId(),
                        "APPOINTMENT_REMINDER", message))) {
                    reminders.sent(claim, OffsetDateTime.now(clock));
                    delivered++;
                    continue;
                }
            } catch (RuntimeException e) {
                // SDK errors can contain provider request details. Record only the exception class.
                log.warn("event=appointment_reminder_failure appointmentId={} cause={}",
                        claim.appointmentId(), e.getClass().getSimpleName());
            }
            failed++;
            reminders.retryLater(claim, OffsetDateTime.now(clock).plusMinutes(5));
        }
        return new RunResult(attempted, delivered, failed, true);
    }

    public record RunResult(int attempted, int delivered, int failed, boolean configured) {}
}
