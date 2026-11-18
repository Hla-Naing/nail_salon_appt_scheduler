package com.example.nail_salon_appt_scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AppointmentAiTools {

    private final SalonService salonService;
    private final AppointmentService appointmentService;

    public AppointmentAiTools(
            SalonService salonService,
            AppointmentService appointmentService) {

        this.salonService = salonService;
        this.appointmentService = appointmentService;
    }

    // Tool 1: Check available appointment slots
    @Tool(description = "Find available nail salon appointment slots for a given date. Optionally filter by provider or service.")
    public List<SlotDto> checkAvailability(
            @ToolParam(description = "Appointment date in YYYY-MM-DD format")
            String date,

            @ToolParam(description = "Provider ID, or null for any provider", required = false)
            Long providerId,

            @ToolParam(description = "Service ID, or null for any service", required = false)
            Long serviceId) {

        LocalDate appointmentDate = LocalDate.parse(date);

        return salonService.getAvailableSlots(
                providerId,
                serviceId,
                appointmentDate,
                0,
                20
        );
    }

    // Tool 2 foundation: List the authenticated customer's appointments
    // Not exposed to Gemini yet because the customer ID must come
    // from the authenticated session, not from AI-generated arguments.
    public List<AppointmentView> listMyAppointments(Long customerId) {

        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException(
                    "A valid authenticated customer is required"
            );
        }

        return appointmentService.getCustomerAppointments(customerId);
    }
    @Tool(description = "List the currently logged-in customer's nail salon appointments.")
    public List<AppointmentView> listMyAppointmentsForCurrentUser(
            ToolContext context) {

        Object customerId = context.getContext().get("customerId");

        if (!(customerId instanceof Long id) || id <= 0) {
            throw new IllegalArgumentException(
                    "Authenticated customer ID is required"
            );
        }

        return listMyAppointments(id);
    }

}
