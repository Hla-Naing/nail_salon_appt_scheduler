# Nail Salon Appointment Scheduler

A CMPE 172 project for browsing nail services and available appointment slots. Customers will be able to book, cancel, and reschedule appointments; providers will manage their availability.

## Technology
Java 21, Spring Boot, PostgreSQL, Spring JDBC, and handwritten SQL. No ORM.

## Run locally
1. Start PostgreSQL.
2. Create the database: `createdb nail_salon_db`
3. Run the app: `./mvnw spring-boot:run`
4. Visit `http://localhost:8080/` or `http://localhost:8080/slots`

The app loads `schema.sql` and `seed.sql` on startup. It uses your local PostgreSQL user from the `USER` environment variable.

## Current endpoints
- `GET /` — salon summary from the database
- `GET /slots` — unbooked slots with provider and service details

Requests follow Controller → Service → Repository → PostgreSQL. The repository uses JDBC and SQL; controllers return DTOs.

## Planned booking rules
- Only one active booking per slot, enforced by a PostgreSQL partial unique index.
- Cancellations less than 5 hours before the appointment incur a salon-wide fee.
- Customers may reschedule at least 24 hours before the appointment.

Booking, authentication, cancellation, and rescheduling are planned features. The seed accounts have placeholder password values and cannot be used to log in yet.
