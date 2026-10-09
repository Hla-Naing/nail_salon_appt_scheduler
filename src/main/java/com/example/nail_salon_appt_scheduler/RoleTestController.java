
package com.example.nail_salon_appt_scheduler;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class RoleTestController {

    private final SessionAuthService sessionAuthService;

    public RoleTestController(SessionAuthService sessionAuthService) {
        this.sessionAuthService = sessionAuthService;
    }

    @GetMapping("/customer/test")
    public Map<String, String> customerTest(HttpServletRequest request) {
        UserAccount user =
                sessionAuthService.requireRole(request, "CUSTOMER");

        return Map.of(
                "message", "Customer access granted",
                "username", user.username()
        );
    }

    @GetMapping("/provider/test")
    public Map<String, String> providerTest(HttpServletRequest request) {
        UserAccount user =
                sessionAuthService.requireRole(request, "PROVIDER");

        return Map.of(
                "message", "Provider access granted",
                "username", user.username()
        );
    }
}

