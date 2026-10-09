package com.example.nail_salon_appt_scheduler;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component("salonTime")
public class SalonTime {
    public static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    public String display(OffsetDateTime value) {
        return value.atZoneSameInstant(ZONE).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z"));
    }
    public boolean future(OffsetDateTime value) { return value.isAfter(OffsetDateTime.now()); }
    public OffsetDateTime parse(String input) {
        try {
            LocalDateTime local = LocalDateTime.parse(input);
            var offsets = ZONE.getRules().getValidOffsets(local);
            if (offsets.size() != 1) throw new DateTimeException("Ambiguous or missing local time");
            return local.atOffset(offsets.getFirst());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a valid Pacific time outside the daylight-saving clock change");
        }
    }
}
