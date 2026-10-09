package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderService {
    private final ProviderRepository providers;
    private final AppointmentRepository appointments;
    public ProviderService(ProviderRepository providers, AppointmentRepository appointments) {
        this.providers = providers;
        this.appointments = appointments;
    }

    private Long providerId(Long userId) {
        return providers.findProviderId(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider profile not found"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<ProviderAppointmentView> appointments(Long userId) {
        Long providerId = providerId(userId);
        appointments.completePastAppointments();
        return providers.appointments(providerId);
    }

    public List<ProviderSlotView> slots(Long userId) { return providers.slots(providerId(userId)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long createSlot(Long userId, Long serviceId, OffsetDateTime start, OffsetDateTime end) {
        Long providerId = providerId(userId);
        if (serviceId == null || serviceId <= 0 || start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service, start and end are required");
        }
        if (!start.isAfter(OffsetDateTime.now()) || !end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start must be in the future and end must follow start");
        }
        if (!providers.serviceExists(serviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found");
        }
        return providers.createSlot(providerId, serviceId, start, end);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeSlot(Long userId, Long slotId) {
        if (slotId == null || slotId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid slot ID is required");
        }
        // The same row lock as booking serializes a booking racing with removal.
        if (!providers.lockOwnedSlot(slotId, providerId(userId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");
        }
        if (appointments.isSlotBooked(slotId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove a booked slot");
        }
        providers.removeSlot(slotId);
    }
}
