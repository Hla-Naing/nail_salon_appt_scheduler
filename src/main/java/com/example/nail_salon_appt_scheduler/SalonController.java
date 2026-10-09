package com.example.nail_salon_appt_scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    public List<SlotDto> slots(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page cannot be negative"
            );
        }

        if (size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 50"
            );
        }

        return service.getAvailableSlots(
                providerId,
                serviceId,
                date,
                page,
                size
        );
    }
}
