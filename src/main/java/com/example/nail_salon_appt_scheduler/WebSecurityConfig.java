package com.example.nail_salon_appt_scheduler;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                var session = request.getSession(true);
                String token = (String) session.getAttribute("csrfToken");
                if (token == null) {
                    token = UUID.randomUUID().toString();
                    session.setAttribute("csrfToken", token);
                }
                if ("POST".equals(request.getMethod()) && !token.equals(request.getParameter("csrfToken"))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Form expired. Reload the page and try again");
                }
                request.setAttribute("csrfToken", token);
                return true;
            }
        }).addPathPatterns("/web", "/web/**");
    }
}
