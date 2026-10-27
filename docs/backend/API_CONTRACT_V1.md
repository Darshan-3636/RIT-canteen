# RIT Canteen backend API contract V1 — Phase A freeze

Base path: `/api/v1`. Requests/responses use JSON UTF-8. IDs are UUID strings for users/sessions/memberships and stable text slugs for sellers/items. Times are ISO-8601 UTC instants (for example `2026-10-27T12:00:00Z`); seller-local business date/hour calculations use `Asia/Kolkata`. Financial fields in future modules are integer **paise**, not floating-point rupees. Versioned incompatible changes require an explicit coordinated V2/amendment.

**Checkpoint state:** `/health` and `/ready` are operational. Auth/profile paths below are reserved and fail closed with `503 AUTH_NOT_READY` in Phase A; protected paths return `401` first when there is no valid bearer session. Phase B implements their successful behavior without changing the shapes below. Commerce and engagement routes are not mounted at this checkpoint.

| Method | Path | Auth | V1 successful result (Phase B unless noted) |
| --- | --- | --- | --- |
| GET | `/health` | none | `200 {"status":"ok"}`; live in Phase A |
| GET | `/ready` | none | `200 {"status":"ready"}` when DB reachable/migrations booted; `503 DATABASE_NOT_READY` otherwise; live in Phase A |
| POST | `/api/v1/auth/register` | none | `201 RegisterResponse`; customer only, no privileged role input |
| POST | `/api/v1/auth/login` | none | `200 LoginResponse`; bearer token only in this response |
| POST | `/api/v1/auth/logout` | bearer | `204` empty body; revoke current session |
| GET | `/api/v1/auth/me` | bearer | `200 MeResponse`; current live identity and effective memberships |
| GET | `/api/v1/me/profile` | bearer | `200 ProfileDto`; own profile only |
| PATCH | `/api/v1/me/profile` | bearer | `200 ProfileDto`; own profile only |

### DTO JSON examples

`POST /api/v1/auth/register`:

```json
{"fullName":"New Student","email":"new@example.test","password":"user-chosen-password","affiliation":"STUDENT","department":"CSE"}
```

`201` response; the customer must use Login next (registration does not mint a session):

```json
{"user":{"id":"b6f3f702-4c86-4f8b-8f17-bb873f223c20","email":"new@example.test","fullName":"New Student","affiliation":"STUDENT","platformAdmin":false,"memberships":[]}}
```

`POST /api/v1/auth/login`:

```json
{"email":"owner.main@demo.ritcanteen.test","password":"demo-password-supplied-locally"}
```

`200` response (sample token is illustrative, not a valid credential):

```json
{"accessToken":"illustrative-bearer-token-not-valid","expiresAt":"2026-10-27T12:00:00Z","user":{"id":"a258ff96-5cbc-4819-bc9f-9d7c4bd1132f","email":"owner.main@demo.ritcanteen.test","fullName":"Main Canteen Owner","affiliation":null,"platformAdmin":false,"memberships":[{"sellerId":"main-canteen","role":"OWNER","capabilities":["bulk.view","bulk.respond","catalog.edit"]}]}}
```

`GET /api/v1/auth/me` wraps that same `user` object but never returns a token: `{"user":{...}}`. `GET /api/v1/me/profile` returns `{"id":"...","email":"...","fullName":"...","affiliation":"STUDENT","department":"CSE"}`. `PATCH /api/v1/me/profile` accepts `{"fullName":"Updated Name","affiliation":"STUDENT","department":"ECE"}` and returns the updated `ProfileDto`; `email`/roles/memberships cannot be patched here. `null` is emitted explicitly for absent optional fields.

The eight future local-demo personas map by exact normalized email to distinct backend UUIDs, not to Android's debug-only account IDs. Seller memberships map `owner.main`→`main-canteen`, `owner.secondary`→`north-counter`, `manager.main`/`staff.main`→`main-canteen`; the platform administrator has independent platform authority. No persona is seeded in Phase A. Android catalog photo keys remain local assets keyed by stable seller/item IDs.

### Security and errors

Bearer header: `Authorization: Bearer <opaque-base64url-token>`. The server persists only its SHA-256 hash; all protected `/api/v1` handlers must use `requireAuthenticatedUser` and seller/admin helpers. Authentication, role and seller capability come from live server data, never a client field. Owner/manager/staff wires are `OWNER`, `MANAGER`, `STAFF`; customer affiliation wires are `STUDENT`, `FACULTY`. Unknown JSON fields are rejected by the configured serializer when a DTO is decoded. Current Phase A stubs do not decode request bodies, so they return `503` regardless of body.

```json
{"error":{"code":"SELLER_CAPABILITY_REQUIRED","message":"This seller action is not permitted","details":null}}
```

Use `400` for invalid input, `401` for missing/invalid/expired/revoked session, `403` for forbidden role/capability, `404` for absent or hidden resource, `409` for conflict/obsolete revision, `422` for unfulfillable rule, `429` if throttling is implemented, and `503` for unavailable dependency or intentionally inactive Phase-A auth. Never return stack traces. Success/payment/order state must not be asserted by a client.

### Contributor registration seams

Backend 2 implements `fun Route.registerCommerceRoutes(context: BackendContext)` in `edu.msrit.canteen.backend.features.commerce`, owns migrations `V020`–`V039`, and tests/mounts that function in isolation. Backend 3 implements `fun Route.registerEngagementRoutes(context: BackendContext)` in `edu.msrit.canteen.backend.features.engagement`, owns `V040`–`V059`, and tests/mounts it in isolation. The lead later invokes both within the `/api/v1` route after branch integration. Neither contributor edits core `Application.kt`, the five core tables, `V001`–`V019`, or these frozen DTO/error/auth symbols without coordinated amendment. Business endpoint paths are intentionally **not invented** at this checkpoint.
