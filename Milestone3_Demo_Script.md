# Milestone 3 code-walkthrough script

Target length: approximately 6–7 minutes at a conversational pace with short code/demo pauses. Replace FirstName LastName before submitting. This is a preparation script; no video has been recorded.

Before recording, open the files listed below in order. Have the application running against a disposable database, and keep a terminal with the actual test summary visible. With Compose, copy `.env.example` to `.env`, replace its password placeholder, then run `docker compose up --build`. Use the seeded customer `maya` and provider `anna` with the existing demo password documented in README. If Docker is unavailable, use the local Maven/PostgreSQL setup in README and say which environment you actually used.

## 0:00–0:40 — Introduce the existing project

Open `pom.xml`, then `CustomerAppointmentController.java` and `WebController.java`.

“Hi, I'm FirstName LastName. This is my Nail Salon Appointment Scheduler for CMPE 172 Milestone 3. I extended my existing Spring Boot project with integration, observability and deployment support. It still uses Java 21, PostgreSQL and JdbcTemplate, with Thymeleaf pages and session-based customer and provider roles.

“These two controllers show the two entry points. The REST booking endpoint and the website booking form both call the same appointment service. They also share the cancellation service. That keeps notification behavior consistent across both interfaces.”

Point to REST `bookAppointment`/`cancelAppointment` and website `book`/`cancel`.

## 0:40–1:35 — Show the transaction boundary

Open `AppointmentService.java`, `AppointmentNotification.java` and `AppointmentNotificationListener.java`.

“In `bookAppointment`, the transaction locks the availability row, checks that the slot is in the future and free, and creates the appointment. The original database unique index is still a second defense against double booking. I publish a small immutable event containing the notification snapshot, but no HTTP request happens here.

“Cancellation follows the same pattern. It checks ownership and status, calculates the existing late fee, updates the appointment, then publishes its event.

“The listener uses `AFTER_COMMIT`, so a rolled-back booking never produces a success notification. It also uses `NOT_SUPPORTED`, because Spring can still have transaction resources attached during completion callbacks. Suspending them ensures the HTTP call happens outside an active database transaction. The gateway has a guard for that too. Unexpected notification errors are caught here, so a committed appointment remains successful.”

Highlight both service methods, then `afterCommit` and its two annotations.

## 1:35–2:25 — Explain the HTTP contract and deduplication

Open `NotificationRequest.java`, `NotificationController.java`, `NotificationInbox.java` and the bottom of `schema.sql`.

“This is one complete notification request: a stable ID, an opaque customer routing identifier, a supported type and the message. Booking messages include the appointment, slot and start time. Cancellation messages include the fee. Validation rejects missing or unsupported data.

“The second REST controller accepts `POST /notifications`. This is a mock: it logs the notification ID and type, without sending real email or logging private recipient details.

“Retries reuse the same ID and payload. PostgreSQL stores a receipt with a unique ID and payload hash. The insert handles concurrent duplicates. The same payload gets a duplicate acknowledgment; changing the payload under the same ID gets a conflict. These receipts survive restarts. The receiver is in this application by default, but the gateway can target another instance over HTTP.”

Highlight `sendNotification`, `accept` and `ON CONFLICT`.

## 2:25–3:15 — Walk through resilience and its limits

Open `NotificationGateway.java`, `NotificationProperties.java` and `application.properties`.

“The gateway uses Spring RestClient with explicit connection and response timeouts. Defaults are 500 milliseconds to connect, one second to read a response, and three total attempts with a short delay. These settings and the URL are configurable.

“It retries transport failures and selected temporary HTTP errors. Permanent client errors stop immediately. If delivery still fails, the method returns false and logs a fallback warning; the appointment stays committed.

“This is bounded best-effort delivery. There is no durable sender queue, so a crash immediately after commit or an extended outage can lose a notification. Restoring the service lets new notifications work, but does not automatically replay old ones. A durable outbox would address that future requirement.”

Point to `send`, retry classification, interruption handling and the final fallback log.

## 3:15–4:05 — Demonstrate delivery and an outage

Show `/web/slots`; book one future appointment as the customer. Show `docker compose logs app` with `event=appointment_committed` followed by `event=notification_received`. For local Maven, show the same application console messages.

“Here the booking committed, and the mock receiver accepted its confirmation. The shared service gives us the same behavior from the API.”

For Compose, change the outbound destination while preserving the database:

```sh
NOTIFICATION_BASE_URL=http://127.0.0.1:1 docker compose up -d --force-recreate app
```

Refresh and log in again after recreation because sessions are process-local. Book another available future slot, then cancel it. Show the resulting history plus retry/fallback logs.

“The destination is unavailable, but booking and cancellation still succeed. These WARN events explain the delivery failure. The database history is authoritative.”

Restore with:

```sh
NOTIFICATION_BASE_URL=http://app:8080 docker compose up -d --force-recreate app
```

If demonstrating locally, restart Maven with `NOTIFICATION_BASE_URL=http://127.0.0.1:1`, then restore its original URL. A restart also ends local in-memory sessions.

## 4:05–4:40 — Show health, metrics and logging

Open `HealthController.java`, the listener counter increment and `GlobalExceptionHandler.java`.

```sh
curl -s http://localhost:8080/health
curl -s http://localhost:8080/actuator/metrics/salon.bookings.success
curl -s http://localhost:8080/actuator/metrics/salon.notifications.fallback
```

“Health is public and runs `SELECT 1` against PostgreSQL. It returns 200 when healthy and 503 when the application cannot reach the database. These Micrometer counters count committed operations and normal fallback outcomes. They reset when the process restarts. Logging uses searchable event fields: INFO for committed operations, WARN for retries and fallback, and ERROR for unexpected failures, without raw customer details.”

## 4:40–5:20 — Show Docker and CI

Open `Dockerfile`, `docker-compose.yml` and `.github/workflows/ci.yml`.

“The multi-stage Dockerfile builds with Java 21 and runs the JAR as a non-root user. Compose starts PostgreSQL first, waits for readiness, and connects the application to the service name `db`. Environment variables supply credentials; the local `.env` is ignored. The named volume retains data, and existing SQL initialization stays active.

“GitHub Actions runs on every push and pull request using Java 21 and a PostgreSQL service. It runs Maven verify and checks that the real database suite did not silently skip. It uploads test results. I only claim an actual hosted CI run if I show one here.”

If Docker cannot run on the recording machine, explicitly say that Compose configuration was validated but container execution is unverified.

## 5:20–6:25 — Prove the important integration behavior

Open `PostgresWorkflowSuite.java`, especially `committedBookingAndCancellationSurviveUnavailableNotificationService`, plus `NotificationGatewayTest.java` and the generated test results in `target/surefire-reports/`.

“This integration test uses the real Spring service proxy, real PostgreSQL and the real HTTP gateway pointing to an unavailable service. Before the outbound call, a separate database connection sees the committed appointment. The test checks no active sending transaction, verifies gateway fallback, and still expects successful REST booking and cancellation responses.

“Other tests verify the website path, rollback suppression, receipt deduplication and concurrent double-booking prevention. Gateway tests start their own HTTP server on an ephemeral port, so they do not depend on a manually running app. They exercise successful delivery, response timeouts, retries and recovery.

“The recorded local build passed 59 tests with zero failures or errors. Sixteen duplicate Testcontainers cases were skipped because Docker was unavailable; the same sixteen workflow cases passed against local PostgreSQL. I also ran fifty-eight HTTP smoke checks against the packaged application. The local JDK was 22 compiling for Java 21; Docker and CI are configured to run Java 21. The README distinguishes actual results from checks still requiring those environments.”

Show the terminal build summary. The previously generated report and diagram are available inside the submission ZIP. Finish by identifying the ZIP and the 5–7 minute video as separate submission items. Do not claim the video has already been uploaded.
