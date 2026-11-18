package com.example.nail_salon_appt_scheduler;

import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ReminderTextGenerator {
    private final Optional<ChatClient> client;
    private final SalonTime salonTime;

    ReminderTextGenerator(Optional<ChatClient> client, SalonTime salonTime) {
        this.client = client;
        this.salonTime = salonTime;
    }

    boolean available() {
        return client.isPresent();
    }

    String generate(ReminderRepository.Claim claim) {
        String startsAt = salonTime.display(claim.startsAt());
        String greeting = client.orElseThrow().prompt()
                .templateRenderer(new NoOpTemplateRenderer())
                .system("""
                        Write a short, friendly greeting for this appointment reminder, as plain text.
                        One sentence of at most 160 characters. The application supplies the appointment details
                        separately. Do not repeat them or add policies, instructions, promises, amounts or links.
                        """)
                .user("Salon service: " + claim.service())
                .call().content();
        if (!StringUtils.hasText(greeting) || greeting.length() > 300) {
            throw new IllegalStateException("Gemini did not return a usable reminder greeting");
        }
        return "%s Reminder: your %s appointment starts %s."
                .formatted(greeting.strip(), claim.service(), startsAt);
    }
}
