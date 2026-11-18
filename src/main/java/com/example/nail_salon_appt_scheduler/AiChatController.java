package com.example.nail_salon_appt_scheduler;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/ai")
public class AiChatController {

    private final ChatClient chatClient;
    private final SessionAuthService sessionAuthService;

    public AiChatController(
            ChatClient.Builder builder,
            AppointmentAiTools appointmentAiTools,
            SessionAuthService sessionAuthService) {

        this.chatClient = builder
                .defaultTools(appointmentAiTools)
                .build();

        this.sessionAuthService = sessionAuthService;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {

        UserAccount customer =
        sessionAuthService.requireRole(httpRequest, "CUSTOMER");

        String response = chatClient.prompt()
        .system("""
            You are a helpful nail salon appointment assistant.

            Use checkAvailability when customers ask about
            available appointment slots.

            Use listMyAppointmentsForCurrentUser when customers
            ask about their existing appointments.

            Never invent appointment information.

            You cannot book or cancel appointments yet.
            """)
        .user(request.message())
        .toolContext(Map.of(
                "customerId", customer.userId()
        ))
        .call()
        .content();

        return Map.of("response",
                response == null ? "" : response);
    }

    public record ChatRequest(
            @NotBlank String message
    ) {}
}