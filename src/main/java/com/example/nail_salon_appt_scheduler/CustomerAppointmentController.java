package com.example.nail_salon_appt_scheduler;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/customer/appointments")
public class CustomerAppointmentController {

    private final SessionAuthService sessionAuthService;
    private final AppointmentService appointmentService;

    public CustomerAppointmentController(
            SessionAuthService sessionAuthService,
            AppointmentService appointmentService) {

        this.sessionAuthService = sessionAuthService;
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<?> bookAppointment(
            @jakarta.validation.Valid @RequestBody BookingRequest request,
            HttpServletRequest httpRequest) {

        UserAccount customer =
                sessionAuthService.requireRole(
                        httpRequest,
                        "CUSTOMER"
                );

        Long appointmentId =
                appointmentService.bookAppointment(
                        customer.userId(),
                        request.slotId()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Appointment booked successfully",
                        "appointmentId", appointmentId,
                        "slotId", request.slotId(),
                        "status", "BOOKED"
                ));
    }

    @GetMapping
    public ResponseEntity<?> getMyAppointments(
            HttpServletRequest request) {

        UserAccount customer =
                sessionAuthService.requireRole(
                        request,
                        "CUSTOMER"
                );

        return ResponseEntity.ok(
                appointmentService.getCustomerAppointments(
                        customer.userId()
                )
        );
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<?> cancelAppointment(
            @PathVariable Long appointmentId,
            HttpServletRequest request) {

        UserAccount customer =
                sessionAuthService.requireRole(
                        request,
                        "CUSTOMER"
                );

        var fee =
                appointmentService.cancelAppointment(
                        customer.userId(),
                        appointmentId
                );

        return ResponseEntity.ok(Map.of(
                "message", "Appointment cancelled successfully",
                "appointmentId", appointmentId,
                "status", "CANCELLED",
                "feeCharged", fee
        ));
    }


    public record BookingRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long slotId) {

        public BookingRequest(Long slotId) {
            this.slotId = slotId;
        }

        public Long getSlotId() {
            return slotId;
        }
    }
}

