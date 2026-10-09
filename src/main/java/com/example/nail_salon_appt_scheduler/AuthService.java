
package com.example.nail_salon_appt_scheduler;

import java.nio.charset.StandardCharsets;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserAccount registerCustomer(String name, String username, String password, String confirmPassword) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        name = name.trim();
        username = username.trim();
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be at most 100 characters");
        }
        if (username.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be at most 50 characters");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        // BCrypt accepts at most 72 bytes, including for multibyte characters.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        if (userRepository.usernameExists(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is already taken");
        }

        Long userId;
        try {
            userId = userRepository.createCustomer(name, username, passwordEncoder.encode(password));
        } catch (DuplicateKeyException e) {
            // Another registration may have claimed the username after the check.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is already taken");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Created account could not be loaded"));
    }

    public UserAccount authenticate(String username, String password) {

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        return user;
    }
}
