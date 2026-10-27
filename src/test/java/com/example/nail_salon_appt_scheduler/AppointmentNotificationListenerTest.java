package com.example.nail_salon_appt_scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentNotificationListenerTest {
    @Test void unexpectedGatewayBugDoesNotEscapeAfterCommit() {
        var gateway = mock(NotificationGateway.class);
        when(gateway.send(any())).thenThrow(new IllegalStateException("unexpected"));
        var metrics = new SimpleMeterRegistry();
        try {
            var listener = new AppointmentNotificationListener(gateway, metrics);
            assertDoesNotThrow(() -> listener.afterCommit(new AppointmentNotification(new NotificationRequest(
                    "booking-1", "customer:1", "BOOKING_CONFIRMATION", "Confirmed"))));
            assertEquals(1, metrics.get("salon.bookings.success").counter().count());
        } finally { metrics.close(); }
    }
}
