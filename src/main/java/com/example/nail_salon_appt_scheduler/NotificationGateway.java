package com.example.nail_salon_appt_scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.*;

@Component
public class NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(NotificationGateway.class);
    private final RestClient client;
    private final NotificationProperties settings;

    public NotificationGateway(RestClient.Builder builder, NotificationProperties settings) {
        this.settings = settings;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(settings.getConnectTimeoutMs());
        factory.setReadTimeout(settings.getResponseTimeoutMs());
        client = builder.baseUrl(settings.getBaseUrl().toString()).requestFactory(factory).build();
    }

    /** Best effort: false means fallback; no delivery queue or automatic later replay. */
    public boolean send(NotificationRequest notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Notifications must be sent after transaction completion");
        }
        for (int attempt = 1; attempt <= settings.getMaxAttempts(); attempt++) {
            boolean retryable;
            try {
                var response = client.post().uri("/notifications").contentType(MediaType.APPLICATION_JSON)
                        .body(notification).retrieve().toBodilessEntity();
                if (response.getStatusCode().is2xxSuccessful()) return true;
                retryable = false;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                retryable = status == 408 || status == 429 || status == 502 || status == 503 || status == 504 || status == 500;
                log.warn("event=notification_http_failure notificationId={} attempt={} status={}",
                        notification.notificationId(), attempt, status);
            } catch (ResourceAccessException e) {
                retryable = true;
                log.warn("event=notification_transport_failure notificationId={} attempt={} cause={}",
                        notification.notificationId(), attempt, e.getClass().getSimpleName());
            } catch (RestClientException e) {
                retryable = false;
                log.warn("event=notification_client_failure notificationId={} cause={}",
                        notification.notificationId(), e.getClass().getSimpleName());
            }
            if (!retryable || attempt == settings.getMaxAttempts()) break;
            log.warn("event=notification_retry notificationId={} nextAttempt={}", notification.notificationId(), attempt + 1);
            try {
                Thread.sleep(settings.getRetryDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("event=notification_fallback notificationId={} type={} action=retain_appointment",
                notification.notificationId(), notification.type());
        return false;
    }
}
