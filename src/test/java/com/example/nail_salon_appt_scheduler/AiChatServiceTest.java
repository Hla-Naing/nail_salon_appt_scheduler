package com.example.nail_salon_appt_scheduler;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiChatServiceTest {
    private final ChatModel model = mock(ChatModel.class);
    private final SalonService salon = mock(SalonService.class);
    private final AppointmentService appointments = mock(AppointmentService.class);
    private final SalonKnowledgeBase knowledge = mock(SalonKnowledgeBase.class);
    private final Clock clock = Clock.systemUTC();
    private final BookingConfirmationService confirmations =
            new BookingConfirmationService(appointments, salon, new SalonTime(), clock);
    private AiChatService chat;

    @BeforeEach
    void setUp() {
        when(model.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().model("mock").build());
        chat = new AiChatService(Optional.of(ChatClient.create(model)),
                new AppointmentAiTools(salon, appointments, confirmations), confirmations, knowledge, clock);
    }

    @Test
    void historyIsIsolatedByCustomerAndSession() {
        when(model.call(any(Prompt.class))).thenReturn(text("reply"));
        var first = new MockHttpSession();
        var second = new MockHttpSession();
        chat.reply(first, 1L, "Customer one private phrase");
        chat.reply(first, 1L, "My follow-up");
        chat.reply(second, 1L, "Same customer, new session");
        chat.reply(first, 2L, "Different customer");

        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(4)).call(prompts.capture());
        var values = prompts.getAllValues();
        assertEquals(List.of("Customer one private phrase", "My follow-up"),
                values.get(1).getUserMessages().stream().map(message -> message.getText()).toList());
        assertEquals(1, values.get(2).getUserMessages().size());
        assertEquals(1, values.get(3).getUserMessages().size());
    }

    @Test
    void sendsRetrievedPolicyToTheModelBeforeAnswering() {
        String question = "Is there a late cancellation fee?";
        String policy = "Less than five hours before the appointment costs $10.00.";
        when(knowledge.retrieve(question)).thenReturn(List.of(new Document(policy, Map.of("source", "Cancellation policy"))));
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertTrue(prompt.getSystemMessage().getText().contains(policy));
            assertEquals(question, prompt.getUserMessage().getText());
            return text("The fee is $10.00. Source: Cancellation policy.");
        });

        var reply = chat.reply(new MockHttpSession(), 1L, question).getBody();
        assertTrue(reply.response().contains("$10.00"));
        assertEquals(List.of("Cancellation policy"), reply.sources());
        verify(knowledge).retrieve(question);
    }

    @Test
    void multiStepToolsCannotWriteOrSeeTheConfirmationToken() {
        when(salon.getAvailableSlots(null, null, java.time.LocalDate.of(2026, 11, 25), 0, 20))
                .thenReturn(List.of(availableSlot()));
        when(salon.getAvailableSlot(41L)).thenReturn(availableSlot());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var options = (ToolCallingChatOptions) prompt.getOptions();
            assertEquals(1L, options.getToolContext().get(AppointmentAiTools.CUSTOMER_ID));
            assertFalse(options.getToolContext().values().stream().anyMatch(jakarta.servlet.http.HttpSession.class::isInstance));
            assertFalse(options.getToolCallbacks().stream().anyMatch(tool -> tool.getToolDefinition().name().contains("confirm")));
            return tool("checkAvailability", "{\"date\":\"2026-11-25\"}");
        }).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertTrue(prompt.getInstructions().getLast().toString().contains("Gel Manicure"));
            return tool("prepareBooking", "{\"slotId\":41}");
        }).thenReturn(text("I booked it."));

        var session = new MockHttpSession();
        var reply = chat.reply(session, 1L, "Book the Gel Manicure slot on November 25.").getBody();
        assertNotNull(reply.pendingAction());
        assertTrue(reply.response().contains("confirm it separately"));
        assertFalse(reply.response().contains("I booked it"));
        assertEquals(41L, reply.pendingAction().details().targetId());
        verifyNoInteractions(appointments);
    }

    @Test
    void failedChatDoesNotExposeProviderErrorsOrPublishDraft() {
        when(salon.getAvailableSlot(41L)).thenReturn(availableSlot());
        when(model.call(any(Prompt.class))).thenReturn(tool("prepareBooking", "{\"slotId\":41}"))
                .thenThrow(new IllegalStateException("sensitive provider error"));
        var session = new MockHttpSession();
        var reply = chat.reply(session, 1L, "Prepare my selected slot");
        assertEquals(503, reply.getStatusCode().value());
        assertNull(reply.getBody().pendingAction());
        assertFalse(reply.getBody().response().contains("sensitive"));
        verifyNoInteractions(appointments);
    }

    private SlotDto availableSlot() {
        var start = OffsetDateTime.parse("2026-11-25T13:00:00-08:00");
        return new SlotDto(41L, "Anna", "Gel Manicure", 60, 45, start, start.plusHours(1));
    }

    private ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ChatResponse tool(String name, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(name + "-id", "function", name, arguments))).build())));
    }
}
