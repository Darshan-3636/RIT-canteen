# Backend Person 1 — Phase A foundation checkpoint response

## Git and scope

- Starting base: `89123939c3ad9370b486f3f7251efb26ae778780` from `feat/rit-demo-auth-catalog-images`, verified against the GitHub remote before branching.
- Isolated worktree/branch: `feat/rit-backend-foundation-auth`.
- The immutable full `FOUNDATION_SHA` is supplied **outside this commit** in the project-lead handoff message, because a commit cannot contain its own hash. Phase B final SHA: **not applicable; Phase B was not started** under the owner's explicit stop instruction.
- No `app/**`, Android UI/image/test, root Gradle, `main`, or tested integration branch was changed. No commerce/admin feature implementation or Android API client was added.

## Deliverables and exact schema

`backend/` is a standalone Gradle Kotlin/JVM + Ktor + Netty service with kotlinx.serialization JSON, Hikari PostgreSQL pool, Flyway, loopback-first env config, a Docker Compose PostgreSQL example, a local `.env.example`, own Gradle wrapper and startup README. Core migrations in `backend/src/main/resources/db/migration/` are `V001__users.sql`, `V002__sellers.sql`, `V003__seller_memberships.sql`, `V004__seller_membership_capabilities.sql`, `V005__sessions.sql`. The exact columns, keys, checks, supervisor trigger, FK deletion rules, migration ranges and five canonical DDL file links are in `FOUNDATION_HANDOFF.md`. `V006`–`V019` remain reserved for Backend 1; Backend 2 uses `V020`–`V039`, Backend 3 uses `V040`–`V059`.

The four exact preview seller IDs (`main-canteen`, `north-counter`, `coffee-corner`, `campus-pantry`) are idempotently inserted only with `DEMO_SEED_ENABLED=true` and `RIT_ENV=local`. The fixture descriptions match Android semantics without asserting verified location/hours/vendor status. Phase A does not seed any of the eight demo user accounts or any item rows; those belong to Phase B and Backend 2 respectively. Android artwork remains bundled and keyed by stable IDs.

## Routes, DTOs and helper interfaces

Live: `GET /health` and `GET /ready`. The six `/api/v1/auth/*` and `/api/v1/me/profile` methods listed in `API_CONTRACT_V1.md` are registered but deliberately fail closed with `503 AUTH_NOT_READY` until Phase B; protected routes first return `401` without a valid bearer session. No successful login/registration/profile mutation is claimed. JSON errors use `{ "error": { "code": "...", "message": "...", "details": null } }`; uncaught errors do not expose a stack trace in the response.

Public Kotlin types and signatures are frozen in `FOUNDATION_HANDOFF.md`: `BackendConfig`, `BackendContext`, `BackendBootstrap`, `BackendContext.transaction`, `AuthenticatedPrincipal`, `AuthorizationService`, `ApplicationCall.requireAuthenticatedUser`, `.requireSellerCapability`, `.requirePlatformAdmin`, `respondApiError`, and the DTOs/enums in `ApiDtos.kt`. The future `Route.registerCommerceRoutes(context)` and `Route.registerEngagementRoutes(context)` package/signature seams are documented but intentionally not mounted or stubbed. Only the lead will import/mount them after merge.

Seller capability wires are `bulk.view`, `bulk.respond`, `operations.history`, `catalog.edit`, `availability.edit`, `hours.edit`, `promotions.manage`, `employees.manage`, `profile.edit`, `analytics.view`, `reports.export`. Owner, manager and staff form a live same-seller chain; manager/staff direct grants intersect active supervisor grants. Session token lookup uses a 32-byte SHA-256 hash, expiry and revocation; no bearer plaintext column exists. Platform administration is distinct from seller ownership. Guarded writes must recheck with the connection overload inside one transaction.

## Actual verification

- `env RIT_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/rit_canteen_test RIT_TEST_DATABASE_USER=rit_canteen RIT_TEST_DATABASE_PASSWORD=<local-test-value> ./gradlew test --no-daemon --max-workers=1` from `backend/`: **7 tests passed, 0 failures, 0 skips** (four configuration tests, three real-PostgreSQL integration tests). The integration tests covered migration and seller-seed idempotency, HTTP health/readiness/fail-closed auth, delegated seller capability, cross-seller denial, supervisor revocation and session revocation. The test DB was isolated and disposable.
- PostgreSQL 16 Alpine was run locally in a disposable Podman container bound to `127.0.0.1:5433`; no external DB service or owner data was used. A separate app DB showed Flyway history `001`–`005` with `success=true` and exactly the four seller IDs above.
- Live Netty server with local PostgreSQL: `curl -i http://127.0.0.1:8080/health` returned `200 {"status":"ok"}`; `/ready` returned `200 {"status":"ready"}`. `POST /api/v1/auth/login` returned `503 AUTH_NOT_READY`; `GET /api/v1/auth/me` without token returned `401 UNAUTHENTICATED`.
- Final clean verification after documentation/logging polish: `./gradlew clean build --no-daemon --max-workers=1` with the isolated PostgreSQL test environment completed successfully; all **7 tests passed, 0 failed, 0 skipped**. The Gradle distribution archives were assembled. No Android device test was applicable because this checkpoint changes no Android code or client integration.

## Security, deviations and unfinished work

The server and Compose port bind to loopback by default. `.env`, Gradle outputs, logs, DB volumes and dumps are ignored; the committed `.env.example` has only a placeholder. `RIT_ENV=production` rejects demo seed mode. Database errors abort startup, and `/ready` fails when DB is unavailable. Response errors are sanitized. The development fixtures are not a production identity or authorization system.

The attached plan's broader objective includes Phase B auth, but the owner explicitly requested **Phase A only**. Therefore no password verification/registration/session issuance, eight-user seed, persistent profile handler, rate limiting, real OTP, payment, commerce/admin routes, or Android networking was implemented. The three PostgreSQL tests require a disposable DB and skip when test env vars are absent; they were run here with one. Docker was unavailable, so the real smoke test used Podman with official `postgres:16-alpine` instead. No known Phase A functional test remains failing.

## Contributor startup

Clone/fetch the published feature branch and branch from the **full `FOUNDATION_SHA`**, not its moving future HEAD. Read `backend/README.md`, `FOUNDATION_HANDOFF.md` and `API_CONTRACT_V1.md`. Copy `backend/.env.example` to a private `.env`, set a unique local PostgreSQL password, and run backend `./gradlew test` and `./gradlew run` (Windows: `.\gradlew.bat`). Do not edit core migrations/auth symbols or root Android Gradle; use the assigned migration range and route registration seam. The project owner should share this checkpoint before any Phase B work begins.
