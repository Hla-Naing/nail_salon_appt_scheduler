# Nail Salon Appointment Scheduler — Milestone 2

CMPE 172 appointment booking with Java 21, Spring Boot, PostgreSQL, JdbcTemplate, Thymeleaf and Bootstrap. No ORM. Controllers call services, which call repositories containing explicit SQL.

## Run

```sh
createdb nail_salon_db
./mvnw spring-boot:run
```

Open **http://localhost:8080/web**. The existing `GET /` JSON salon summary and all original JSON routes remain available.

The default database is `jdbc:postgresql://localhost:5432/nail_salon_db`, with your local `$USER` and no password. Override with `SALON_DB_URL`, `SALON_DB_USER`, and `SALON_DB_PASSWORD`. Use a Java 21 JDK; Maven compiles with release 21. Schema and seed scripts run on startup. The additive `removed_at` migration preserves existing appointments.

| Username | Role | Demo password |
| --- | --- | --- |
| maya | CUSTOMER | `TestPassword123!` |
| lily | CUSTOMER | `TestPassword123!` |
| anna | PROVIDER | `TestPassword123!` |
| sofia | PROVIDER | `TestPassword123!` |

Seed inserts preserve existing accounts. Fresh providers receive two future slots each. Restarting does not add duplicate slots; after the initial batch expires, log in as a provider to create availability. All page times and date filters use America/Los_Angeles. API date-times require an ISO-8601 offset, for example `2027-01-15T10:00:00-08:00`.

## Pages

- `/web`: home; `/web/login`: login.
- `/web/slots`: browse, filter, paginate, and book.
- `/web/confirmation/{appointmentId}`: owner-only booking confirmation.
- `/web/appointments`: customer appointments, history, cancellation fees.
- `/web/provider`: provider availability, bookings, and history.

The forms use server sessions, database role checks, and CSRF tokens. Password hashes never go into response DTOs or page models. Bootstrap loads from a CDN.

## JSON API

| Method | URL | Access |
| --- | --- | --- |
| GET | `/` | Public salon summary |
| GET | `/slots?providerId=&serviceId=&date=&page=0&size=10` | Public; omit unused parameters |
| POST | `/auth/login` | JSON `username`, `password` |
| GET | `/auth/me` | Logged-in user, current database role |
| POST | `/auth/logout` | End session |
| POST | `/customer/appointments` | CUSTOMER; JSON `slotId` |
| GET | `/customer/appointments` | CUSTOMER; own history |
| DELETE | `/customer/appointments/{appointmentId}` | CUSTOMER; owner-only |
| GET | `/provider/appointments` | PROVIDER; own bookings/history |
| GET | `/provider/slots` | PROVIDER; own future availability |
| POST | `/provider/slots` | PROVIDER; JSON `serviceId`, `startAt`, `endAt` |
| DELETE | `/provider/slots/{slotId}` | PROVIDER; own unbooked slot |

Errors use `timestamp`, `status`, `error`, `message`, and `path`. Page size is 1–50; page starts at zero. Less than 5 hours before start costs $10 to cancel; earlier cancellations cost $0. Appointments that have started cannot be cancelled. History reads update past BOOKED rows to COMPLETED once the slot ends. Availability removal sets `removed_at` so history survives.

## Tests and build

With Docker running:

```sh
./mvnw clean test
./mvnw clean package
```

`PostgresContainerTest` runs the real PostgreSQL workflows, including the synchronized two-thread booking test. It is explicitly skipped when Docker is unavailable. Unit tests do not require a database. Without Docker, use a **dedicated** PostgreSQL database; the alternate suite refuses any database name except `salon_m2_test`:

```sh
createdb salon_m2_test
export SALON_TEST_DB_URL=jdbc:postgresql://localhost:5432/salon_m2_test
export SALON_TEST_DB_USER="$USER"
export SALON_TEST_DB_PASSWORD=''
./mvnw clean test
./mvnw clean package
```

`LocalPostgresTest` and `PostgresContainerTest` inherit the same six tests. Tests create unique fixture accounts and slots and clean up only their own records. They do not use the development database or seed accounts. If neither Docker nor `SALON_TEST_DB_URL` is available, both integration variants are skipped: a green unit-only run does **not** prove concurrency correctness.

To repeat curl smoke checks, start another app instance against a disposable database seeded by the app (not `salon_m2_test` or your development database), then run:

```sh
python3 scripts/smoke_test.py --base-url http://localhost:18080 --disposable-database
```

The script uses real curl requests and temporary cookie jars. It creates and cancels appointments, and retains that history in the disposable database.

## Submission material

- [Report draft](docs/Milestone2_Report_Draft.md)
- [5–7 minute code walkthrough](docs/Milestone2_Code_Walkthrough.md)
- [Verification results and complete changed-file inventory](docs/Milestone2_Verification.md)

Rescheduling is not implemented in this milestone scope. Record your own screenshots and video, review the report, then commit/push the changes on `milestone-2` for submission.
