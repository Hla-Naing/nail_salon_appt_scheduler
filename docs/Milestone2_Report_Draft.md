# CMPE 172 Milestone 2 — Nail Salon Appointment Scheduler

## 1. Overview

My application lets customers browse nail appointments, book a slot, and manage their own bookings. Providers can create availability, remove unbooked availability, and see customers booked with them. The project uses Java 21 as its compilation target, Spring Boot, PostgreSQL, JdbcTemplate, Thymeleaf, and Bootstrap. It keeps the original Controller → Service → Repository structure and does not use JPA or Hibernate.

The website starts at `/web`. The original JSON endpoints still work, including the salon summary at `/` and slot browsing at `/slots`. I used separate page routes so adding HTML would not change existing API responses.

## 2. Booking workflow

A customer logs in and browses `/web/slots`. The page shows the provider, service, price, and start/end times. Provider, service, and date filters narrow the results, and pagination limits each page. The Book button submits a slot ID to `WebController.book`. The customer ID comes from the authenticated session, not a hidden input supplied by the browser.

`AppointmentService.bookAppointment` validates the IDs, locks the slot, checks that it is still in the future, checks for an active booking, and inserts an appointment. On success the page redirects to `/web/confirmation/{appointmentId}`. The confirmation lookup is scoped to the logged-in customer. The equivalent JSON flow is `POST /customer/appointments`, which returns 201 with the appointment ID, slot ID, and BOOKED status. A booking conflict returns 409.

## 3. Frontend-to-backend flow

The HTML path is:

```text
Browser form → WebController → AppointmentService
             → AppointmentRepository → JdbcTemplate → PostgreSQL
             → repository result → service result → redirect
             → confirmation controller → Thymeleaf → browser
```

The JSON API uses `CustomerAppointmentController` instead of `WebController`. Both call the same service. Controllers never execute SQL. Thymeleaf renders the data returned through the service layer, and Bootstrap supplies the page styling. Forms use POST and redirects after successful writes to reduce accidental refresh submissions.

## 4. Authentication and role-based access

`AuthService.authenticate` finds the account by username and uses `BCryptPasswordEncoder.matches` against the stored hash. The application does not compare plaintext passwords in SQL. `SessionAuthService.login` replaces the previous session and stores the account ID. API responses and page models contain only the public account fields they need.

`SessionAuthService.requireRole` calls `requireUser`, which looks up the session account ID in PostgreSQL. It checks the current database role, so editing a browser value or an old session role cannot grant provider access. A missing login returns 401, and a role mismatch returns 403. Customer IDs and provider profile IDs are resolved on the server. `WebSecurityConfig` also checks CSRF tokens for the HTML forms, and session cookies are HttpOnly and SameSite=Lax.

## 5. The booking race condition

Without a lock, two customers can both read a free slot before either inserts an appointment. Both requests would believe that booking is allowed. A check followed by an insert is not enough by itself because another transaction can act between those statements.

This project coordinates requests using the availability row. Each booking first obtains an exclusive row lock for the same slot. The second request waits until the first transaction finishes. After the first commits, the second request checks bookings again and sees the committed appointment. It returns 409 instead of inserting another booking.

## 6. ACID in this project

- **Atomicity:** the service transaction contains the slot lock, availability check, and appointment insert. A runtime exception rolls back the transaction. Cancellation's owner check, row lock, status change, timestamp, and fee update also belong to one transaction.
- **Consistency:** foreign keys keep appointments attached to real customers and slots. Check constraints limit valid status values and prevent negative fees. The partial unique index prevents two active bookings for the same slot. Service rules reject past bookings and invalid availability times.
- **Isolation:** READ_COMMITTED prevents reading another transaction's uncommitted insert. The slot row lock serializes competing booking decisions for that slot. Transactions booking unrelated slots can still proceed separately.
- **Durability:** once PostgreSQL commits the booking, it remains stored independently of the browser session and application process. PostgreSQL's normal write-ahead logging and commit settings provide this; the application does not disable them.

## 7. READ_COMMITTED and transaction boundaries

The booking service has:

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public Long bookAppointment(Long customerId, Long slotId) {
    // Validate, lock the slot, check active bookings, then insert.
}
```

The annotation takes effect because controllers call a Spring-managed service bean. The integration test uses that same bean. Each booking thread receives its own transaction and JDBC connection. The row lock remains held until the service invocation commits or rolls back.

At READ_COMMITTED, each SQL statement sees committed data as of that statement. This matters because the active-booking query runs after the waiting request obtains its lock. It can see the first request's committed insert. READ_COMMITTED alone does not stop the race; the row lock and the order of operations are necessary.

## 8. Pessimistic locking

`AppointmentRepository.lockSlot` contains:

```sql
SELECT start_at
FROM availability_slots
WHERE slot_id = ? AND removed_at IS NULL
FOR UPDATE
```

The following statement counts appointments with `status = 'BOOKED'`. Only after that check does the repository insert a booking. Provider removal locks the same availability row before checking whether it is booked. A request therefore cannot silently remove a slot while another transaction books it. Removed rows stay in the database for history but cannot be booked again.

## 9. Database unique index

`schema.sql` retains the database backstop:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS one_active_booking_per_slot
    ON appointments(slot_id)
    WHERE status = 'BOOKED';
```

The index applies only to active bookings. Cancelled history can coexist with a later booking of the same slot. Even an insert that bypasses the normal locking service cannot create two BOOKED appointments for one slot. The test suite checks a direct duplicate insert as well as the service race. A duplicate-key exception in the booking service becomes a 409 response; other integrity conflicts also receive a sanitized 409 from the global handler.

## 10. Retry strategy

There is no automatic retry loop. An ordinary 409 means the requested booking is unavailable, so retrying it would not help the customer. The customer should refresh the slot list and choose another time. The application also does not implement transient deadlock retries. The transaction rolls back on an unexpected database failure and the client receives a generic error. This keeps the milestone implementation small and avoids retrying business conflicts.

## 11. Cancellation and owner checks

`AppointmentRepository.lockCustomerAppointment` filters by both appointment ID and customer ID:

```sql
WHERE a.appointment_id = ?
  AND a.customer_id = ?
FOR UPDATE OF a
```

The customer ID comes from the session. Another customer gets 404 and cannot change the appointment. `AppointmentService.cancelAppointment` now has a transaction, so the repository lock covers the entire decision and update. Only BOOKED appointments whose start time is still in the future can be cancelled.

The service compares the remaining duration with five hours. Less than five hours charges $10.00; otherwise the fee is zero. It updates status to CANCELLED, stores `cancelled_at` and `fee_charged`, and keeps the record. The UI states the fee policy before cancellation and shows the resulting fee afterward.

## 12. Completion, filtering, and pagination

Before customer or provider history queries, `completePastAppointments` runs:

```sql
UPDATE appointments a SET status = 'COMPLETED'
FROM availability_slots av
WHERE a.slot_id = av.slot_id AND a.status = 'BOOKED'
  AND av.end_at < CURRENT_TIMESTAMP
```

Upcoming rows stay BOOKED, and CANCELLED rows are unchanged. Completion is persisted when history is read; there is no background scheduler. A database viewed directly before anyone reads history may still contain ended BOOKED rows.

`SalonRepository.findAvailableSlots` includes future slots with no BOOKED appointment and no removal timestamp. It adds parameterized provider, service, and date conditions, then uses `ORDER BY a.start_at, a.slot_id`, `LIMIT`, and `OFFSET`. The second ordering field makes equal start times stable. Page starts at zero, size is 1–50, and offset arithmetic uses `long` to prevent integer overflow.

The API uses offset date-times. Pages display Pacific time, and date filtering explicitly uses `AT TIME ZONE 'America/Los_Angeles'` so the database connection's time zone does not change the meaning of a date filter. Provider form inputs are interpreted in that zone. Ambiguous or nonexistent daylight-saving transition times are rejected.

## 13. Provider availability management

`ProviderController` checks the PROVIDER role before calling `ProviderService`. `ProviderRepository.findProviderId` maps the session's user ID to the provider profile. Creation accepts only service ID, start, and end. The service must exist, start must be future, and end must follow start. Success returns 201.

Removal finds and locks a slot owned by that provider. A missing or other provider's slot returns 404. A BOOKED slot returns 409. Successful removal sets `removed_at`; it does not delete a row referenced by appointment history. The dashboard lists the provider's future slots and all of their appointments, including cancelled and completed records.

## 14. Validation and exceptions

Jakarta validation checks API request records: login fields are nonblank, slot IDs are positive and required, and availability requires a service and timestamps. Services enforce cross-field rules and validate IDs and pagination for both the HTML and JSON controllers.

`GlobalExceptionHandler` returns a consistent JSON shape:

```json
{
  "timestamp": "2026-10-09T05:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Slot is already booked",
  "path": "/customer/appointments"
}
```

This is an illustrative shape, not a captured response. The handler covers invalid request bodies, type conversion, validation errors, response-status exceptions, integrity conflicts, and unexpected exceptions. Unexpected errors return a generic 500 message; SQL and stack traces stay out of responses. Normal web errors show an HTML message, and invalid credentials are displayed on the login page.

## 15. Automated unit tests

- `AppointmentServiceTest`: future booking, missing/past/booked slots, invalid IDs, locking order, duplicate-key conflict without retries, owner-only cancellation, cancellation state checks, started appointments, early/late fees, and completion before history.
- `ProviderServiceTest`: provider profile resolution, creation, missing service/profile, required fields, reversed/past times, ownership, booked-slot rejection, and removal order.
- `ValidationAndAuthTest`: Jakarta constraints, pagination/filter validation, large offsets, BCrypt, session replacement, current database roles, and daylight-saving conversion.
- `GlobalExceptionHandlerTest`: generic 500 responses and sanitized database-conflict responses.

These tests use Mockito where the database is not part of the behavior being checked.

## 16. Two-thread PostgreSQL test

`PostgresWorkflowSuite.exactlyOneOfTwoConcurrentCustomersBooksTheSlot` creates two different CUSTOMER accounts and one future slot. It uses `Executors.newFixedThreadPool(2)`, a ready latch, and a start latch. Both workers wait before calling the real Spring booking service. The test also asserts that the injected service is a Spring proxy.

It waits for both futures with timeouts and asserts exactly one success and one 409. A separate SQL count must equal one active BOOKED row. This is actual concurrent execution, not two sequential calls. Fixture rows use unique names, and cleanup removes only those fixtures from an isolated database.

`PostgresContainerTest` supplies PostgreSQL 16 through Testcontainers. `LocalPostgresTest` runs the same six integration tests with a dedicated database named `salon_m2_test` when Docker is unavailable. The other integration tests cover authentication/RBAC/validation, ownership and filtering, provider removal/history, persisted completion, the unique index, and rendered web pages and forms.

## 17. Database setup and cleanup

The schema change adds a nullable `removed_at` column without dropping tables or records. Seed accounts use BCrypt hashes. User, provider, and service inserts are idempotent, and slots are resolved by usernames and service names rather than generated IDs. A provider with no slots receives a future demo batch once. Later restarts do not append more slots; the provider UI is used to replenish availability.

Temporary role-test endpoints and tracked cookie files were removed. Cookie filenames are ignored. The old context-only test was replaced because it implicitly connected to the development database. `PasswordGenerator.java` was not present. No tests or smoke checks used the existing development records.

## 18. Verification results

See [Milestone2_Verification.md](Milestone2_Verification.md) for exact commands, final counts, runtime details, curl results, limitations, and the changed-file inventory. This draft includes no fabricated screenshots. Screenshots and the narrated video still need to be recorded for submission.
