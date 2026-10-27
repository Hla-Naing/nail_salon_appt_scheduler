# Nail Salon Appointment Scheduler

A CMPE 172 project built with Java 21, Spring Boot, PostgreSQL, and Thymeleaf. Customers can register, book appointments, and cancel bookings. Providers can manage their availability and view appointments.

## Run locally

Start PostgreSQL, then run:

```sh
createdb nail_salon_db
./mvnw spring-boot:run
```

Open http://localhost:8080/web. The app uses your local PostgreSQL username with no password by default. You can change the connection with `SALON_DB_URL`, `SALON_DB_USER`, and `SALON_DB_PASSWORD`. Tables and demo data are created on startup.

Demo accounts all use the password `TestPassword123!`:

- Customers: `maya`, `lily`
- Providers: `anna`, `sofia`

Cancelling less than five hours before an appointment costs $10. Earlier cancellations are free.

## Docker

Copy `.env.example` to `.env` and replace the password placeholder, then start the containers:

```sh
docker compose up --build
```

The app is available at the same URL. Run `docker compose down` to stop it; the database data stays in the Docker volume.

## Tests

```sh
./mvnw clean verify
```

With Docker running, integration tests start their own PostgreSQL container. Without Docker, you can use a separate local test database:

```sh
createdb salon_m2_test
export SALON_TEST_DB_URL=jdbc:postgresql://localhost:5432/salon_m2_test
export SALON_TEST_DB_USER="$USER"
export SALON_TEST_DB_PASSWORD=''
./mvnw clean verify
```

Database integration tests are skipped if neither option is available. Tests cover booking, cancellation, authentication, double-booking prevention, and notification failures.

## Milestone 3

Bookings and cancellations trigger mock notifications over HTTP after the database transaction commits. The gateway uses timeouts, retries, and duplicate detection. If notification delivery fails, the appointment still succeeds. Failed notifications are logged but are not automatically replayed later.

To check database health and the successful booking count:

```sh
curl http://localhost:8080/health
curl http://localhost:8080/actuator/metrics/salon.bookings.success
```

GitHub Actions builds the project and runs the tests on every push and pull request.
