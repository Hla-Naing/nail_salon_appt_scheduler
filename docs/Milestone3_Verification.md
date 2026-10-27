# Milestone 3 verification

Verified on October 27, 2026. This is a local verification record, not a claim that GitHub Actions or Docker deployment ran.

- `./mvnw -B clean verify` with the dedicated PostgreSQL test environment: **BUILD SUCCESS**.
- JUnit: **75 tests discovered, 59 passed, 0 failures, 0 errors, 16 skipped**.
- `LocalPostgresTest`: **16 tests passed**, including independent-connection commit visibility before actual HTTP failure, cancellation persistence, rollback suppression, concurrent booking protection, receiver deduplication and public health/metrics.
- `PostgresContainerTest`: **16 skipped**, because the Docker daemon was unavailable. It inherits the same workflow suite that ran against local PostgreSQL.
- `NotificationGatewayTest`: **7 passed** against an ephemeral JDK HTTP server or an unavailable local port. Includes actual response timeouts, bounded retries, recovery and fallback.
- Local runtime: OpenJDK **22.0.2**, compiling with Maven `release 21`. Java 21 runtime execution is configured in Docker and CI but was not locally verified.
- PostgreSQL: isolated PostgreSQL 14.19 (Homebrew) cluster under `/tmp`, port 55432; test database `salon_m2_test`. No development database used.
- `SALON_DB_PASSWORD=compose-validation-placeholder docker compose config --quiet`: passed.
- Docker image build, container startup and container health checks: **not run**, because Docker Desktop's daemon socket was absent.
- GitHub Actions: workflow created and inspected; no push or hosted CI run performed.

The machine-readable suite counts are in `Milestone3_Test_Results.json`. Raw Surefire XML is regenerated in `target/surefire-reports/`; it is omitted from the submission archive because it contains machine-specific system properties.

Packaged-application checks also passed: **58 curl HTTP smoke checks** against an app on port 18080 and a separate disposable `salon_m3_demo` database. Actual booking and cancellation notifications reached the mock controller over HTTP. A live database outage check stopped only the temporary cluster: `/health` returned HTTP **503** with database `DOWN`, then returned **200/UP** after PostgreSQL restarted. Compose/CI target PostgreSQL 16; container version compatibility remains unverified locally.
