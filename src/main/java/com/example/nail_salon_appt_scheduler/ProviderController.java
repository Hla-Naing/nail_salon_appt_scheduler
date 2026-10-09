package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/provider")
public class ProviderController {
    private final SessionAuthService sessions;
    private final ProviderService providers;
    public ProviderController(SessionAuthService sessions, ProviderService providers) {
        this.sessions = sessions;
        this.providers = providers;
    }

    @GetMapping("/appointments")
    public List<ProviderAppointmentView> appointments(HttpServletRequest request) {
        return providers.appointments(sessions.requireRole(request, "PROVIDER").userId());
    }

    @GetMapping("/slots")
    public List<ProviderSlotView> slots(HttpServletRequest request) {
        return providers.slots(sessions.requireRole(request, "PROVIDER").userId());
    }

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody SlotRequest input, HttpServletRequest request) {
        Long id = providers.createSlot(sessions.requireRole(request, "PROVIDER").userId(),
                input.serviceId(), input.startAt(), input.endAt());
        return Map.of("slotId", id);
    }

    @DeleteMapping("/slots/{slotId}")
    public Map<String, String> remove(@PathVariable Long slotId, HttpServletRequest request) {
        providers.removeSlot(sessions.requireRole(request, "PROVIDER").userId(), slotId);
        return Map.of("message", "Availability removed");
    }

    public record SlotRequest(@NotNull @Positive Long serviceId,
                              @NotNull @Future OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {}
}
