package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long bookAppointment(Long customerId, Long slotId) {

        if (customerId == null || customerId <= 0 || slotId == null || slotId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer and slot are required"
            );
        }

        // Lock this availability slot until the transaction finishes.
        OffsetDateTime startAt = appointmentRepository.lockSlot(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Slot not found"
                ));

        // Do not allow booking an appointment in the past.
        if (!startAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot book a past slot"
            );
        }

        // Check again after obtaining the row lock.
        if (appointmentRepository.isSlotBooked(slotId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Slot is already booked"
            );
        }

        try {
            return appointmentRepository.createAppointment(customerId, slotId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot is already booked", e);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public java.util.List<AppointmentView> getCustomerAppointments(Long customerId) {

        appointmentRepository.completePastAppointments();
        return appointmentRepository.findByCustomerId(customerId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal cancelAppointment(Long customerId,Long appointmentId) {

        if (customerId == null || customerId <= 0 || appointmentId == null || appointmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid customer and appointment are required");
        }
        AppointmentCancelInfo appointment =
                appointmentRepository
                        .lockCustomerAppointment(
                                appointmentId,
                                customerId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Appointment not found"
                                ));

        if (!appointment.status().equals("BOOKED")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Appointment is not currently booked"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (!appointment.startAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Past appointments cannot be cancelled"
            );
        }

        Duration untilAppointment = Duration.between(now, appointment.startAt());

        BigDecimal fee = BigDecimal.ZERO;

        if (untilAppointment.compareTo(Duration.ofHours(5)) < 0) {
            fee = new BigDecimal("10.00");
        }

        appointmentRepository.cancelAppointment(
                appointmentId,
                fee
        );

        return fee;
    }


}

