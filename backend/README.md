# RIT Canteen backend — Phase A foundation

This is an independent Kotlin/JVM Ktor service. Run Gradle **from this directory**; the Android root Gradle project is not involved. At the foundation checkpoint, only `/health` and `/ready` are live. Authentication paths are reserved but return `503 AUTH_NOT_READY` (or `401` without a bearer token) until the separately authorized Phase B. No demo user can log in yet.

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

`./gradlew test` runs four configuration tests without a DB. Set `RIT_TEST_DATABASE_URL`, `RIT_TEST_DATABASE_USER`, and `RIT_TEST_DATABASE_PASSWORD` for the three additional PostgreSQL integration tests; use a **separate disposable database** such as `rit_canteen_test`. No test should point at a user-populated database. The integration test migrates its database and inserts then removes random test identities.

```sh
curl -i http://127.0.0.1:8080/health
curl -i http://127.0.0.1:8080/ready
```

The service runs Flyway before opening HTTP. If the database is unavailable or a migration fails, startup fails; if a running database becomes unreachable, `/ready` returns `503` while `/health` remains `200`. Migrations are in `src/main/resources/db/migration`. On local startup with `DEMO_SEED_ENABLED=true`, only the four *illustrative seller identities* are inserted (idempotently); user/password seeding belongs to Phase B. `RIT_ENV=production` refuses demo seeding. No `.env`, PostgreSQL volume, bearer token or signing secret belongs in Git.

Do not expose this demo server to a public interface. Future Android testing over USB requires an explicit `adb reverse tcp:8080 tcp:8080`; USB attachment alone does not make the local server reachable from the phone.
