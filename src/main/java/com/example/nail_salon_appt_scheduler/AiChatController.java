package com.example.nail_salon_appt_scheduler;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/ai")
public class AiChatController {
    private final AiChatService chat;
    private final BookingConfirmationService confirmations;
    private final SessionAuthService auth;

    public AiChatController(AiChatService chat, BookingConfirmationService confirmations, SessionAuthService auth) {
        this.chat = chat;
        this.confirmations = confirmations;
        this.auth = auth;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiChatService.Reply> chat(@Valid @RequestBody ChatRequest input, HttpServletRequest request) {
        var customer = auth.requireRole(request, "CUSTOMER");
        return chat.reply(request.getSession(false), customer.userId(), input.message());
    }

    @PostMapping(path = "/actions/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BookingConfirmationService.Result confirm(@Valid @RequestBody ConfirmRequest input,
                                                      HttpServletRequest request) {
        var customer = auth.requireRole(request, "CUSTOMER");
        return confirmations.confirm(request.getSession(false), customer.userId(), input.token());
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {}
    public record ConfirmRequest(@NotNull UUID token) {}
}
