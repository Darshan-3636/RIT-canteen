# RIT Canteen backend foundation handoff — V1 frozen checkpoint

The immutable checkpoint is the **full `FOUNDATION_SHA` supplied by the project lead in the Codex handoff**, not this file's own commit hash. Contributors 2 and 3 must branch from that SHA. This document describes the Phase A code as committed. Any incompatible change needs lead coordination and a versioned amendment; do not edit applied Flyway migrations.

## Scope and startup

The independent Gradle build is `backend/`; its own `./gradlew` and `gradlew.bat` run without changing root Android Gradle. Main entry: `edu.msrit.canteen.backend.ApplicationKt.main`. `BackendConfig.fromEnvironment()` requires PostgreSQL JDBC URL, user and non-placeholder password. Defaults: `RIT_ENV=local`, `HTTP_HOST=127.0.0.1`, `HTTP_PORT=8080`, `DEMO_SEED_ENABLED=false`. Seller-local calendar zone is `Asia/Kolkata`; application clock and DB connection timezone are UTC. `RIT_ENV=production` rejects demo seeding. The startup sequence creates a Hikari PostgreSQL pool, runs Flyway, conditionally inserts four seller fixtures, then starts Netty. A DB/migration failure aborts startup; `/ready` probes a live DB connection. See `backend/README.md` and `backend/.env.example` for Linux/Windows setup.

Phase A does **not** seed demo user accounts, issue sessions, accept login, or implement Phase B profile writes. The reserved auth routes deliberately return `503 AUTH_NOT_READY`; protected ones first demand a valid bearer session and otherwise return `401`. No route grants an anonymous role.

## Canonical DDL and migration ownership

The SQL files below are the canonical DDL; this table records every persisted column and key so teammates can design foreign keys without inventing a parallel schema. PostgreSQL `TIMESTAMPTZ` stores instants; HTTP serializes them as UTC ISO-8601 strings. Future currency is integer **paise**, never floating-point rupees.

| File | Table / columns | Keys and invariants |
| --- | --- | --- |
| `backend/src/main/resources/db/migration/V001__users.sql` | `users(id UUID, email TEXT, password_hash TEXT, full_name TEXT, affiliation TEXT NULL, department TEXT NULL, status TEXT, is_platform_admin BOOLEAN, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ)` | `id` PK; normalized lower/trimmed unique email; nonblank hash/name; affiliation `STUDENT\|FACULTY`; status `ACTIVE\|SUSPENDED\|DISABLED`; independent platform-admin flag. |
| `V002__sellers.sql` | `sellers(id TEXT, name TEXT, description TEXT, active BOOLEAN, is_main_canteen BOOLEAN, created_at, updated_at)` | Stable slug PK; only one `is_main_canteen=true` row. No invented location/hours/verification. |
| `V003__seller_memberships.sql` | `seller_memberships(id UUID, user_id UUID, seller_id TEXT, role TEXT, supervisor_membership_id UUID NULL, active BOOLEAN, created_at, updated_at)` | UUID PK; user/seller FKs `RESTRICT`; unique `(user_id,seller_id)`; `OWNER\|MANAGER\|STAFF`; owner has no supervisor, manager has same-seller owner supervisor, staff has same-seller manager supervisor, enforced by check + trigger. Revocation uses `active=false`. |
| `V004__seller_membership_capabilities.sql` | `seller_membership_capabilities(membership_id UUID, capability TEXT, granted_at TIMESTAMPTZ)` | `(membership_id,capability)` PK; membership FK `ON DELETE CASCADE`; dot-separated lowercase wire string. Effective grants additionally intersect live supervisor grants. |
| `V005__sessions.sql` | `sessions(id UUID, user_id UUID, token_hash BYTEA, created_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ NULL)` | UUID PK; user FK `RESTRICT`; unique 32-byte SHA-256 bearer hash; expiry after creation; no plaintext token column. |

Only Backend 1 owns core migrations in `V001`–`V019` and these five tables. `V006`–`V019` are reserved/unallocated, not missing migrations. Backend 2 owns `V020`–`V039`; Backend 3 owns `V040`–`V059`. Place uniquely numbered SQL in the same `backend/src/main/resources/db/migration/` folder. Flyway scans `classpath:db/migration`, applies in numeric order and checks names/checksums. Never rewrite `V001`–`V005` after this checkpoint. Core FK deletion policy preserves users/sellers/memberships; only explicit capability rows cascade on membership deletion.

On **local** `DEMO_SEED_ENABLED=true`, `BackendBootstrap.open` inserts exactly these IDs with `ON CONFLICT (id) DO NOTHING`: `main-canteen` (Main Canteen), `north-counter` (North Counter · demo), `coffee-corner` (Coffee Corner · demo), `campus-pantry` (Campus Pantry · demo). The descriptions mirror `PreviewCatalog` semantics, do not assert verified vendors, and are not overwritten on rerun. The twelve Android item IDs remain unchanged and are **not** seeded in Phase A. Backend 2 may seed catalog rows under its own migration range. Android bundles imagery by stable ID; backend image hosting is not required for this local demo.

## Exact public Kotlin symbols and authorization boundary

- `edu.msrit.canteen.backend.core.BackendConfig` and `RuntimeEnvironment`; `BackendConfig.fromEnvironment(env: Map<String,String> = System.getenv()): BackendConfig`.
- `edu.msrit.canteen.backend.core.BackendContext(val dataSource: DataSource, val config: BackendConfig, val clock: Clock = Clock.systemUTC())`; `context.authorization: AuthorizationService`.
- `edu.msrit.canteen.backend.core.BackendBootstrap.open(config: BackendConfig): BackendContext`; `BackendBootstrap.isReady(dataSource: DataSource): Boolean`; `BackendContext.close()`.
- `edu.msrit.canteen.backend.core.transaction`: `fun <T> BackendContext.transaction(block: (Connection) -> T): T`. It commits on success, rolls back on failure. Guarded writes must recheck permission inside this same transaction.
- `edu.msrit.canteen.backend.auth.AuthenticatedPrincipal(userId: UUID, sessionId: UUID, email: String, fullName: String, affiliation: CustomerAffiliation?, platformAdmin: Boolean)`.
- `edu.msrit.canteen.backend.auth.AuthorizationService.authenticateBearer(rawToken: String): AuthenticatedPrincipal?`; `isPlatformAdmin(principal)` and `isPlatformAdmin(connection, principal)`; `hasSellerCapability(principal, sellerId, capability)` and `hasSellerCapability(connection, principal, sellerId, capability)`. All query live user/session/membership state. The connection overloads are for same-transaction writes.
- `edu.msrit.canteen.backend.api.ApplicationCall.requireAuthenticatedUser(context: BackendContext): AuthenticatedPrincipal?`; `.requireSellerCapability(context, sellerId: String, capability: String): AuthenticatedPrincipal?`; `.requirePlatformAdmin(context): AuthenticatedPrincipal?`. These emit 401/403 and return `null` when denied. Use `?: return@get` (or the matching route lambda label) after a call.
- `edu.msrit.canteen.backend.api.ApplicationCall.respondApiError(status: HttpStatusCode, code: String, message: String)` emits the frozen envelope.
- `edu.msrit.canteen.backend.ApplicationKt.backendModule(context: BackendContext)` installs JSON, error handling and core routes. Only the lead edits this file when merging contributor modules.

Capability wires: `bulk.view`, `bulk.respond`, `operations.history`, `catalog.edit`, `availability.edit`, `hours.edit`, `promotions.manage`, `employees.manage`, `profile.edit`, `analytics.view`, `reports.export`. `OWNER` receives this set when its membership is active; `MANAGER` and `STAFF` receive only direct grants intersected with each live supervisor's effective set. Platform admin is a separate live `users.is_platform_admin` flag and never implicitly a seller member. Capability checks reject unknown wires. Client role flags are ignored. Session revocation/expiry and disabled users invalidate principals. Server handlers must scope records by authenticated user/seller and recheck capability within any mutation transaction.

## Route and module registration seam

Core routes: `GET /health`, `GET /ready`, and reserved `/api/v1/auth/*` plus `/api/v1/me/profile` paths; response contracts are in `API_CONTRACT_V1.md`. HTTP is JSON with `encodeDefaults=true`, explicit `null`, and unknown request keys rejected when decoded into DTOs. Unknown paths return a machine-readable 404. No stack trace is returned to clients.

Backend 2 should implement **in its own file/package** `fun io.ktor.server.routing.Route.registerCommerceRoutes(context: BackendContext)` in `edu.msrit.canteen.backend.features.commerce`. Backend 3 should implement `fun Route.registerEngagementRoutes(context: BackendContext)` in `edu.msrit.canteen.backend.features.engagement`. These functions are intentionally **not defined or called** in the foundation, so neither team changes `Application.kt` or collides with the other. The lead imports and invokes them under `route("/api/v1")` after merging. Contributors can register them directly inside `testApplication` for isolated route tests. Do not assume these modules are already mounted at the checkpoint.

DTO types live in `edu.msrit.canteen.backend.api.ApiDtos.kt`: `RegisterRequest`, `LoginRequest`, `RegisterResponse`, `LoginResponse`, `MeResponse`, `ProfileDto`, `UpdateProfileRequest`, `UserIdentityDto`, `SellerMembershipDto`, `CustomerAffiliation`, `SellerRole`, `ApiErrorEnvelope`, `ApiError`. Enum wire values are uppercase exactly as shown in schema. UTC time strings use `Instant.toString()` shape. Registration is customer-only and has **no role field**; unknown fields fail decoding once Phase B handlers consume it. Phase B must not change these published shapes silently.

Errors use `{ "error": { "code": "SOME_CODE", "message": "Human-readable description", "details": null } }`. Use `400` invalid input, `401` unauthenticated, `403` forbidden, `404` missing/not visible, `409` conflict/obsolete revision, `422` unfulfillable rule, `429` throttled when implemented, `503` unavailable/Phase-A stub. Do not expose SQL or stack traces.

## Verification and compatibility

From `backend/`: `./gradlew test --no-daemon --max-workers=1`. For PostgreSQL tests, set `RIT_TEST_DATABASE_URL`, `RIT_TEST_DATABASE_USER`, `RIT_TEST_DATABASE_PASSWORD` to an **isolated disposable DB**; the test suite runs migrations, verifies four idempotent seeds, routes, live delegated capabilities, cross-seller denial and revocation. The live smoke command is `./gradlew run` with local `DATABASE_*`, then `curl -i http://127.0.0.1:8080/health` and `/ready`. Exact command results are in `TEAM_BACKEND_01_RESPONSE.md` and the checkpoint handoff message. The foundation contains no real auth token issuer, item/order tables, payment verification or Android API wiring.
