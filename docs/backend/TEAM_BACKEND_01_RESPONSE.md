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

---

## Phase B addendum — operational local-demo authentication

The historical Phase A checkpoint above remains `FOUNDATION_SHA=05e55583e38b942b58a208c78dedd0959a101e62`. Phase B advances the same owner branch without changing `V001`–`V005`, the frozen V1 DTOs/API document, migration ownership, seller IDs, Android `app/**`, or the root Gradle build. The full distinct `PHASE_B_SHA` is in the owner's final pushed-commit handoff (a commit cannot contain its own SHA).

### Live V1 routes and semantics

- `POST /api/v1/auth/register` now decodes the frozen `RegisterRequest`, normalizes email, validates name/password/department, rejects unknown fields including role/admin, inserts a customer with `STUDENT` or `FACULTY`, and returns `201 RegisterResponse` without a token. A duplicate normalized email returns `409 EMAIL_IN_USE`.
- `POST /api/v1/auth/login` verifies a salted slow password hash and `ACTIVE` user status, returns `200 LoginResponse` with one 32-byte-random base64url bearer and UTC `expiresAt`, and persists only SHA-256 of the token. Unknown email, wrong password and inactive account share `401 INVALID_CREDENTIALS`. Sessions last 24 hours.
- `POST /api/v1/auth/logout` requires a live bearer, revokes only its own session in a transaction, returns empty `204`; subsequent bearer use is `401 UNAUTHENTICATED`.
- `GET /api/v1/auth/me` returns `MeResponse.user` from live DB state with no token. It includes only active seller memberships with nonempty *effective* capabilities after same-seller supervisor and current account checks. `platformAdmin` is a separate live flag.
- `GET /api/v1/me/profile` returns only the live caller's `ProfileDto`. `PATCH /api/v1/me/profile` updates only caller `fullName`, `affiliation`, `department` under a rechecked, locked session/user transaction and returns the same shape. Omitted fields stay unchanged; explicit `department:null` clears it; explicit null for name or affiliation is `400`. Unknown keys including email/user ID/roles are `400 INVALID_JSON`. Nullable output fields are emitted explicitly.

Auth failures use the frozen `{ "error": { "code", "message", "details": null } }` envelope. JSON input is capped at 16 KiB and decoded with unknown keys rejected. SQL exceptions and traces are never returned in HTTP responses. `PasswordHasher` uses PBKDF2-HMAC-SHA256 with a fresh 256-bit salt, 600,000 iterations and a versioned stored format. The service never persists raw passwords or bearer tokens. Session revocation/expiry and disabled accounts are checked on every request. The original seller/admin helper signatures remain compatible; delegated capability evaluation now also drops access immediately if a supervisor's *user* account is disabled.

### Explicit local-only demo personas

These **public fixture credentials are for an isolated local demo only**. They are never inserted unless `RIT_ENV=local` and `DEMO_SEED_ENABLED=true`; `test`/`production` modes reject demo seed mode. Backend user UUIDs are stable, distinct and derived from normalized fixture emails, not Android debug account IDs. `ON CONFLICT` never overwrites a pre-existing email or grants privileges to an account that merely registered that address.

| Email | Demo password | Identity |
| --- | --- | --- |
| `student.arya@demo.ritcanteen.test` | `RitDemo!Student01` | Student customer |
| `student.kiran@demo.ritcanteen.test` | `RitDemo!Student02` | Student customer |
| `faculty.maya@demo.ritcanteen.test` | `RitDemo!Faculty01` | Faculty customer |
| `owner.main@demo.ritcanteen.test` | `RitDemo!OwnerMain01` | OWNER, `main-canteen` |
| `owner.secondary@demo.ritcanteen.test` | `RitDemo!OwnerOther01` | OWNER, `north-counter` |
| `manager.main@demo.ritcanteen.test` | `RitDemo!Manager01` | MANAGER, `main-canteen`, supervised by its owner |
| `staff.main@demo.ritcanteen.test` | `RitDemo!Staff01` | STAFF, `main-canteen`, supervised by its manager |
| `admin.platform@demo.ritcanteen.test` | `RitDemo!Admin01` | Platform admin, no seller membership |

Manager's direct grants mirror the Android local-demo manager fixture: `bulk.view`, `bulk.respond`, `operations.history`, `catalog.edit`, `availability.edit`, `hours.edit`, `promotions.manage`, `employees.manage`, `analytics.view`. Staff receives only `bulk.view`. Effective grants intersect with live supervisors; owner receives the frozen full set. No grant is silently restored after removal on restart. Four seller seeds remain unchanged.

### Verification and integration boundary

Using a disposable `postgres:16-alpine` Podman container bound only to `127.0.0.1:5433`, a separate isolated test DB, private ignored `backend/.env` with generated local password, and no shared database: `env RIT_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/rit_canteen_test RIT_TEST_DATABASE_USER=rit_canteen RIT_TEST_DATABASE_PASSWORD=<private-local-value> ./gradlew clean build --no-daemon --max-workers=1` passed **13 tests, 0 failures, 0 skips**. These include four configuration tests, the focused later-migration compatibility test, Phase A real-DB regression tests and Phase B real-DB/Ktor auth tests. The inherited migration assertion now verifies core `001`–`005` in order and successful while permitting later `V020+`/`V040+` migrations. No teammate migration was merged to run that scenario end-to-end yet.

Live Netty smoke against a separate disposable local app DB: `/health` `200`, `/ready` `200`, registration `201`, login `200`, auth/me `200`, profile GET `200`, profile PATCH `200`, logout `204`, and same bearer after logout `401 UNAUTHENTICATED`. The bearer and local DB password were not printed or committed. Automated tests also covered eight distinct fixture logins/UUIDs and non-plaintext hashes, duplicate/malformed/privilege-field rejection, cross-seller denial, wrong/disabled credentials, two independent sessions, expiry, profile persistence over restart, supervisor disabling, revoked grant staying revoked after restart, seed preservation of edited profile/password/UUID, and a fresh non-seeded test schema with zero demo users/sellers.

Phase B changed `backend/README.md`, `Application.kt`, `AuthorizationService.kt`, `Database.kt`, `DatabaseFoundationTest.kt`; added `AccountService.kt`, `PasswordHasher.kt`, `DemoIdentitySeed.kt`, `AuthIntegrationTest.kt`; and appended this response. **No new migration** was needed because `V001`–`V005` already support these records. No commerce/admin module was merged or mounted; no Android API integration was started. Remaining production work includes anti-abuse/rate-limiting, transport/TLS deployment, password reset/verification/OTP policy, session cleanup, operational monitoring and integration with the independent commerce/engagement modules. This is a local-demo auth service, not a production identity deployment.
