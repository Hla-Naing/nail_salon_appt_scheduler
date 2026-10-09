# Milestone 2 verification and changed files

Verified on October 8, 2026 (America/Los_Angeles), on branch `milestone-2`.

## Environment and database isolation

- Maven compiled the project with `--release 21` as configured in `pom.xml`.
- The available runtime was OpenJDK **22.0.2**, not Java 21. Execution on a Java 21 JVM remains a local submission check; no Java 22 APIs or language features were introduced.
- PostgreSQL 16 ran in an isolated temporary cluster at `/tmp/salon-m2-pg`, listening only on localhost port **55432**.
- The temporary smoke application and temporary PostgreSQL server were stopped after verification.
- Automated tests used `salon_m2_test`; the running app/curl checks used a separate `salon_m2_smoke` database.
- The existing `nail_salon_db` development database was not used or modified during verification.
- Docker was unavailable. The Testcontainers variant compiled and was explicitly skipped. The identical PostgreSQL workflow suite ran through `LocalPostgresTest`.

## Commands and results

The test commands were run with:

```sh
export SALON_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/salon_m2_test
export SALON_TEST_DB_USER=salon_test
./mvnw -B clean test
./mvnw -B clean package
```

Both commands completed with **BUILD SUCCESS**. Final results: **41 discovered, 35 executed and passed, 0 failures, 0 errors, 6 skipped**. The six skips are the Docker variant; all six local PostgreSQL integration tests executed successfully.

| Test class | Executed / passed | Skipped |
| --- | ---: | ---: |
| AppointmentServiceTest | 15 | 0 |
| ProviderServiceTest | 8 | 0 |
| ValidationAndAuthTest | 5 | 0 |
| GlobalExceptionHandlerTest | 1 | 0 |
| LocalPostgresTest | 6 | 0 |
| PostgresContainerTest | 0 | 6 |

The two-thread test passed with exactly one successful booking, one conflict, and one active BOOKED database row. The integration suite also passed the ownership, RBAC, validation, filter/pagination, history/completion, unique-index, and HTML-rendering checks. The web test exercised login with a CSRF token, invalid login, missing-token rejection, booking, confirmation ownership, and protected page rendering.

The packaged app was started on port 18080 with:

```sh
java -jar target/nail_salon_appt_scheduler-0.0.1-SNAPSHOT.jar \
  --server.port=18080 \
  --spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/salon_m2_smoke \
  --spring.datasource.username=salon_test
python3 scripts/smoke_test.py --base-url http://localhost:18080 --disposable-database
```

**52 real HTTP checks passed**, using curl. These verified home/login/slot pages, all four seeded users, bad/blank credentials, session identity, logout, 401/403 role checks, invalid requests, pagination, each filter, provider creation, duplicate booking, customer/provider appointment scoping, other-customer cancellation denial, booked/other-provider slot-removal rejection, free-slot removal, booking confirmation, customer/provider pages, cancellation-history preservation, and $0/$10 fees. Temporary cookie jars were removed by the script.

Seed idempotence was checked by rerunning `seed.sql` against the smoke database and comparing counts: 4 users, 7 availability rows, and 2 appointments before and after; every INSERT reported zero new rows. This retained the cancellation history created by smoke checks.

`git diff --check` passed. The diff was reviewed for changes to existing JSON routes and transaction behavior. No ORM, JWT, scheduler, retry loop, or controller SQL was introduced. Generated session cookies are deleted from the working tree and ignored. `PasswordGenerator.java` was absent initially.

Initial dependency-cache/network restrictions were resolved with authorized execution. The filter integration test caught a missing SQL separator before ORDER BY; it was fixed and the full suite rerun successfully. These earlier failures are not represented as passing runs.

Build output is in `target/`, with Surefire reports in `target/surefire-reports/`. Temporary detailed logs from this session are `/tmp/salon-clean-test.log`, `/tmp/salon-final-package.log`, and `/tmp/salon-smoke.log`; they are not committed artifacts.

## Requirement checklist

- [x] Provider booking list, own availability creation/removal, and dashboard.
- [x] Persisted COMPLETED status on history reads; cancelled history preserved.
- [x] Jakarta request validation and service checks for IDs, pagination, and times.
- [x] Consistent JSON errors for 400/401/403/404/409 and generic 500.
- [x] READ_COMMITTED, SELECT FOR UPDATE, partial unique index, 409 conflict handling.
- [x] Service unit tests for booking, cancellation ownership, validation, and providers.
- [x] Real synchronized two-customer PostgreSQL booking test.
- [x] Thymeleaf + Bootstrap pages and server session/RBAC workflow.
- [x] Existing JSON APIs retained alongside `/web` pages.
- [x] Temporary role controller and tracked cookie files removed.
- [x] Fresh future seed data, idempotence, and additive schema update.
- [x] Automated tests, clean package, and real HTTP smoke verification.
- [x] Report draft and approximately 5–7 minute code walkthrough.
- [x] Diff review and complete changed-file inventory below.

## Remaining manual submission work and limits

1. Run with your Java **21** JDK before recording/submitting; this environment's JVM was 22.0.2 with a release-21 compilation target.
2. Start your local app, inspect the Bootstrap layout in a browser, and record your own screenshots and narrated video. HTML rendering was tested; a visual browser review/video was not performed.
3. Review the report in your own voice, add course-specific submission details, and commit/push the work on `milestone-2`. No commit or push was made by this task.
4. Docker execution remains unverified; use the documented dedicated PostgreSQL alternative if Docker is unavailable. The mandatory PostgreSQL concurrency behavior was actually tested successfully.

Completion is refreshed when appointment history is read, not by a scheduler. Seed slots are a one-time batch per initially empty provider; create later slots in the provider dashboard when they expire. Provider slots are independent records; detecting overlaps between different slots was not part of the requested milestone rules. The Bootstrap CDN requires internet access for styling.

## Complete file inventory

### Modified

- `.gitignore`
- `README.md`
- `pom.xml`
- `src/main/java/com/example/nail_salon_appt_scheduler/AppointmentRepository.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/AppointmentService.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/AuthService.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/CustomerAppointmentController.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/LoginController.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/SalonRepository.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/SalonService.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/SessionAuthService.java`
- `src/main/resources/application.properties`
- `src/main/resources/schema.sql`
- `src/main/resources/seed.sql`

### Created

- `docs/Milestone2_Code_Walkthrough.md`
- `docs/Milestone2_Report_Draft.md`
- `docs/Milestone2_Verification.md`
- `scripts/smoke_test.py`
- `src/main/java/com/example/nail_salon_appt_scheduler/GlobalExceptionHandler.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/ProviderAppointmentView.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/ProviderController.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/ProviderRepository.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/ProviderService.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/ProviderSlotView.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/SalonTime.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/WebController.java`
- `src/main/java/com/example/nail_salon_appt_scheduler/WebSecurityConfig.java`
- `src/main/resources/templates/appointments.html`
- `src/main/resources/templates/confirmation.html`
- `src/main/resources/templates/fragments.html`
- `src/main/resources/templates/home.html`
- `src/main/resources/templates/login.html`
- `src/main/resources/templates/provider.html`
- `src/main/resources/templates/slots.html`
- `src/main/resources/templates/web-error.html`
- `src/test/java/com/example/nail_salon_appt_scheduler/AppointmentServiceTest.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/GlobalExceptionHandlerTest.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/LocalPostgresTest.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/PostgresContainerTest.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/PostgresWorkflowSuite.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/ProviderServiceTest.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/ValidationAndAuthTest.java`
- `src/test/resources/application-test.properties`

### Removed

- `cookies.txt`
- `lily_cookies.txt`
- `maya_cookies.txt`
- `src/main/java/com/example/nail_salon_appt_scheduler/RoleTestController.java`
- `src/test/java/com/example/nail_salon_appt_scheduler/NailSalonApptSchedulerApplicationTests.java`

