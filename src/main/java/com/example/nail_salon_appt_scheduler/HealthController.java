package com.example.nail_salon_appt_scheduler;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        boolean databaseUp;
        try {
            databaseUp = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (DataAccessException e) {
            log.error("event=health_database_failure cause={}", e.getClass().getSimpleName());
            databaseUp = false;
        }
        return ResponseEntity.status(databaseUp ? 200 : 503).body(Map.of(
                "status", databaseUp ? "UP" : "DOWN", "application", "UP",
                "database", databaseUp ? "UP" : "DOWN"));
    }
}
