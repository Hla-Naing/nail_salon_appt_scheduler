package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingConfirmationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-17T20:00:00Z");
    private static final OffsetDateTime START = NOW.atOffset(ZoneOffset.UTC).plusHours(4);
    private AppointmentService appointments;
    private BookingConfirmationService service;
    private MockHttpSession session;
    private Clock clock;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentService.class);
        var salon = mock(SalonService.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(salon.getAvailableSlot(2L)).thenReturn(
                new SlotDto(2, "Provider", "Gel Manicure", 60, 45.0, START, START.plusHours(1)));
        service = new BookingConfirmationService(appointments, salon, new SalonTime(), clock);
        session = new MockHttpSession();
    }

    @Test
    void preparingBookingDoesNotCreateAppointment() {
        var pending = prepareBooking();

        assertEquals(PendingAppointmentAction.Kind.BOOKING, pending.details().kind());
        assertEquals(2L, pending.details().targetId());
        assertEquals("Gel Manicure", pending.details().service());
        assertEquals(new BigDecimal("45.00"), pending.details().amount());
        assertEquals(NOW.plusSeconds(600), pending.expiresAt());
        verifyNoInteractions(appointments);
    }

    @Test
    void confirmingBookingUsesTransactionalServiceAndConsumesToken() {
        var pending = prepareBooking();
        when(appointments.bookAppointment(1L, 2L)).thenReturn(10L);

        assertEquals(10L, service.confirm(session, 1L, pending.token()).appointmentId());
        assertThrows(ResponseStatusException.class, () -> service.confirm(session, 1L, pending.token()));
        verify(appointments, times(1)).bookAppointment(1L, 2L);
    }

    @Test
    void anotherCustomerCannotConfirmBooking() {
        var pending = prepareBooking();

        var error = assertThrows(ResponseStatusException.class, () -> service.confirm(session, 3L, pending.token()));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(appointments);
    }

    @Test
    void confirmationIsTiedToTheSessionAndSpecificAction() {
        var pending = prepareBooking();

        assertThrows(ResponseStatusException.class,
                () -> service.confirm(new MockHttpSession(), 1L, pending.token()));
        assertThrows(ResponseStatusException.class, () -> service.confirm(session, 1L, UUID.randomUUID()));
        var replacement = prepareBooking();
        assertThrows(ResponseStatusException.class, () -> service.confirm(session, 1L, pending.token()));
        verifyNoInteractions(appointments);
        when(appointments.bookAppointment(1L, 2L)).thenReturn(10L);
        assertEquals(10L, service.confirm(session, 1L, replacement.token()).appointmentId());
    }

    @Test
    void expiredConfirmationCannotWrite() {
        var pending = prepareBooking();
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));

        var error = assertThrows(ResponseStatusException.class, () -> service.confirm(session, 1L, pending.token()));
        assertEquals("Confirmation expired", error.getReason());
        verifyNoInteractions(appointments);
    }

    @Test
    void simultaneousConfirmationsCanOnlyWriteOnce() throws Exception {
        var pending = prepareBooking();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(appointments.bookAppointment(1L, 2L)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return 10L;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.confirm(session, 1L, pending.token()));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var second = executor.submit(() -> assertThrows(ResponseStatusException.class,
                        () -> service.confirm(session, 1L, pending.token())));
                assertEquals(400, second.get(5, TimeUnit.SECONDS).getStatusCode().value());
            } finally {
                release.countDown();
            }
            assertEquals(10L, first.get(5, TimeUnit.SECONDS).appointmentId());
        }
        verify(appointments, times(1)).bookAppointment(1L, 2L);
    }

    @Test
    void cancellationRequiresConfirmationOfAnOwnedAppointmentAndQuotedFee() {
        var fee = new BigDecimal("10.00");
        when(appointments.getCancellationCandidate(1L, 7L)).thenReturn(new AppointmentView(
                7L, 2L, "Provider", "Gel Manicure", new BigDecimal("45.00"), START, START.plusHours(1),
                "BOOKED", BigDecimal.ZERO));
        var pending = service.prepare(session, 1L, service.describeCancellation(1L, 7L));

        assertEquals(PendingAppointmentAction.Kind.CANCELLATION, pending.details().kind());
        assertTrue(pending.details().summary().contains("less than five hours"));
        assertEquals(fee, pending.details().amount());
        verify(appointments, never()).cancelAppointment(anyLong(), anyLong(), any());
        when(appointments.cancelAppointment(1L, 7L, fee)).thenReturn(fee);

        assertEquals(fee, service.confirm(session, 1L, pending.token()).feeCharged());
        assertThrows(ResponseStatusException.class, () -> service.confirm(session, 1L, pending.token()));
        verify(appointments, times(1)).cancelAppointment(1L, 7L, fee);
    }

    private PendingAppointmentAction.Confirmation prepareBooking() {
        return service.prepare(session, 1L, service.describeBooking(2L));
    }
}
