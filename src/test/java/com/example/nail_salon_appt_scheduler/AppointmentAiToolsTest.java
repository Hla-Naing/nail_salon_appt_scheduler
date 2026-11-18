package com.example.nail_salon_appt_scheduler;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ai.chat.model.ToolContext;

class AppointmentAiToolsTest {

    @Test
    void checkAvailabilityUsesExistingSalonService() {

        // Arrange
        SalonService salonService = mock(SalonService.class);
        AppointmentService appointmentService =
        mock(AppointmentService.class);

        AppointmentAiTools tools =
        new AppointmentAiTools(salonService, appointmentService);

        LocalDate date = LocalDate.of(2026, 11, 21);

        when(salonService.getAvailableSlots(
                null, null, date, 0, 20
        )).thenReturn(List.of());

        // Act
        List<SlotDto> result =
                tools.checkAvailability("2026-11-21", null, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(salonService).getAvailableSlots(
                null, null, date, 0, 20
        );
    }
        @Test
        void listMyAppointmentsUsesAuthenticatedCustomerId() {

        // Arrange
        SalonService salonService = mock(SalonService.class);
        AppointmentService appointmentService =
                mock(AppointmentService.class);

        AppointmentAiTools tools =
                new AppointmentAiTools(salonService, appointmentService);

        Long customerId = 1L;

        when(appointmentService.getCustomerAppointments(customerId))
                .thenReturn(List.of());

        // Act
        List<AppointmentView> result =
                tools.listMyAppointments(customerId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(appointmentService)
                .getCustomerAppointments(customerId);
        }

        @Test
        void listMyAppointmentsUsesCustomerIdFromToolContext() {

        // Arrange
        SalonService salonService = mock(SalonService.class);
        AppointmentService appointmentService =
                mock(AppointmentService.class);

        AppointmentAiTools tools =
                new AppointmentAiTools(salonService, appointmentService);

        ToolContext context = new ToolContext(
                java.util.Map.of("customerId", 1L)
        );

        when(appointmentService.getCustomerAppointments(1L))
                .thenReturn(List.of());

        // Act
        List<AppointmentView> result =
                tools.listMyAppointmentsForCurrentUser(context);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(appointmentService)
                .getCustomerAppointments(1L);
        }

}
