# RIT Canteen backend — integrated local-demo API

This is an independent Kotlin/JVM Ktor service. Run Gradle **from this directory**; the Android root Gradle project is not involved. The integrated baseline mounts Phase B auth, Commerce, Engagement and Admin routes. Android networking lives in the app's debug source set.

## Requirements

- JDK 17 or newer (tested with JDK 25), Gradle wrapper included.
- PostgreSQL 16 (Docker Compose example below, or a local installation).
- Local TCP ports 5432 for PostgreSQL and 8080 for HTTP by default; both bind to `127.0.0.1`.

## Linux/macOS

```sh
cd backend
cp .env.example .env
# Edit .env: set DATABASE_PASSWORD to a unique local password. Do not commit it.
docker compose --env-file .env up -d postgres
set -a
. ./.env
set +a
./gradlew test
./gradlew run
```

If Podman is used instead of Docker, run `podman compose --env-file .env up -d postgres` where Compose support is installed, or start a PostgreSQL container manually bound to `127.0.0.1`. For a local PostgreSQL installation, set `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` to that database and skip Compose.

## Windows PowerShell

```powershell
Set-Location backend
Copy-Item .env.example .env
# Edit .env and replace the placeholder DATABASE_PASSWORD.
docker compose --env-file .env up -d postgres
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
  $parts = $_.Split('=', 2)
  [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}
.\gradlew.bat test
.\gradlew.bat run
```

`./gradlew test` runs four configuration tests and one migration-compatibility unit test without a DB. Set `RIT_TEST_DATABASE_URL`, `RIT_TEST_DATABASE_USER`, and `RIT_TEST_DATABASE_PASSWORD` for the PostgreSQL integration tests; use a **separate disposable database** such as `rit_canteen_test`. No test should point at a user-populated database. Integration tests migrate this database, run HTTP routes, test local fixtures and use isolated temporary identities/schema.

```sh
curl -i http://127.0.0.1:8080/health
curl -i http://127.0.0.1:8080/ready
```

The service runs Flyway before opening HTTP. If the database is unavailable or a migration fails, startup fails; if a running database becomes unreachable, `/ready` returns `503` while `/health` remains `200`. Migrations are in `src/main/resources/db/migration`. On local startup with `DEMO_SEED_ENABLED=true`, the four illustrative sellers, eight distinct local-demo users and 72 catalog items (18 per seller) are inserted idempotently. Seller memberships and grants are created only when the corresponding fixture user is first inserted; restarts never overwrite edited profiles/passwords or restore revoked grants. Commerce seeding preserves seller edits and only upgrades pristine older 12-item fixture rows. See `docs/backend/DEMO_CATALOG_PARITY.md`. `RIT_ENV=production` and `RIT_ENV=test` refuse demo seeding. No `.env`, PostgreSQL volume or bearer token belongs in Git.

For the owner-operated Render Free + Supabase Free cloud demo, use `RIT_ENV=cloud_demo` and `RIT_CLOUD_DEMO_SEED_ENABLED=true`, with the two private account pairs documented in `docs/cloud-demo/OWNER_DEPLOYMENT_HANDOFF.md`. Never set the local `DEMO_SEED_ENABLED` flag on a public service. Cloud mode rejects public registration by default; the owner may explicitly set `RIT_CLOUD_DEMO_SELF_SIGNUP_ENABLED=true` to allow direct-password Student/Faculty customer signup without OTP or institutional verification. Every such registration receives only an unprivileged customer identity and is immediately active; seller/admin authorization still requires private provisioning. This opt-in is for the demo only and is rejected by `RIT_ENV=production`. Cloud mode requires SSL PostgreSQL and binds `0.0.0.0`; Render supplies `PORT`. A small pool defaults to two connections. Cloud mode refuses public local fixture identities and does not provision an admin. This remains a demo-only service, not production. The reproducible Docker image is `backend/Dockerfile`.

After the catalog and two private identities are seeded, cloud-demo startup inserts five historical Main Canteen individual orders and six historical Main Canteen bulk requests. They use stable customer-scoped IDs, insert only once, and never update existing records on restart. Order lines and request instructions are visibly marked `[DEMO / SYNTHETIC]`; individual orders remain `DEMO_SAVED`, `demo_only=true`, `payment_verified=false`, and `demo_payment_outcome=NOT_ATTEMPTED`. The seller analytics API reads the dated bulk requests from PostgreSQL and reports `DEMO_UNVERIFIED` request counts, never verified revenue. This seed is disabled outside explicitly enabled `cloud_demo` mode and does not reset tables or replace catalog/seller edits.

The six live V1 auth/profile methods are `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `GET /api/v1/me/profile`, and `PATCH /api/v1/me/profile`. Registration creates a customer account without a session. Login returns a 24-hour opaque bearer; only its SHA-256 hash is stored. Supply `Authorization: Bearer <token>` on protected routes. Profile PATCH leaves omitted fields unchanged; explicit `department:null` clears that field, while explicit null for name or affiliation is rejected. Demo identities/passwords and exact authorization boundaries are in `docs/backend/TEAM_BACKEND_01_RESPONSE.md`. Commerce and Engagement/Admin are mounted under `/api/v1`; see `docs/backend/ANDROID_API_HANDOFF.md`, `COMMERCE_API.md` and `ENGAGEMENT_ADMIN_API.md`. The frozen `docs/backend/API_CONTRACT_V1.md` still describes the immutable Phase A state and V1 JSON shapes.

For a **separate disposable** app/test/fresh/legacy PostgreSQL database set, export `RIT_TEST_DATABASE_URL`, `RIT_TEST_FRESH_DATABASE_URL`, `RIT_TEST_LEGACY_DATABASE_URL`, `RIT_TEST_DATABASE_USER`, `RIT_TEST_DATABASE_PASSWORD`, `RIT_INTEGRATION_DISPOSABLE_FRESH_DB=YES` and `RIT_INTEGRATION_DISPOSABLE_LEGACY_DB=YES`. Run `./gradlew clean build --no-daemon --max-workers=1`; no integration test should be skipped. The fresh/legacy tests use unique schemas and never truncate existing data. To exercise live loopback HTTP, also set `RIT_INTEGRATION_DISPOSABLE_APP_DB=YES` and `RIT_INTEGRATION_DISPOSABLE_TEST_DB=YES`, then run `bash scripts/verify-integration.sh --smoke-only` for auth and `bash scripts/verify-mounted-backend.sh` for Commerce/Engagement/Admin. Both scripts refuse non-loopback or unconfirmed app databases. Do not point them at real merchant data.

Do not expose this demo server to a public interface. Future Android testing over USB requires an explicit `adb reverse tcp:8080 tcp:8080`; USB attachment alone does not make the local server reachable from the phone.
