# Milestone 2 code walkthrough — about 6½ minutes

Before recording: use Java 21, start PostgreSQL and the app, and open `http://localhost:8080/web`. Have a future slot available. Log in once to check the demo credentials. Open the files below in the IDE and keep the successful test output visible in another tab. The script is approximately 850 spoken words; pause briefly while selecting methods and queries. Do not spend most of the video clicking through pages.

## 0:00–0:35 — Project and login

**Show:** `pom.xml`, then `AuthService.authenticate` in `src/main/java/com/example/nail_salon_appt_scheduler/`.

“This is my Nail Salon Appointment Scheduler. It uses Spring Boot, PostgreSQL, and JdbcTemplate, with Thymeleaf pages. I kept the controller, service, and repository layers. There is no ORM.

“For login, the controller passes the username and password to this service. The repository finds the account, then BCrypt checks the submitted password against the stored hash. The password hash is not returned to the browser.”

## 0:35–1:10 — Session and role enforcement

**Show:** `LoginController.login`, then `SessionAuthService.login`, `requireUser`, and `requireRole`.

“After successful login, I replace the old session and store the user ID. Every protected endpoint checks that session. The important part here is that I load the user from the database again and check their current role. I do not trust a role or customer ID sent by the browser. An unauthenticated request gets 401, and the wrong role gets 403. Providers are mapped to their provider profile on the server.”

## 1:10–1:45 — Slot browsing and pagination

**Show:** `SalonController.slots`, `SalonService.getAvailableSlots`, and the bottom half of `SalonRepository.findAvailableSlots`.

“The slot API accepts provider, service, and date filters. The repository adds SQL conditions with bound parameters. It returns future slots that have no active booking and have not been removed. Date filters use Pacific time, which matches the website.

“Here are LIMIT and OFFSET. Page starts at zero, and size is limited to 50. The service validates those values and calculates the offset using a long. Results sort by start time and slot ID so ties have a stable order.”

## 1:45–2:45 — Booking and the transaction

**Show:** `CustomerAppointmentController.bookAppointment`, then `AppointmentService.bookAppointment`.

“The request contains a slot ID. The controller gets the customer ID from the authenticated session and calls the service.

“This method is the transaction boundary. It uses READ_COMMITTED. First it validates the IDs, then it locks the slot. After the lock, it checks the time and checks whether the slot is already booked. Only then does it insert the appointment.

“The race we are preventing is two customers reading a free slot at the same time. Without a lock, both could pass a check before either inserts. Here, the second transaction waits for the first. Once it gets the lock, its next statement sees the first committed booking and returns 409. I do not retry that conflict.”

## 2:45–3:30 — Row lock, unique index, and ACID

**Show:** `AppointmentRepository.lockSlot`, `isSlotBooked`, and `createAppointment`; then `src/main/resources/schema.sql` at `one_active_booking_per_slot`.

“This is the SELECT FOR UPDATE query. The lock stays held until the service transaction finishes. The unique partial index is a second defense. It permits only one BOOKED appointment per slot, even if another insert bypasses the service.

“In ACID terms, the transaction makes the operation atomic. Constraints and the index protect consistency. READ_COMMITTED together with this lock provides the isolation needed for the decision. PostgreSQL provides durability after commit. READ_COMMITTED by itself would not solve the race.”

## 3:30–4:10 — Cancellation and completion

**Show:** `AppointmentRepository.lockCustomerAppointment`, `AppointmentService.cancelAppointment`, and `AppointmentRepository.completePastAppointments`.

“Cancellation filters by both appointment ID and customer ID. If it belongs to another customer, the query finds nothing and returns 404. This service is transactional too, so its appointment lock covers the checks and update.

“Only a future BOOKED appointment can be cancelled. Less than five hours before the start charges ten dollars; otherwise the fee is zero. I update the status instead of deleting the record.

“Before history queries, this SQL changes ended BOOKED appointments to COMPLETED. It leaves cancelled and upcoming appointments alone.”

## 4:10–4:50 — Provider features and validation

**Show:** `ProviderController.create`, `ProviderService.createSlot`, `removeSlot`, `ProviderRepository.lockOwnedSlot`, and `GlobalExceptionHandler`.

“Provider creation validates the service and timestamps. The provider ID comes from the session user's profile. Removal checks ownership and locks the same slot row used by booking. It rejects an active booking with 409. Otherwise it sets a removal timestamp, preserving old appointment history.

“Jakarta annotations validate API input, while the service handles logical rules like end being after start. The global handler gives errors a consistent JSON shape. Unexpected failures return a generic 500 without exposing SQL or stack traces.”

## 4:50–5:45 — Explain the automated concurrency test

**Show:** `src/test/java/com/example/nail_salon_appt_scheduler/PostgresWorkflowSuite.java`, specifically `fixtures` and `exactlyOneOfTwoConcurrentCustomersBooksTheSlot`. Briefly show `LocalPostgresTest` / `PostgresContainerTest` and the final Surefire results.

“This test creates two different customers and one future slot. These are fixture records in an isolated PostgreSQL database.

“The executor has two threads. Both count down the ready latch and wait on the start latch. Releasing that latch lets both attempt the same booking through the real Spring service bean, so the transaction annotation actually runs.

“I wait for both results and assert one success, one conflict, and exactly one BOOKED row in the database. Any other exception fails the test. The same suite supports Testcontainers or a dedicated local PostgreSQL database. In this environment Docker was unavailable, so the PostgreSQL tests ran successfully through the local variant.”

## 5:45–6:30 — Short website demo and request flow

**Show:** `/web/login`, `/web/slots`, a booking confirmation, `/web/appointments`, and `/web/provider`. Briefly return to `WebController.book` if time allows.

“I'll finish by showing the web flow. I log in as Maya, filter slots, book one, and see the confirmation. My appointments shows the status and cancellation option. As Anna, I can view my bookings and create or remove my availability.

“These forms call a controller, which calls the same services and JDBC repositories used by the JSON API. Thymeleaf renders the results. The browser never decides the user's role or writes directly to the database. That connects the interface to the transaction and ownership rules I just showed.”

## Recording notes

- Demo password: `TestPassword123!`; customers `maya` and `lily`; providers `anna` and `sofia`.
- Keep cancelled history visible if it helps explain status persistence.
- Show the actual final result: 35 executed tests passed; the six Docker duplicates were skipped, while all six local PostgreSQL tests ran.
- Build/runtime details and reproducible commands are in `Milestone2_Verification.md`. Do not present a unit-only run as proof of concurrency.
- Add your own title/name, screenshots, and submission links. No screenshots or video have been fabricated or recorded by this implementation work.
