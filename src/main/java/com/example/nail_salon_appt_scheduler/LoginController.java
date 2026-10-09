package com.example.nail_salon_appt_scheduler;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class LoginController {
    private final AuthService auth;
    private final SessionAuthService sessions;

    public LoginController(AuthService auth, SessionAuthService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest input, HttpServletRequest request) {
        UserAccount user = auth.authenticate(input.username(), input.password());
        sessions.login(request, user);
        return Map.of("message", "Login successful", "name", user.name(), "role", user.role());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        UserAccount user = sessions.requireUser(request);
        return Map.of("userId", user.userId(), "role", user.role());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("message", "Logged out successfully");
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 200) String password) {}
}
