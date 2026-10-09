package com.example.nail_salon_appt_scheduler;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SalonService {
    private final SalonRepository repository;

    public SalonService(SalonRepository repository) {
        this.repository = repository;
    }

    public HomeDto getHome() {
        return new HomeDto(
            "Nail Salon",
            repository.countServices(),
            repository.countProviders()
        );
    }

    public List<SlotDto> getAvailableSlots(
        Long providerId,
        Long serviceId,
        java.time.LocalDate date,
        int page,
        int size) {

        int offset = page * size;

        return repository.findAvailableSlots(
                providerId,
                serviceId,
                date,
                size,
                offset
        );
    }
}
