# Nail Salon Appointment Scheduler — Milestone 4

CMPE 172 appointment booking with Java 21, Spring Boot, PostgreSQL, JdbcTemplate, Thymeleaf and Bootstrap. No ORM. Controllers call services, which call repositories containing explicit SQL. Milestone 4 adds a Spring AI appointment assistant, automated reminders, and a searchable salon FAQ.

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
| POST | `/ai/chat` | CUSTOMER; JSON `message` |
| POST | `/ai/actions/confirm` | CUSTOMER; JSON `token` from the pending action |
| POST | `/ai/reminders/run` | PROVIDER; own upcoming appointments; requires `X-Salon-AI` header |
| POST | `/ai/knowledge/reload` | PROVIDER; rebuild the FAQ index; requires `X-Salon-AI` header |

Errors use `timestamp`, `status`, `error`, `message`, and `path`. Page size is 1–50; page starts at zero. Less than 5 hours before start costs $10 to cancel; earlier cancellations cost $0. Appointments that have started cannot be cancelled. History reads update past BOOKED rows to COMPLETED once the slot ends. Availability removal sets `removed_at` so history survives.

## Milestone 4 demo

Set `GEMINI_API_KEY` in your private `.env` for Compose, or export it for a local Maven or JAR run. Use `docker compose up -d --build` to apply the repository changes with the existing database volume. The scheduler and health endpoints work without the key. Gemini chat, embeddings, and new reminders require a key with access to the configured models.

| Setting | Default | Purpose |
| --- | --- | --- |
| `GEMINI_CHAT_MODEL` | `gemini-3.1-flash-lite` | Chat tools and reminder text |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | Embed FAQ sections and queries |
| `SALON_AI_TIMEOUT_MS` | `8000` | Per-attempt Gemini HTTP timeout |
| `SALON_REMINDER_WINDOW` | `PT24H` | Look ahead up to 24 hours |
| `SALON_REMINDER_INTERVAL` | `PT5M` | Delay between scheduled scans |
| `SALON_AI_KNOWLEDGE_SOURCE` | `classpath:knowledge/salon-faq.md` | Markdown resource location for local/JAR runs |
| `SALON_APP_PORT` | `8080` | Compose host port |

Use a disposable demo database when creating records for a walkthrough. The example uses the seeded accounts shown above and private temporary cookie jars.

```sh
base=http://localhost:8080
demo_dir=$(mktemp -d)
trap 'rm -r "$demo_dir"' EXIT
curl -fsS -c "$demo_dir/customer.cookies" -H 'Content-Type: application/json' \
  -d '{"username":"maya","password":"TestPassword123!"}' "$base/auth/login"
curl -fsS -c "$demo_dir/provider.cookies" -H 'Content-Type: application/json' \
  -d '{"username":"anna","password":"TestPassword123!"}' "$base/auth/login"

chat() {
  curl -fsS -b "$demo_dir/customer.cookies" -H 'Content-Type: application/json' \
    -d "$1" "$base/ai/chat"
}
chat '{"message":"What is the late cancellation fee?"}'
chat '{"message":"Which services and providers have openings this week?"}'
```

- **Agent 1**. Pick a returned slot and continue the same session with `chat '{"message":"Prepare a booking for slot 123"}'`, substituting the actual slot ID. Review `pendingAction.details`, then use the token from `pendingAction.token` in the request below. A follow-up such as “yes” in chat is conversational only. Ask to list your appointments, choose the new appointment, and request a cancellation draft. Review its fee and confirm using its own token. Reusing a token returns HTTP 400.

```sh
curl -fsS -b "$demo_dir/customer.cookies" -H 'Content-Type: application/json' \
  -d '{"token":"REPLACE_WITH_PENDING_ACTION_TOKEN"}' "$base/ai/actions/confirm"
```

- **Agent 2**. Create an Anna slot within the next 24 hours via `/web/provider`. Book it as Maya and leave it booked. Run the provider command twice before the next scheduled scan. The first result should show one delivered reminder if Gemini and the gateway are available. The second should show zero for that appointment. If the schedule already ran, use the app log to find the earlier receipt.

```sh
curl -fsS -b "$demo_dir/provider.cookies" -H 'X-Salon-AI: true' -X POST "$base/ai/reminders/run"
docker compose logs app --tail 100 | rg 'event=notification_received .*type=APPOINTMENT_REMINDER'
```

- **RAG**. Ask “What is the fee if I cancel two hours before my appointment?” and check the response and retrieved `sources`. Ask “What should I bring for nail art?” for a different knowledge section. Rebuild the in-memory index using the provider command below. Update the packaged Markdown and rebuild, or point a local run at a trusted `file:` resource before reloading.

```sh
curl -fsS -b "$demo_dir/provider.cookies" -H 'X-Salon-AI: true' -X POST "$base/ai/knowledge/reload"
```

See the [Milestone 4 report](docs/CMPE172_Milestone4_Report.md) and [video walkthrough](docs/Milestone4_Code_Walkthrough.md).

## Milestone 4 verification

Verified November 17, 2026. Maven discovered **106 tests** and finished with **86 passed, 0 failures or errors, and 20 skipped**. All 20 PostgreSQL Testcontainers tests passed. The alternate `LocalPostgresTest` suite was skipped because `SALON_TEST_DB_URL` was unset. Maven ran on JDK 22 with compiler release 21, and AI tests used mocked models or a local HTTP stub.

The isolated Docker Compose deployment built and started on Temurin Java 21.0.13 with PostgreSQL 16 and Gemini credentials unset. Both services became healthy, `GET /health` confirmed PostgreSQL connectivity, and all 58 existing HTTP smoke checks passed.

Using the private key on that isolated stack, both configured Gemini model metadata requests returned HTTP 200. The live demo retrieved the cancellation policy and answered $10, found an actual slot, prepared and confirmed a booking, rejected token replay, sent one provider-triggered reminder, returned zero new deliveries on the repeat run, and confirmed a cancellation. A scheduled-only run sent a reminder for a second synthetic booking. PostgreSQL contained one matching durable receipt per appointment.

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

`LocalPostgresTest` and `PostgresContainerTest` inherit the same PostgreSQL tests, including Milestone 3 notification checks and Milestone 4 confirmation and reminder concurrency checks. Tests create unique fixture accounts and slots and clean up only their own records. They do not use the development database or seed accounts. If neither Docker nor `SALON_TEST_DB_URL` is available, both integration variants are skipped: a green unit-only run does **not** prove concurrency correctness.

To repeat curl smoke checks, start another app instance against a disposable database seeded by the app (not `salon_m2_test` or your development database), then run:

```sh
python3 scripts/smoke_test.py --base-url http://localhost:18080 --disposable-database
```

The script uses real curl requests and temporary cookie jars. It creates and cancels appointments, and retains that history in the disposable database.



## Milestone 3 integration

Both REST and website booking/cancellation use `AppointmentService` events and a shared `AFTER_COMMIT` listener. The listener suspends transaction resources before calling `NotificationGateway`; failures preserve committed appointments. Notifications are synchronous, bounded best effort. A process crash after commit or exhausted retries can lose delivery: there is no sender outbox or automatic replay.

The mock `POST /notifications` controller is in this application and sends no real messages. Its JSON contract is:

```json
{"notificationId":"demo-booking-42","recipient":"customer:42","type":"BOOKING_CONFIRMATION","message":"Appointment #42 confirmed."}
```

Use `BOOKING_CONFIRMATION` or `CANCELLATION_CONFIRMATION`. First acceptance returns HTTP 200 `RECEIVED`; identical retries return 200 `DUPLICATE`; changed payload under the same ID returns 409; invalid input returns 400. PostgreSQL receipts retain only the ID, payload hash and acceptance timestamp. The unique ID prevents duplicate acceptance across concurrent requests and restarts. Internal appointment IDs generate stable `booking-{id}` / `cancellation-{id}` notification IDs.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `NOTIFICATION_BASE_URL` | `http://localhost:8080` | Receiver origin; Compose defaults to `http://app:8080` |
| `NOTIFICATION_CONNECT_TIMEOUT_MS` | `500` | Connection timeout |
| `NOTIFICATION_RESPONSE_TIMEOUT_MS` | `1000` | Response/read timeout |
| `NOTIFICATION_MAX_ATTEMPTS` | `3` | Total attempts, bounded to 1–5 |
| `NOTIFICATION_RETRY_DELAY_MS` | `100` | Fixed delay between transient failures |

For an app on port 18080, also set `NOTIFICATION_BASE_URL=http://localhost:18080`. For a second instance on 8081, point the scheduler at `http://localhost:8081`; the same artifact provides the mock controller. The default deployment demonstrates HTTP between logical components, with the receiver co-located in the application process.

## Health, metrics and failure recovery

```sh
curl -i http://localhost:8080/health
curl -s http://localhost:8080/actuator/metrics/salon.bookings.success
curl -s http://localhost:8080/actuator/metrics/salon.cancellations.success
curl -s http://localhost:8080/actuator/metrics/salon.notifications.fallback
```

Public `/health` checks PostgreSQL with `SELECT 1`, returning `status`, `application`, and `database`, with HTTP 200/503. Actuator/Micrometer counters count committed bookings, committed cancellations and ordinary gateway fallbacks, and reset on restart. App logs use `event=` names at INFO for commits/receipts, WARN for retry/fallback, and ERROR for unexpected/database failures. Management and mock routes are public for the local demo; restrict their network exposure for Internet deployment.

To simulate notification failure locally, restart the app with `NOTIFICATION_BASE_URL=http://127.0.0.1:1`. Book and cancel a future appointment; verify successful responses/history and WARN logs. Restore the original URL and restart for new deliveries. Restarting clears in-memory sessions, so log in again. Failed historical deliveries are not automatically replayed. Receiver receipts make an operator's exact-payload replay safe if the same ID is retained.

## Docker deployment

Docker Desktop or a working Docker daemon is required. Copy the example environment file and replace its placeholder with a locally generated password. Keep `.env` private.

```sh
cp .env.example .env
# Edit .env: replace REPLACE_WITH_A_LOCAL_RANDOM_VALUE.
docker compose up --build
```

Open http://localhost:8080/web. Compose uses PostgreSQL service `db`, a persistent volume, readiness ordering, and application `/health` probes. Schema and seed initialization remain active. Stop with `docker compose down`; the database volume persists. Existing volumes retain their original database password even if `.env` changes.

A repeatable Compose failure/recovery demo:

```sh
NOTIFICATION_BASE_URL=http://127.0.0.1:1 docker compose up -d --force-recreate app
docker compose logs --tail=100 app
# Log in again, book/cancel, then restore:
NOTIFICATION_BASE_URL=http://app:8080 docker compose up -d --force-recreate app
```

## Milestone 3 verification and submission

GitHub Actions runs `./mvnw -B clean verify` on Java 21 with PostgreSQL on every push/PR. It asserts the local database suite actually runs. Neither unit tests nor integration tests require an app running on port 8080; the test gateway uses an ephemeral HTTP server or a deliberately unavailable destination.

Verified locally on October 27, 2026: **59 passed, 0 failures/errors, 16 skipped** out of 75 discovered tests. All 16 PostgreSQL integration cases passed; only the duplicate Docker-backed variant skipped. The packaged app passed **58 HTTP smoke checks**, including actual booking/cancellation notification delivery. Compose configuration validated, but image build/startup and hosted CI were not run. Local verification used JDK 22.0.2 with compiler release 21; Java 21 runtime is configured in CI/Docker and remains unverified here. See [verification details](docs/Milestone3_Verification.md).

- [Report (PDF)](docs/CMPE172_Milestone3_Report.pdf) and [editable Markdown](docs/CMPE172_Milestone3_Report.md).
- [Distribution diagram (PNG)](docs/Milestone3_Distribution.png), [PDF](docs/Milestone3_Distribution.pdf), and [Mermaid](docs/Milestone3_Distribution.mmd).
- [5–7 minute code-walkthrough script](Milestone3_Demo_Script.md).
- [Machine-readable test counts](docs/Milestone3_Test_Results.json).
- `CMPE172_Milestone3_FirstName_LastName.zip` in the repository root. Replace name placeholders before submission; no submission URL is invented.

Rebuild the archive with `python3 scripts/package_milestone3.py`. It includes source, tests, Docker/CI configuration and documentation, excluding `.git`, secrets, caches and compiled files. The PDF/diagram renderer is an optional documentation tool: install `reportlab==5.1.0` in a separate Python environment, then run `python scripts/render_milestone3.py`; Java builds do not need it.

Review and commit on the current `milestone3` branch when ready:

```sh
git status --short
git diff --check
git diff
# Inspect new files too; then stage and review everything:
git add .
git diff --cached --stat
git diff --cached
git commit -m "Complete Milestone 3 integration and deployment"
```

No commits or pushes were made by the implementation task. The generated ZIP is ignored by Git and should be submitted separately.
