package com.example.nail_salon_appt_scheduler;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SalonService {
    private final SalonRepository repository;

    public SalonService(SalonRepository repository) {
        this.repository = repository;
    }

    public List<SalonRepository.Choice> providers() { return repository.providers(); }
    public List<SalonRepository.Choice> services() { return repository.services(); }

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

        if (page < 0 || size < 1 || size > 50 || (providerId != null && providerId <= 0)
                || (serviceId != null && serviceId <= 0)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Use positive filter IDs, page >= 0 and size between 1 and 50");
        }
        long offset = (long) page * size;

        return repository.findAvailableSlots(
                providerId,
                serviceId,
                date,
                size,
                offset
        );
    }
}
