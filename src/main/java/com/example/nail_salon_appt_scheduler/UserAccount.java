
package com.example.nail_salon_appt_scheduler;

public record UserAccount(
        Long userId,
        String name,
        String username,
        String passwordHash,
        String role
) {}

