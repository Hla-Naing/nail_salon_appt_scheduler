package com.example.nail_salon_appt_scheduler;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class NotificationGatewayTest {
    HttpServer server;
    ExecutorService executor;
    List<String> bodies = new CopyOnWriteArrayList<>();
    NotificationProperties settings;
    NotificationRequest request = new NotificationRequest("booking-42", "customer:1", "BOOKING_CONFIRMATION", "Appointment #42 confirmed.");

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        settings = new NotificationProperties();
        settings.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        settings.setConnectTimeoutMs(100);
        settings.setResponseTimeoutMs(100);
        settings.setRetryDelayMs(1);
    }
    @AfterEach void cleanup() {
        server.stop(0);
        executor.shutdownNow();
    }
    NotificationGateway gateway() { return new NotificationGateway(RestClient.builder(), settings); }
    void respond(int status, int delayMs) {
        server.createContext("/notifications", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
                exchange.sendResponseHeaders(status, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
    }
    @Test void sendsJsonSuccessfullyOverRealHttp() {
        respond(200, 0);
        assertTrue(gateway().send(request));
        assertEquals(1, bodies.size());
        assertTrue(bodies.getFirst().contains("\"notificationId\":\"booking-42\""));
        assertTrue(bodies.getFirst().contains("BOOKING_CONFIRMATION"));
    }
    @Test void retriesTransientStatusWithIdenticalIdAndPayload() {
        respond(503, 0);
        assertFalse(gateway().send(request));
        assertEquals(3, bodies.size());
        assertEquals(1, bodies.stream().distinct().count());
    }
    @Test void recoversOnSecondAttempt() {
        server.createContext("/notifications", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(bodies.size() == 1 ? 503 : 200, -1);
            exchange.close();
        });
        server.start();
        assertTrue(gateway().send(request));
        assertEquals(2, bodies.size());
    }
    @Test void doesNotRetryPermanentClientError() {
        respond(400, 0);
        assertFalse(gateway().send(request));
        assertEquals(1, bodies.size());
    }
    @Test void timesOutAndFallsBackWithinBoundedTime() {
        respond(200, 1000);
        long started = System.nanoTime();
        assertFalse(gateway().send(request));
        assertTrue(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis() < 2500);
        assertEquals(3, bodies.size());
    }
    @Test void connectionRefusedFallsBack() {
        server.stop(0);
        assertFalse(gateway().send(request));
    }
    @Test void refusesRemoteCallInsideActiveTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> gateway().send(request)); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
