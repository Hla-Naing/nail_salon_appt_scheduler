package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpSession;

import static com.example.nail_salon_appt_scheduler.PendingAppointmentAction.*;

@Service
public class BookingConfirmationService {
    private static final String PENDING_ACTION = BookingConfirmationService.class.getName();
    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    private final AppointmentService appointments;
    private final SalonService salon;
    private final SalonTime salonTime;
    private final Clock clock;

    public BookingConfirmationService(AppointmentService appointments, SalonService salon,
                                      SalonTime salonTime, Clock clock) {
        this.appointments = appointments;
        this.salon = salon;
        this.salonTime = salonTime;
        this.clock = clock;
    }

    Details describeBooking(long slotId) {
        var slot = salon.getAvailableSlot(slotId);
        var price = BigDecimal.valueOf(slot.price()).setScale(2);
        String summary = "Book %s with %s at %s for $%s?"
                .formatted(slot.serviceName(), slot.providerName(), salonTime.display(slot.startAt()), price);
        return new Details(Kind.BOOKING, slotId, slot.serviceName(), slot.providerName(),
                slot.startAt(), price, summary);
    }

    Details describeCancellation(long customerId, long appointmentId) {
        var appointment = appointments.getCancellationCandidate(customerId, appointmentId);
        var fee = AppointmentService.cancellationFee(appointment.startAt(), OffsetDateTime.now(clock)).setScale(2);
        String summary = "Cancel %s with %s at %s? The current fee is $%s. "
                + "Cancellations less than five hours before the appointment cost $10.00.";
        return new Details(Kind.CANCELLATION, appointmentId, appointment.serviceName(),
                appointment.providerName(), appointment.startAt(), fee,
                summary.formatted(appointment.serviceName(), appointment.providerName(),
                        salonTime.display(appointment.startAt()), fee));
    }

    Confirmation prepare(HttpSession session, long customerId, Details details) {
        synchronized (WebUtils.getSessionMutex(session)) {
            var confirmation = new Confirmation(UUID.randomUUID(), clock.instant().plus(EXPIRATION), details);
            session.setAttribute(PENDING_ACTION, new PendingAppointmentAction(customerId, confirmation));
            return confirmation;
        }
    }

    Result confirm(HttpSession session, long customerId, UUID token) {
        Details details;
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!(session.getAttribute(PENDING_ACTION) instanceof PendingAppointmentAction pending)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no pending action");
            }
            if (pending.customerId() != customerId) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action belongs to another customer");
            }
            var confirmation = pending.confirmation();
            if (!confirmation.token().equals(token)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation token does not match");
            }
            session.removeAttribute(PENDING_ACTION);
            if (!clock.instant().isBefore(confirmation.expiresAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation expired");
            }
            details = confirmation.details();
        }

        return switch (details.kind()) {
            case BOOKING -> new Result(Kind.BOOKING, appointments.bookAppointment(customerId, details.targetId()), null);
            case CANCELLATION -> new Result(Kind.CANCELLATION, details.targetId(),
                    appointments.cancelAppointment(customerId, details.targetId(), details.amount()));
        };
    }

    public record Result(Kind kind, long appointmentId, BigDecimal feeCharged) {}
}
