# Commerce API — Backend 2

Feature branch: `feat/rit-backend-orders-catalog`
Foundation base: `05e55583e38b942b58a208c78dedd0959a101e62`

This module provides a local demonstration catalog and commerce request API. It does not implement Phase B login/session issuance, payments, seller acceptance outside the quote workflow, kitchen fulfillment, pickup handover, notifications, or Android HTTP integration. `AGREED_AWAITING_PAYMENT` means only that customer and seller approved the same current quote revision; it is not a paid or fulfilled order.

## Registration and startup

The lead mounts the feature after merging, without moving the shared registration seam:

```kotlin
routing {
    route("/api/v1") {
        registerCommerceRoutes(context)
    }
}
```

Import `edu.msrit.canteen.backend.features.commerce.registerCommerceRoutes`. The module itself registers paths relative to `/api/v1`; tests mount it in an isolated Ktor application. Local demonstration catalog setup is explicitly separate from migrations because core bootstrap inserts seller rows after Flyway:

```kotlin
val context = BackendBootstrap.open(config)
seedCommerceFixtures(context) // Only after bootstrap, LOCAL, and DEMO_SEED_ENABLED=true
```

`seedCommerceFixtures` refuses production and uses `ON CONFLICT DO NOTHING`, preserving seller edits on subsequent starts. The lead may add that call to local/dev bootstrap during integration; this branch does not change `Application.kt`. Seller open status is `UNKNOWN` until hours are configured. Regular bulk requests are rejected while hours are unknown.

## Data and conventions

- Currency is integer paise. No floating point values or client-supplied prices are accepted.
- Catalog image values are stable local asset keys (`imageKey`, matching item ID), not vendor image URLs.
- Catalog results are scoped to one seller. Published unavailable items remain visible only when `unavailableVisibility=SHOW`.
- The default bulk minimum is eight combined units and can be changed per seller by an authorized `hours.edit` member.
- Seller hours use ISO weekday numbers (Monday 1 through Sunday 7) and local `HH:mm` in `Asia/Kolkata`; an empty schedule means unknown. Closures are local calendar dates.
- New quotes expire after 24 hours. This is an implementation default pending project-owner policy confirmation.
- Mutations use transactions, server-side current catalog prices, live authorization and expected quote versions. The request customer cannot supply the seller approval.
- Errors use the frozen envelope `{ "error": { "code": "...", "message": "...", "details": null } }`. Standard status mapping: 400 invalid input, 401 missing/invalid bearer, 403 insufficient capability, 404 absent or not visible, 409 stale/terminal/idempotency conflict/expired, 422 business rule, 500 unexpected internal failure (no SQL detail returned).

## Seller directory and catalog

| Method | Path | Auth | Behavior |
|---|---|---|---|
| GET | `/sellers` | Guest | Active seller-first directory, capability modes, categories and configured minimum. Open status is explicitly `UNKNOWN`. |
| GET | `/sellers/{sellerId}` | Guest | One active seller. |
| GET | `/sellers/{sellerId}/menu?query=&categoryId=` | Guest | Search/filter within only that seller. |
| GET | `/sellers/{sellerId}/menu/{itemId}` | Guest | One published visible item in that seller. |
| PATCH | `/sellers/{sellerId}/menu/items/{itemId}/availability` | `availability.edit` | Set `AVAILABLE` or `UNAVAILABLE`; visibility remains unchanged. |
| PATCH | `/sellers/{sellerId}/menu/items/{itemId}` | `catalog.edit` | Update name, description, integer `pricePaise`, published, dietary or unavailable visibility. |
| PATCH | `/sellers/{sellerId}/bulk-policy` | `hours.edit` | Change `minimumUnits` (1–999), `specialPickupEnabled`, or both. |
| PUT | `/sellers/{sellerId}/hours` | `hours.edit` | Replace configured weekly schedule; `hours: []` marks hours unknown. |
| PUT | `/sellers/{sellerId}/closures/{localDate}` | `hours.edit` | Set a local date closure and optional reason. |
| DELETE | `/sellers/{sellerId}/closures/{localDate}` | `hours.edit` | Remove a closure. |

Example seller response:

```json
{
  "id":"main-canteen","name":"Main Canteen",
  "description":"Campus meals and familiar favourites.","active":true,
  "isMainCanteen":true,"openStatus":"UNKNOWN","bulkMinimumUnits":8,
  "orderingModes":["INDIVIDUAL_PREPARED_DEMO","BULK_REQUEST"],
  "categoryIds":["dosa","drinks","packs","rice","snacks"]
}
```

Example client-facing menu response from `GET /sellers/main-canteen/menu?categoryId=dosa`:

```json
{
  "sellerId":"main-canteen","categoryIds":["dosa","drinks","packs","rice","snacks"],
  "items":[{
    "id":"masala-dosa","sellerId":"main-canteen","name":"Masala Dosa",
    "description":"Crisp dosa with spiced potato filling, sambar and chutney.",
    "pricePaise":5000,"kind":"PREPARED","dietary":"VEGETARIAN",
    "availability":"AVAILABLE","published":true,
    "orderingModes":["INDIVIDUAL_PREPARED_DEMO","BULK_REQUEST"],
    "imageKey":"masala-dosa","categoryId":"dosa"
  }]
}
```

Guest menu GETs are intentionally read-only. Mutating routes call the frozen auth helpers and recheck seller capability inside the same transaction as the write. Owner/manager/staff delegation remains enforced by the foundation authorization service.

## Bulk request lifecycle

| Method | Path | Auth | Behavior |
|---|---|---|---|
| POST | `/bulk/requests` | Bearer customer | Create one seller request. Requires `Idempotency-Key`; prices are snapshotted from current published/available catalog. |
| GET | `/me/bulk/requests` | Bearer owner | Current revisions for this customer only. |
| GET | `/me/bulk/requests/{requestId}` | Bearer owner | Own request only; absent and other-customer IDs both return 404. |
| GET | `/sellers/{sellerId}/bulk/requests?status=` | `bulk.view` | Seller-scoped inbox; optional exact status. |
| GET | `/sellers/{sellerId}/bulk/requests/{requestId}` | `bulk.view` | Request visible to that seller only. |
| POST | `/sellers/{sellerId}/bulk/requests/{requestId}/counteroffers` | `bulk.respond` | Append a new immutable terms revision using current seller catalog prices; body has `expectedVersion`, `lines`, `pickupAt`, optional `specialPickupReason`. |
| POST | `/bulk/requests/{requestId}/customer-approval` | Bearer owner | Approve current revision with `{ "expectedVersion": 1 }`. |
| POST | `/sellers/{sellerId}/bulk/requests/{requestId}/seller-approval` | `bulk.respond` | Seller approval for current revision; actor must differ from request customer. |
| POST | `/sellers/{sellerId}/bulk/requests/{requestId}/special-pickup-decision` | `bulk.respond` | Separate seller decision on current special-pickup revision. |
| POST | `/bulk/requests/{requestId}/withdraw` | Bearer owner | Withdraw a nonterminal request using expected version. |
| GET | `/sellers/{sellerId}/pickup-schedule` | `bulk.view` | Returns an empty `confirmed` list with explanation; no payment or operational confirmation exists. |

Create body example:

```json
{
  "sellerId":"north-counter",
  "lines":[{"itemId":"north-lunch","quantity":8}],
  "pickupAt":"2026-11-02T06:30:00Z"
}
```

Header: `Idempotency-Key: <client-generated-opaque-key>`. A repeated key with identical request content replays the same request; a different payload with the same key receives 409. Each item must belong to the one selected seller, be published/available, and have quantity 1–999. Combined units must meet the seller minimum. Regular pickup requires a future instant within known seller hours and not on a closure date. Unknown hours are not treated as open. A special request requires the seller's special-pickup setting and a nonblank reason; it remains pending until the separate seller decision for that quote revision is approved.

Initial status is `SUBMITTED`. Counteroffers append a new immutable revision and status `COUNTEROFFER`; old approvals do not carry forward. Customer and seller approvals are recorded separately against `(requestId, version)`. When both current-revision approvals exist, and a special-pickup approval is present where applicable, state becomes `AGREED_AWAITING_PAYMENT`. Expiry, rejection and withdrawal are terminal. Reads lazily mark expired current revisions as `EXPIRED`. `PAID_CONFIRMED` is not a supported state or endpoint.

## Individual prepared-food demo history

| Method | Path | Auth | Behavior |
|---|---|---|---|
| POST | `/me/individual-demo-orders` | Bearer customer | Persist multiple Main Canteen `PREPARED` items at current server prices; quantities start at one, no bulk minimum. |
| GET | `/me/individual-demo-orders` | Bearer owner | Customer-specific demo history. |

Example body: `{ "lines": [{"itemId":"masala-dosa","quantity":1},{"itemId":"idli","quantity":2}] }`. The response includes `status: "DEMO_SAVED"`, `demoOnly: true`, and `paymentVerified: false`. Packaged products and secondary sellers cannot use this endpoint. It is not an operational order, kitchen acceptance, payment, delivery or pickup promise.

## Owned schema and fixtures

- `V020__commerce_catalog.sql`: categories, per-seller category eligibility, menu, seller policies, known weekly hours and local closures.
- `V021__bulk_requests.sql`: requests, immutable quote revisions/lines, version-scoped approvals and special-pickup decisions, audit events, idempotency.
- `V022__individual_demo_orders.sql`: customer-owned individual demo history and price-snapshotted lines.
- Demo seed categories: `dosa`, `rice`, `snacks`, `drinks`, `packs`.
- Demo seller IDs: `main-canteen`, `north-counter`, `coffee-corner`, `campus-pantry`.
- Demo item IDs and prices (paise): `masala-dosa` 5000, `veg-biryani` 8500, `filter-coffee` 3000, `idli` 3500, `lemon-rice` 4500 (seeded unavailable but visible), `water-bottle` 2000, `north-lunch` 9000, `north-snack` 6500, `corner-coffee` 3500, `corner-biscuits` 2500, `pantry-juice` 3000, `pantry-water` 2000.
- No user, membership or bearer fixtures are included in runtime seed. Integration tests create random users and sessions in the separate test DB and store only SHA-256 token hashes.

## Local verification

From `backend/`, use the local `.env` instructions in `README.md`. For PostgreSQL tests set `RIT_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/rit_canteen_test`, `RIT_TEST_DATABASE_USER`, and `RIT_TEST_DATABASE_PASSWORD`. Example commands:

```powershell
.\gradlew.bat clean assemble --no-daemon --max-workers=1
.\gradlew.bat test --tests edu.msrit.canteen.backend.features.commerce.CommerceIntegrationTest --no-daemon --max-workers=1
```

The frozen `DatabaseFoundationTest.migrationsAndSellerSeedsAreIdempotent` expects exactly `V001`–`V005`; it will conflict with the new commerce migrations when the entire foundation DB suite is enabled. Do not change that owner-owned test in this branch; the lead must update its assertion to check the core prefix. No credentials belong in this document or Git.
