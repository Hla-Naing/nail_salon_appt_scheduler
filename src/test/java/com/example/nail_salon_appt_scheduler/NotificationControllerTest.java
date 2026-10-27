package com.example.nail_salon_appt_scheduler;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
    NotificationInbox inbox;
    MockMvc mvc;
    String valid = "{\"notificationId\":\"booking-42\",\"recipient\":\"customer:1\",\"type\":\"BOOKING_CONFIRMATION\",\"message\":\"Appointment #42 confirmed.\"}";
    @BeforeEach void setup() {
        inbox = mock(NotificationInbox.class);
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(inbox))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void acceptsValidPayloadAndAcknowledgesDuplicate() throws Exception {
        when(inbox.accept(any())).thenReturn(true, false);
        mvc.perform(post("/notifications").contentType("application/json").content(valid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));
        mvc.perform(post("/notifications").contentType("application/json").content(valid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DUPLICATE"));
    }
    @Test void rejectsMissingBlankUnsupportedOversizedAndMalformedInputs() throws Exception {
        for (String body : java.util.List.of("{}", "{broken", valid.replace("customer:1", " "),
                valid.replace("booking-42", "bad id"), valid.replace("BOOKING_CONFIRMATION", "PASSWORD_RESET"),
                valid.replace("Appointment #42 confirmed.", "x".repeat(1001)),
                valid.replace("customer:1", "x".repeat(101)))) {
            mvc.perform(post("/notifications").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(inbox);
    }
}
