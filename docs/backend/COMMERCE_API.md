# Commerce API — Backend 2

Originally implemented on `feat/rit-backend-orders-catalog` from foundation `05e55583e38b942b58a208c78dedd0959a101e62`; updated after integration on `feat/rit-verify-complete-backend-integration`.

This module provides the demonstration catalog, commerce request API and customer individual-cart API. Phase B authentication/session issuance is mounted separately; Android debug builds use these HTTP routes. Individual orders have a **demo-only simulated payment and seller status lifecycle**; neither is a verified payment, real kitchen fulfillment or pickup handover. `AGREED_AWAITING_PAYMENT` means only that customer and seller approved the same current bulk quote revision; it is not a paid or fulfilled order.

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
- The Android cloud client uses the server's `imageKey` to select bundled art; an unknown key has no bundled image.
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
| GET | `/sellers/{sellerId}/operating-policy` | Guest | Current seller minimum, special-pickup setting, timezone, weekly hours and future closures from PostgreSQL. Added on `feat/rit-verify-complete-backend-integration`; not present in the original deployed baseline. |
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

The operating-policy response uses `minimumUnits`, `specialPickupEnabled`, `timezone`, `hours: [{dayOfWeek,opensAt,closesAt}]` and `closures: [{localDate,reason}]`. Hours use ISO weekday 1–7; absent hours mean unverified rather than open. The Android cloud client treats a missing route as unverified and still submits only to the backend for authoritative scheduling validation.

## Bulk request lifecycle

### Customer-owned bulk draft (V045)

`GET /me/draft/bulk` reads only the current customer's saved draft. `PUT /me/draft/bulk`
replaces it with `{expectedVersion,sellerId,lines,pickupAt,specialPickup,specialPickupReason,instructions}`.
An absent draft reads as version 0. `lines:[]` and `sellerId:null` clear it. The draft may be
incomplete, but every saved line must belong to its single seller and have quantity 1–999.
The response includes current server prices and `available` flags; it is not a seller quote.
Each successful replacement increments the version. A stale version returns 409, and a
foreign-seller item returns 422. Only a live customer session may access the draft.
The Android cloud client reloads on session restoration and never labels a failed save as saved.

V046 preserves optional pickup instructions with a submitted bulk request. The initial
request body may include `instructions` (at most 500 characters); it is included in the
idempotency fingerprint and returned on customer/seller reads. Existing requests retain
an empty instruction string. Empty-instruction retries retain the pre-V046 fingerprint,
so requests submitted before this migration remain replayable. The Android cloud client
derives its account-scoped retry key from the saved draft version to survive process restarts.
No instruction implies seller acceptance or a pickup promise.

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

### Customer-owned individual cart (V043)

These routes require a valid customer bearer. They accept no client price or total. The server accepts only published, available, `PREPARED` items from the active Main Canteen, caps a draft at 100 distinct lines and each quantity at 1–999, and recalculates current prices on every read. A saved unavailable item remains visible with `available:false`, but checkout is disabled. A version mismatch returns HTTP 409 `STALE_CART`; clients must refresh before another edit. `PUT` is a complete replacement, so `lines:[]` clears the cart.

| Method | Path | Body / response | Result |
|---|---|---|---|
| GET | `/me/cart/individual` | Response `IndividualCartDto` | Fetch the authenticated customer's current cart; absent cart is version 0 with empty lines. |
| PUT | `/me/cart/individual` | `{"expectedVersion":0,"lines":[{"itemId":"masala-dosa","quantity":2}]}` → `IndividualCartDto` | Atomically replace lines and increment version after server validation. |
| POST | `/me/cart/individual/checkout` | `Idempotency-Key: <opaque 1–128-character key>` and `{"expectedVersion":1}` → `IndividualDemoOrderDto` (201) | Under one DB transaction: lock cart, revalidate live items/prices, snapshot order lines, clear cart, increment version. Same key+version replays the same order ID; reused key/different version or stale cart returns 409. |

`IndividualCartDto` has `version`, `sellerId` (null when empty), `subtotalPaise`, `readyForCheckout`, and `lines:[{itemId,itemName,quantity,unitPricePaise,available}]`. This is **not** a live paid order. Checkout returns `status:DEMO_SAVED`, `demoOnly:true`, `paymentVerified:false`; it never reports a charge, reservation, kitchen acceptance or pickup. The legacy line-based POST below remains for compatibility but the Android customer UI now uses cart checkout.

| Method | Path | Auth | Behavior |
|---|---|---|---|
| POST | `/me/individual-demo-orders` | Bearer customer | Persist multiple Main Canteen `PREPARED` items at current server prices; quantities start at one, no bulk minimum. |
| GET | `/me/individual-demo-orders` | Bearer owner | Customer-specific demo history. |
| POST | `/me/individual-demo-orders/{orderId}/simulate-payment` | Bearer order owner | `{ "outcome":"SUCCESS" }`, `FAILURE` or `PENDING`; returns updated order. Only local/cloud-demo runtime, not production. Success means simulated only; `paymentVerified:false` always. Other-customer IDs return 404. |
| GET | `/sellers/{sellerId}/individual-demo-orders` | Seller `bulk.view` | Seller-scoped demo queue. A different seller's orders never appear. |
| POST | `/sellers/{sellerId}/individual-demo-orders/{orderId}/demo-status` | Seller `bulk.respond` | `{ "status":"DEMO_ACCEPTED" }`, `DEMO_REJECTED`, `DEMO_READY` or `DEMO_COMPLETED`. Only allowed in sequence after a simulated SUCCESS. Other-seller IDs return 404; invalid/duplicate transitions return 409. Local/cloud-demo runtime only. |

Example body: `{ "lines": [{"itemId":"masala-dosa","quantity":1},{"itemId":"idli","quantity":2}] }`. The response includes `status: "DEMO_SAVED"`, `demoOnly: true`, and `paymentVerified: false`. Packaged products and secondary sellers cannot use this endpoint. It is not an operational order, kitchen acceptance, payment, delivery or pickup promise.

The demo lifecycle is `DEMO_SAVED → DEMO_PAYMENT_PENDING/FAILED → DEMO_PAYMENT_SIMULATED → DEMO_ACCEPTED → DEMO_READY → DEMO_COMPLETED`; a seller may reject at the simulated-payment stage. Failed/pending payment can be retried, but a success or seller transition cannot be replayed as a new transition. The server enforces the account and seller scope in PostgreSQL transactions. The display is deliberately marked “demo”; these statuses must not be interpreted as a real payment, kitchen dispatch, pickup code or order-completion proof. The `demo_payment_outcome` field is independent of the permanently false `payment_verified` column.

## Owned schema and fixtures

- `V020__commerce_catalog.sql`: categories, per-seller category eligibility, menu, seller policies, known weekly hours and local closures.
- `V021__bulk_requests.sql`: requests, immutable quote revisions/lines, version-scoped approvals and special-pickup decisions, audit events, idempotency.
- `V022__individual_demo_orders.sql`: customer-owned individual demo history and price-snapshotted lines.
- `V043__individual_customer_cart.sql`: one versioned individual cart per user, seller-scoped line foreign keys, and unique customer/idempotency-key index on demo orders. `individual_customer_carts.customer_user_id` references `users(id)` with cascade deletion; lines reference both cart and `(seller_id,item_id)` menu identity. Existing demo orders remain intact; the new idempotency columns are nullable for legacy rows.
- `V044__individual_demo_lifecycle.sql`: expands only the individual demo status check, records the simulated payment outcome and update timestamp, and indexes seller-scoped queue reads. `demo_only` remains true and `payment_verified` remains constrained false.

Individual demo data relationships and guards:

| Table | Ownership / relationship | Critical constraints |
|---|---|---|
| `individual_customer_carts` | One row per `users.id` (`customer_user_id` PK); cascades only with user deletion | Nonnegative `version`; PUT and checkout lock this row, compare `expectedVersion`, then increment. |
| `individual_customer_cart_lines` | `(customer_user_id,item_id)` PK; cart cascade FK and `(seller_id,item_id)` menu FK | Quantity 1–999; server restricts eligible items to one active Main Canteen's available prepared food. |
| `individual_demo_orders` | UUID PK, `customer_user_id → users.id`, `seller_id → sellers.id` | Nonnegative snapshotted subtotal; `demo_only=true`, `payment_verified=false` enforced by checks; status/outcome allowlists; unique `(customer_user_id,idempotency_key)` for non-null keys. |
| `individual_demo_order_lines` | `(order_id,item_id)` PK; order FK and `(seller_id,item_id)` menu FK | Immutable name/price/quantity snapshots from checkout; quantity 1–999, nonnegative unit price. |

The V044 seller index is `(seller_id,created_at DESC)`. A cart checkout transaction writes the order and its lines, clears the cart and increments its version; the retry key resolves to the existing order after restart. Only the owning customer can change a payment simulation, and only a seller with live `bulk.respond` capability on that order's seller can advance its demo status. Neither client-supplied prices nor client-supplied payment verification are accepted.
- Demo seed categories: `dosa`, `rice`, `snacks`, `drinks`, `packs`.
- Demo seller IDs: `main-canteen`, `north-counter`, `coffee-corner`, `campus-pantry`.
- The current local fixture is the 72-item manifest (`backend/src/main/resources/demo/catalog-items-v2.tsv`), 18 items per seller. Sample IDs/prices (paise): `masala-dosa` 5000, `veg-biryani` 8500, `filter-coffee` 3000, `idli` 3500, `lemon-rice` 4500 (seeded unavailable but visible), `water-bottle` 2000, `north-lunch` 9000, `north-snack` 6500, `corner-coffee` 3500, `corner-biscuits` 2500, `pantry-juice` 3000, `pantry-water` 2000. See `DEMO_CATALOG_PARITY.md` for full parity and mutation-preserving seed rules.
- The Commerce catalog fixture does not create user or membership records; the separate local-auth bootstrap creates eight demo identities only when `RIT_ENV=local` and `DEMO_SEED_ENABLED=true`. Integration tests create random users/sessions in the separate test DB and store only SHA-256 bearer-token hashes.

## Local verification

From `backend/`, use the local `.env` instructions in `README.md`. For PostgreSQL tests set `RIT_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/rit_canteen_test`, `RIT_TEST_DATABASE_USER`, and `RIT_TEST_DATABASE_PASSWORD`. Example commands:

```powershell
.\gradlew.bat clean assemble --no-daemon --max-workers=1
.\gradlew.bat test --tests edu.msrit.canteen.backend.features.commerce.CommerceIntegrationTest --no-daemon --max-workers=1
```

The integrated test suite validates the immutable core migration prefix and the current migration list through V044 in disposable databases. No credentials belong in this document or Git.
