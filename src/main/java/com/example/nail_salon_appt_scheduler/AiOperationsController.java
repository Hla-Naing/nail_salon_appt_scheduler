package com.example.nail_salon_appt_scheduler;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/ai")
class AiOperationsController {
    private static final Logger log = LoggerFactory.getLogger(AiOperationsController.class);
    private final AppointmentReminderAgent reminders;
    private final SalonKnowledgeBase knowledge;
    private final SessionAuthService auth;

    AiOperationsController(AppointmentReminderAgent reminders, SalonKnowledgeBase knowledge, SessionAuthService auth) {
        this.reminders = reminders;
        this.knowledge = knowledge;
        this.auth = auth;
    }

    // The custom header prevents a browser from sending a cross-origin form POST with the session cookie.
    @PostMapping("/reminders/run")
    ResponseEntity<AppointmentReminderAgent.RunResult> runReminders(
            @RequestHeader("X-Salon-AI") String operationHeader, HttpServletRequest request) {
        var provider = auth.requireRole(request, "PROVIDER");
        var result = reminders.run(provider.userId());
        return ResponseEntity.status(result.configured() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    @PostMapping("/knowledge/reload")
    ResponseEntity<Map<String, ?>> reloadKnowledge(
            @RequestHeader("X-Salon-AI") String operationHeader, HttpServletRequest request) {
        auth.requireRole(request, "PROVIDER");
        try {
            return ResponseEntity.ok(Map.of("documents", knowledge.reload()));
        } catch (RuntimeException | IOException e) {
            log.warn("event=salon_knowledge_reload_failed cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "The salon knowledge index could not be rebuilt. Try again later."));
        }
    }
}
