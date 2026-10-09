
package com.example.nail_salon_appt_scheduler;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class SessionAuthService {

    private final UserRepository userRepository;

    public SessionAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserAccount requireRole(
            HttpServletRequest request, String requiredRole) {

        UserAccount user = requireUser(request);
        if (!user.role().equals(requiredRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return user;
    }

    public void login(HttpServletRequest request, UserAccount user) {
        HttpSession previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("userId", user.userId());
        session.setAttribute("role", user.role());
    }

    public UserAccount requireUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null ||
                !(session.getAttribute("userId") instanceof Long userId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Account not found"));

        return user;
    }
}

