package com.example.nail_salon_appt_scheduler;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalonController {
    private final SalonService service;

    public SalonController(SalonService service) {
        this.service = service;
    }

    @GetMapping("/")
    public HomeDto home() {
        return service.getHome();
    }

    @GetMapping("/slots")
    public List<SlotDto> slots() {
        return service.getAvailableSlots();
    }
}
