
package com.example.nail_salon_appt_scheduler;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserAccount authenticate(String username, String password) {

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Username and password are required");
        }

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid username or password"));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException(
                    "Invalid username or password");
        }

        return user;
    }
}

