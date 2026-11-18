package com.example.nail_salon_appt_scheduler;

import java.time.Clock;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.HttpSessionMutexListener;

@Configuration(proxyBeanMethods = false)
class AiSessionConfiguration {
    @Bean
    ServletListenerRegistrationBean<HttpSessionMutexListener> sessionMutexListener() {
        return new ServletListenerRegistrationBean<>(new HttpSessionMutexListener());
    }

    @Bean
    Clock salonClock() {
        return Clock.systemUTC();
    }
}
