package com.example.nail_salon_appt_scheduler;

import java.net.URI;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "salon.notification")
public class NotificationProperties {
    @NotNull private URI baseUrl = URI.create("http://localhost:8080");
    @Min(1) @Max(10000) private int connectTimeoutMs = 500;
    @Min(1) @Max(10000) private int responseTimeoutMs = 1000;
    @Min(1) @Max(5) private int maxAttempts = 3;
    @Min(0) @Max(2000) private int retryDelayMs = 100;
    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI value) { baseUrl = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getResponseTimeoutMs() { return responseTimeoutMs; }
    public void setResponseTimeoutMs(int value) { responseTimeoutMs = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { maxAttempts = value; }
    public int getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(int value) { retryDelayMs = value; }
    @AssertTrue(message = "base URL must be an absolute HTTP(S) URL without credentials, query or fragment")
    public boolean isBaseUrlValid() {
        return baseUrl != null && baseUrl.getHost() != null && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null && baseUrl.getFragment() == null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()));
    }
}
