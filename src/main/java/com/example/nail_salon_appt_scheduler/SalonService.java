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

    public List<SlotDto> getAvailableSlots() {
        return repository.findAvailableSlots();
    }
}
