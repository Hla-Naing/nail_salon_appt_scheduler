package com.example.nail_salon_appt_scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
class BookingConfirmationServiceTest {

    private AppointmentService appointmentService;
    private BookingConfirmationService confirmationService;
    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        appointmentService = mock(AppointmentService.class);
        confirmationService =
                new BookingConfirmationService(appointmentService);

        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);

        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
    }

    @Test
    void preparingBookingDoesNotCreateAppointment() {

        PendingBooking pending =
                confirmationService.prepareBooking(request, 1L, 2L);

        assertEquals(1L, pending.customerId());
        assertEquals(2L, pending.slotId());

        verify(session).setAttribute(
                eq("AI_PENDING_BOOKING"),
                any(PendingBooking.class)
        );

        verifyNoInteractions(appointmentService);
    }

    @Test
    void confirmingBookingUsesTransactionalService() {

        PendingBooking pending =
                confirmationService.prepareBooking(request, 1L, 2L);

        when(session.getAttribute("AI_PENDING_BOOKING"))
                .thenReturn(pending);

        when(appointmentService.bookAppointment(1L, 2L))
                .thenReturn(10L);

        Long appointmentId =
                confirmationService.confirmBooking(request, 1L);

        assertEquals(10L, appointmentId);

        verify(session).removeAttribute("AI_PENDING_BOOKING");

        verify(appointmentService).bookAppointment(1L, 2L);
    }
    @Test
    void anotherCustomerCannotConfirmBooking() {

        // Arrange: Customer 1 prepares a booking
        PendingBooking pending =
                confirmationService.prepareBooking(request, 1L, 2L);

        when(session.getAttribute("AI_PENDING_BOOKING"))
                .thenReturn(pending);

        // Act: Customer 2 attempts to confirm it
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () ->
                        confirmationService.confirmBooking(request, 2L)
                );

        // Assert
        assertEquals(403, exception.getStatusCode().value());

        verify(appointmentService, never())
                .bookAppointment(anyLong(), anyLong());
    }
    }
