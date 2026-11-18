package com.example.nail_salon_appt_scheduler;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiConfigurationTest {
    @Test
    void configuredSdkRetriesQuotaAndBoundsCapacityFailures() throws Exception {
        var calls = new AtomicInteger();
        var repeatFailure = new java.util.concurrent.atomic.AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int attempt = calls.incrementAndGet();
            int status = repeatFailure.get() ? 503 : attempt == 1 ? 429 : 200;
            String body = switch (status) {
                case 429 -> """
                        {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Test quota failure"}}
                        """;
                case 503 -> """
                        {"error":{"code":503,"status":"UNAVAILABLE","message":"Test busy model"}}
                        """;
                default -> """
                        {"responseId":"test-response","modelVersion":"gemini-3.1-flash-lite",
                         "candidates":[{"content":{"role":"model","parts":[{"text":"hello"}]},"finishReason":"STOP"}]}
                        """;
            };
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        try {
            var config = new GeminiConfiguration();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            try (var sdk = Client.builder().apiKey("mock-key")
                    .httpOptions(config.requestOptions(2000).baseUrl(baseUrl).build()).build()) {
                var chat = config.salonChatClient(sdk, "gemini-3.1-flash-lite");

                assertEquals("hello", chat.prompt("Hello").call().content());
                assertEquals(2, calls.get());
                repeatFailure.set(true);
                assertThrows(RuntimeException.class, () -> chat.prompt("Hello").call().content());
                assertEquals(4, calls.get());
            }
        } finally {
            server.stop(0);
        }
    }
}
