# RIT Canteen backend — Phase B local-demo auth

This is an independent Kotlin/JVM Ktor service. Run Gradle **from this directory**; the Android root Gradle project is not involved. The Phase A checkpoint remains immutable at `05e55583e38b942b58a208c78dedd0959a101e62`; this branch adds Phase B registration, login, identity, profile and logout. Commerce/admin routes and Android networking are not included.

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

The service runs Flyway before opening HTTP. If the database is unavailable or a migration fails, startup fails; if a running database becomes unreachable, `/ready` returns `503` while `/health` remains `200`. Migrations are in `src/main/resources/db/migration`. On local startup with `DEMO_SEED_ENABLED=true`, the four illustrative sellers and eight distinct local-demo users are inserted idempotently. Seller memberships and grants are created only when the corresponding fixture user is first inserted; restarts never overwrite edited profiles/passwords or restore revoked grants. `RIT_ENV=production` and `RIT_ENV=test` refuse demo seeding. No `.env`, PostgreSQL volume or bearer token belongs in Git.

The six live V1 methods are `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `GET /api/v1/me/profile`, and `PATCH /api/v1/me/profile`. Registration creates a customer account without a session. Login returns a 24-hour opaque bearer; only its SHA-256 hash is stored. Supply `Authorization: Bearer <token>` on protected routes. Profile PATCH leaves omitted fields unchanged; explicit `department:null` clears that field, while explicit null for name or affiliation is rejected. Demo identities/passwords and exact authorization boundaries are in `docs/backend/TEAM_BACKEND_01_RESPONSE.md`. The frozen `docs/backend/API_CONTRACT_V1.md` still describes the immutable Phase A state and V1 JSON shapes.

Do not expose this demo server to a public interface. Future Android testing over USB requires an explicit `adb reverse tcp:8080 tcp:8080`; USB attachment alone does not make the local server reachable from the phone.
