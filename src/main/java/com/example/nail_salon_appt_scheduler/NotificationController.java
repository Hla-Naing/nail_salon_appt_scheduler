package com.example.nail_salon_appt_scheduler;

import java.util.Map;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private final NotificationInbox inbox;
    public NotificationController(NotificationInbox inbox) { this.inbox = inbox; }

    @PostMapping
    public ResponseEntity<?> sendNotification(@Valid @RequestBody NotificationRequest request) {
        boolean received = inbox.accept(request);
        if (received) log.info("event=notification_received notificationId={} type={}", request.notificationId(), request.type());
        return ResponseEntity.ok(Map.of("notificationId", request.notificationId(), "status", received ? "RECEIVED" : "DUPLICATE"));
    }
}
