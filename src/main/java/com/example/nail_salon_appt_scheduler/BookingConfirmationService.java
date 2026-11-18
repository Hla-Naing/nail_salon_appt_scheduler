package com.example.nail_salon_appt_scheduler;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class BookingConfirmationService {

    private static final String PENDING_BOOKING = "AI_PENDING_BOOKING";
    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    private final AppointmentService appointmentService;

    public BookingConfirmationService(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    public PendingBooking prepareBooking(
            HttpServletRequest request,
            Long customerId,
            Long slotId) {

        if (customerId == null || customerId <= 0 ||
                slotId == null || slotId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer and slot are required"
            );
        }

        PendingBooking pending = new PendingBooking(
                customerId,
                slotId,
                Instant.now()
        );

        request.getSession().setAttribute(PENDING_BOOKING, pending);

        return pending;
    }

    public Long confirmBooking(
            HttpServletRequest request,
            Long customerId) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No pending booking"
            );
        }

        Object stored = session.getAttribute(PENDING_BOOKING);

        if (!(stored instanceof PendingBooking pending)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No pending booking"
            );
        }

        if (!pending.customerId().equals(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Booking belongs to another customer"
            );
        }

        if (Instant.now().isAfter(
                pending.createdAt().plus(EXPIRATION))) {

            session.removeAttribute(PENDING_BOOKING);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking confirmation expired"
            );
        }

        session.removeAttribute(PENDING_BOOKING);

        return appointmentService.bookAppointment(
                customerId,
                pending.slotId()
        );
    }
}
