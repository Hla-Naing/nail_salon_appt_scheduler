package com.example.nail_salon_appt_scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentReminderAgentTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T21:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    private final ReminderRepository repository = mock(ReminderRepository.class);
    private final NotificationGateway gateway = mock(NotificationGateway.class);
    private final ChatModel model = mock(ChatModel.class);
    private final ReminderTextGenerator generator = new ReminderTextGenerator(
            Optional.of(ChatClient.create(model)), new SalonTime());
    private final AppointmentReminderAgent agent =
            new AppointmentReminderAgent(repository, generator, gateway, CLOCK, Duration.ofHours(24));

    @org.junit.jupiter.api.BeforeEach
    void configureModel() {
        when(model.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    }

    @Test
    void usesLlmAndGatewayThenRecordsDelivery() {
        var claim = claim(null);
        when(repository.claim(NOW, NOW.plusHours(24), 8L)).thenReturn(Optional.of(claim), Optional.empty());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertEquals("Salon service: Gel Manicure", prompt.getUserMessage().getText());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("We look forward to seeing you!"))));
        });
        String message = "We look forward to seeing you! Reminder: your Gel Manicure appointment starts Nov 17, 2026 3:00 PM PST.";
        when(repository.persistPayloadIfUpcoming(claim, message, NOW)).thenReturn(true);
        var request = new NotificationRequest("reminder-test", "customer:413", "APPOINTMENT_REMINDER", message);
        when(gateway.send(request)).thenReturn(true);

        assertEquals(new AppointmentReminderAgent.RunResult(1, 1, 0, true), agent.run(8L));
        var order = inOrder(repository, gateway);
        order.verify(repository).persistPayloadIfUpcoming(claim, message, NOW);
        order.verify(gateway).send(request);
        order.verify(repository).sent(claim, NOW);
        assertEquals(0, agent.run(8L).attempted());
        verify(gateway, times(1)).send(request);
    }

    @Test
    void deliveryFailureRetriesLaterWithSameIdAndPersistedPayload() {
        var first = claim("Already generated reminder.");
        var retry = claim(first.message());
        when(repository.claim(NOW, NOW.plusHours(24), null))
                .thenReturn(Optional.of(first), Optional.empty(), Optional.of(retry), Optional.empty());
        when(repository.persistPayloadIfUpcoming(any(), eq(first.message()), eq(NOW))).thenReturn(true);
        var request = new NotificationRequest(first.notificationId(), "customer:413", "APPOINTMENT_REMINDER", first.message());
        when(gateway.send(request)).thenReturn(false, true);

        assertEquals(1, agent.run(null).failed());
        verify(repository).retryLater(first, NOW.plusMinutes(5));
        verify(repository, never()).sent(eq(first), any());
        assertEquals(1, agent.run(null).delivered());
        verify(repository).sent(retry, NOW);
        verify(gateway, times(2)).send(request);
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void generationFailureNeverSendsOrMarksDelivered() {
        var claim = claim(null);
        when(repository.claim(NOW, NOW.plusHours(24), null)).thenReturn(Optional.of(claim), Optional.empty());
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("mock quota failure"));

        assertEquals(new AppointmentReminderAgent.RunResult(1, 0, 1, true), agent.run(null));
        verify(repository).retryLater(claim, NOW.plusMinutes(5));
        verify(repository, never()).sent(any(), any());
        verifyNoInteractions(gateway);
    }

    private ReminderRepository.Claim claim(String message) {
        return new ReminderRepository.Claim(52, 413, "Gel Manicure", NOW.plusHours(2),
                "reminder-test", UUID.randomUUID(), message);
    }
}
