package com.example.nail_salon_appt_scheduler;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AppointmentAiTools {
    static final String CUSTOMER_ID = "customerId";
    static final String PREPARATION = "preparation";

    private final SalonService salon;
    private final AppointmentService appointments;
    private final BookingConfirmationService confirmations;

    public AppointmentAiTools(SalonService salon, AppointmentService appointments,
                              BookingConfirmationService confirmations) {
        this.salon = salon;
        this.appointments = appointments;
        this.confirmations = confirmations;
    }

    @Tool(description = "Find up to 20 available slots on a Pacific calendar date. Provider and service filters are optional.")
    public List<SlotDto> checkAvailability(
            @ToolParam(description = "Pacific date in YYYY-MM-DD format") String date,
            @ToolParam(description = "Provider ID", required = false) Long providerId,
            @ToolParam(description = "Service ID", required = false) Long serviceId) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Supply a date in YYYY-MM-DD format.");
        }
        try {
            return salon.getAvailableSlots(providerId, serviceId, LocalDate.parse(date), 0, 20);
        } catch (DateTimeException | ResponseStatusException e) {
            throw new IllegalArgumentException("Supply a valid date and positive filter IDs.");
        }
    }

    @Tool(description = "Look up actual provider and service names with IDs for availability filters.")
    public Catalog listCatalog() {
        return new Catalog(salon.providers(), salon.services());
    }

    @Tool(description = "List appointments belonging to the logged-in customer, including their status.")
    public List<AppointmentView> listMyAppointmentsForCurrentUser(ToolContext context) {
        return listMyAppointments(customerId(context));
    }

    public List<AppointmentView> listMyAppointments(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("A valid authenticated customer is required.");
        }
        return appointments.getCustomerAppointments(customerId);
    }

    @Tool(description = "Prepare a booking for a specific real slot the customer selects. Returns a review summary. Only the customer can confirm in the app.")
    public PreparationResult prepareBooking(@ToolParam(description = "Selected availability slot ID") Long slotId,
                                             ToolContext context) {
        customerId(context);
        return prepare(context, () -> confirmations.describeBooking(positiveId(slotId)));
    }

    @Tool(description = "Prepare cancellation of a specific appointment owned by the customer. Returns a review summary with the fee.")
    public PreparationResult prepareCancellation(@ToolParam(description = "Selected appointment ID") Long appointmentId,
                                                  ToolContext context) {
        long customerId = customerId(context);
        return prepare(context, () -> confirmations.describeCancellation(customerId, positiveId(appointmentId)));
    }

    private PreparationResult prepare(ToolContext context, Supplier<PendingAppointmentAction.Details> describe) {
        if (!(context.getContext().get(PREPARATION) instanceof Preparation preparation)) {
            throw new IllegalArgumentException("Preparation requires an authenticated chat request.");
        }
        if (preparation.details != null) {
            return new PreparationResult(false, "One action can be prepared per turn.");
        }
        try {
            preparation.details = describe.get();
            return new PreparationResult(true, preparation.details.summary());
        } catch (ResponseStatusException e) {
            return new PreparationResult(false, e.getReason());
        }
    }

    private long customerId(ToolContext context) {
        if (context == null || !(context.getContext().get(CUSTOMER_ID) instanceof Long id) || id <= 0) {
            throw new IllegalArgumentException("Authenticated customer ID is required.");
        }
        return id;
    }

    private long positiveId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Supply a positive ID from the lookup results.");
        }
        return id;
    }

    static final class Preparation {
        PendingAppointmentAction.Details details;
    }

    public record Catalog(List<SalonRepository.Choice> providers, List<SalonRepository.Choice> services) {}
    public record PreparationResult(boolean prepared, String summary) {}
}
