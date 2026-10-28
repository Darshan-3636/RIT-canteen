# Android–backend integration checkpoint (demo lifecycle, not production completion)

## Scope and branch safety

This branch preserves the prior local integration checkpoint `f4fbd3e40427932b69fd26e6965f915cef602e54`. The codebase is an Android Kotlin/Compose app and a Kotlin/Ktor backend with PostgreSQL, not Node.js. Neither `main` nor the Render deployment branch was modified. New work adds an account-scoped **individual demo cart**, atomic/idempotent checkout, owner-approved opt-in cloud-demo customer signup without OTP, and a PostgreSQL-backed **simulated** payment/seller status lifecycle. It does **not** complete real payment, institutional identity approval or operational seller fulfillment. See [feature audit](FEATURE_BACKEND_AUDIT.md) for every persistent feature's status.

## Implemented in this checkpoint

- Flyway V043 adds a versioned cart per customer and line rows with seller/item foreign keys. Existing orders and migrations are preserved. It also adds nullable idempotency columns and a customer/key unique index to individual demo orders; old rows remain valid.
- Authenticated `GET/PUT /api/v1/me/cart/individual` uses a live session, version check, one database transaction and server-side catalog eligibility. The cart accepts one active Main Canteen's published/available prepared items; submitted client prices/totals are ignored. Reads reprice items from PostgreSQL and flag unavailable saved lines.
- `POST /api/v1/me/cart/individual/checkout` locks the cart, verifies a stable retry key and expected version, revalidates item availability and current prices, saves snapshot lines, and clears the cart atomically. A retry with the same key/version returns the same order ID. This produces `DEMO_SAVED`, `demoOnly:true`, `paymentVerified:false`—not a paid or kitchen-dispatched order.
- Flyway V044 adds demo status/outcome persistence and seller queue indexing while preserving the database constraint that `payment_verified` is always false. A customer's explicit success/pending/failure choice is recorded as a **simulation only**. Main Canteen seller capabilities gate the same order's demo accepted → ready → completed progression; invalid/duplicate and cross-account/seller transitions are rejected. No PhonePe or bank call occurs, and these routes are unavailable in production runtime.
- Cloud-demo registration may be explicitly enabled with `RIT_CLOUD_DEMO_SELF_SIGNUP_ENABLED=true`, per the owner's decision. It creates an immediately active, password-only Student/Faculty **customer** with self-declared affiliation and no seller/admin grant. The default remains disabled; production mode rejects the opt-in. Android cloud debug signup now presents this path. No OTP or affiliation verification is claimed.
- The debug Android customer flow fetches the cart after login and session restoration. Add, quantity change, removal and clear send versioned server replacements and update UI only after confirmation. The cart and review show server-priced totals. Failed changes do not receive “Added” feedback; the UI refreshes after a conflict. Review/checkout reuses a stable key across retries and disables the button while saving. The release variant retains no debug backend transport or mock authentication.
- The Android customer order detail shows the server ID, current demo status and a conspicuously fictional payment choice. The seller orders area opens a server-owned individual demo queue; only users with `bulk.respond` can advance eligible demo statuses. Customer history refreshes from the server after payment and on reentry. The old process-local checkout/QR flow remains excluded from cloud mode and is not used for this path.
- The guarded local HTTP smoke script now checks cart isolation, stale-version rejection, idempotent checkout and restart persistence. New PostgreSQL integration and Android unit/device tests exercise the same path.

Prior checkpoint functionality remains: server auth/profile, four-seller/72-item catalog, customer/seller bulk negotiation, seller existing-item edits, operating policy, customer engagement APIs and guarded cloud demo behavior. No local fixture or real-payment-success fallback was introduced into cloud mode.

## Actual verification

| Check | Result | Boundary |
|---|---|---|
| Backend clean compile/build and complete PostgreSQL suite | 43 tests passed, 0 failed, 0 skipped | Three isolated loopback PostgreSQL databases in a disposable container; no hosted data |
| Commerce integration tests | 8 tests passed, 0 skipped | Includes account isolation, seller/packaged exclusion, stale cart, atomic replay, simulated-payment transitions, seller scope and unchanged `payment_verified:false` |
| Android cloud-configured debug unit tests | 151 tests, 0 failures, 54 fixture-only skips | No live Render credentials |
| Android local-fixture unit tests | 151 tests, 0 failures, 3 cloud-only skips | Local configuration only |
| Cloud-configured debug/release APK build and lint | Both builds passed; lint 0 errors, 34 debug warnings, 30 release warnings | Build only; production auth not implemented |
| Guarded local auth/profile HTTP smoke | Passed signup, login, profile, logout/revocation and eight seeded demo personas | Disposable loopback app/test DBs |
| Guarded mounted-backend HTTP smoke | Passed 72-row catalog, cart/replay/restart, simulated payment, seller status transitions, roles/tenancy, bulk quote, engagement/admin and unsupported real-payment route | Disposable loopback app DB; server restart exercised |
| Moto G60 fullstack device tests | 3/3 passed: login/catalog, customer cart relogin/idempotent checkout, customer–seller bulk quote | USB-authorized Moto G60 via `adb reverse` to disposable local backend |
| Moto G60 new cross-role API test | 1/1 passed: customer cart checkout and simulated outcome, other-seller isolation, Main Canteen seller accepted/ready/completed, customer relogin/history | Same real local Ktor/PostgreSQL server and Android transport; no mocked responses |
| Moto G60 new Compose lifecycle UI tests | 2/2 passed: explicit simulated customer action and capability/state-gated seller control | Actual Compose interaction on device |
| Moto G60 food-details-to-cart Compose test | 1/1 passed | Actual UI interaction with local backend |
| Moto G60 complete instrumented suite | 73/73 passed, 0 skipped on final run | Local backend and disposable PostgreSQL; no hosted account |
| Hosted Render checks | Unverified; read-only `/health` timed out after 20 seconds with HTTP 000 | No deployment or hosted authenticated writes; timeout is not evidence of a service failure |

The local PostgreSQL container was restricted to `127.0.0.1`. Tests did not touch Supabase or Render beyond the read-only health attempt. An earlier fullstack run had one 25-second login-navigation timeout, then passed isolated and in the full suite. The first final 73-test rerun was interrupted because the Moto G60 entered its lock-screen/AOD state, causing “No compose hierarchies found” errors; after the owner unlocked it and USB-only stay-awake was temporarily enabled, the full suite passed 73/73. The original stay-awake value (`0`) was restored. No screenshot-based visual approval was claimed.

## Remaining blockers and design decisions

1. **Hosted deployment:** V043/V044 and the new Ktor routes are not on the last verified Render baseline. This branch must be reviewed before an owner-approved backend deployment. The prior operating-policy GET was 404 on the then-current deployment; it was not reverified here.
2. **Public signup:** The owner chose no OTP and automatic customer approval for this demo. Cloud signup is now an opt-in flag, but the hosted service has not been updated or tested here. Email and Student/Faculty status are unverified, and no rate limiting, password reset or institutional directory approval has been delivered. The release app still lacks production auth transport.
3. **Operational individual orders:** Seller and customer see the same PostgreSQL demo order and status, but this is **not** a kitchen request, stock reservation, contractual order or pickup promise. The seller transition is intentionally restricted to demonstration runtime. Real acceptance, hours/capacity and fulfillment policy still need design and implementation.
4. **Real payment/pickup:** The owner approved a dummy payment for the demo; the backend records a fictional outcome and always returns `paymentVerified:false`. There is no PhonePe Business integration, verified webhook, pay-at-counter reconciliation, financial ledger or authorized pickup/QR redemption. Bulk requests still stop at `AGREED_AWAITING_PAYMENT`.
5. **Other persistent UI:** Bulk draft cart remains local. Seller employee/promotion/category creation, seller analytics UI, admin UI, notification read-state and operational promotions remain incomplete or deliberately disabled in cloud mode. See the audit matrix rather than treating compile success as integration.
6. **Cross-device/hosted acceptance:** A local device relogin passed. A second physical device and hosted disposable accounts/credentials were unavailable, so live cross-device and hosted seller fulfillment were not tested.

## Next controlled step

Review V043/V044 and the Android/backend API contract in the feature-branch PR. After owner approval, deploy the backend migrations/routes first, enable self-signup only if its unverified-account risk is accepted, and run read-only health/route checks; then build/install the matching cloud debug APK and use disposable accounts for authorized hosted writes. Follow [deployment checklist](LIVE_DEPLOYMENT_CHECKLIST.md) and [phone acceptance](ANDROID_PHONE_ACCEPTANCE.md). Do not merge/deploy on the strength of local tests alone.
