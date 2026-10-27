package com.example.nail_salon_appt_scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AppointmentNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);
    private final NotificationGateway gateway;
    private final MeterRegistry metrics;
    public AppointmentNotificationListener(NotificationGateway gateway, MeterRegistry metrics) {
        this.gateway = gateway;
        this.metrics = metrics;
        metrics.counter("salon.bookings.success");
        metrics.counter("salon.cancellations.success");
        metrics.counter("salon.notifications.fallback");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void afterCommit(AppointmentNotification event) {
        var request = event.request();
        // AFTER_COMMIT can still expose bound transaction resources. NOT_SUPPORTED suspends them.
        try {
            boolean booking = request.type().equals("BOOKING_CONFIRMATION");
            metrics.counter(booking ? "salon.bookings.success" : "salon.cancellations.success").increment();
            log.info("event=appointment_committed notificationId={} type={}", request.notificationId(), request.type());
            if (!gateway.send(request)) metrics.counter("salon.notifications.fallback").increment();
        } catch (RuntimeException e) {
            // A committed appointment must remain a success even for an unexpected notification error.
            log.error("event=notification_unexpected_failure notificationId={} cause={}",
                    request.notificationId(), e.getClass().getSimpleName());
        }
    }
}
