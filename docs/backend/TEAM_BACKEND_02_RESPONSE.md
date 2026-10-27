# TEAM_BACKEND_02_RESPONSE — Commerce feature contribution

## Identity and base

- Repository: https://github.com/Darshan-3636/RIT-canteen
- Foundation SHA: `05e55583e38b942b58a208c78dedd0959a101e62`
- Feature branch: `feat/rit-backend-orders-catalog`
- Starting `HEAD` and `git merge-base HEAD FOUNDATION_SHA` both verified as `05e55583e38b942b58a208c78dedd0959a101e62`.
- Work was done in an isolated Git worktree under `.worktrees/rit-backend-2`; the TEAM_01 checkout was not changed.

## Implemented within owned module

- Added commerce-owned PostgreSQL migrations `V020__commerce_catalog.sql`, `V021__bulk_requests.sql`, and `V022__individual_demo_orders.sql`; no migration before V020 was edited.
- Added `edu.msrit.canteen.backend.features.commerce.registerCommerceRoutes(context)` with guest seller directory/scoped catalog reads, guarded catalog availability/content edits, seller bulk policy/hour/closure edits, customer-scoped individual demo history, and customer/seller bulk request routes.
- Added `seedCommerceFixtures(context)`, called only after foundation bootstrap, restricted to local/test runtime configuration, repeatable with stable seller/item IDs, and refusing production execution. It does not seed users or memberships.
- Implemented one-seller/minimum validation, current server-side price snapshots in paise, pickup validation in `Asia/Kolkata`, unknown-hours rejection, special pickup reason and separate seller decision, expected-version locking, append-only quote terms, revision-bound approvals, expiry, withdrawal, and customer-scoped records. Customer and seller approvals run against live sessions/permissions and must apply to the same current revision. Totals are overflow-checked. No `PAID_CONFIRMED` state or payment endpoint exists.
- Seed fixture IDs match the four foundation sellers and twelve Android catalog items. Local asset keys are stable item IDs, not remote/verified photo URLs.
- Wrote endpoint/schema/state examples, test commands, seed IDs, and integration seam in [COMMERCE_API.md](COMMERCE_API.md).

## Exact changed files

| File | Change |
|---|---|
| `backend/src/main/kotlin/edu/msrit/canteen/backend/features/commerce/CommerceDtos.kt` | Commerce request/response DTOs. |
| `backend/src/main/kotlin/edu/msrit/canteen/backend/features/commerce/CommerceSeed.kt` | Local-only idempotent catalog fixture seed. |
| `backend/src/main/kotlin/edu/msrit/canteen/backend/features/commerce/CommerceCatalog.kt` | Seller and seller-scoped catalog queries/DTO mapping. |
| `backend/src/main/kotlin/edu/msrit/canteen/backend/features/commerce/CommerceOperations.kt` | Pricing, bulk state transitions, authorization checks and individual demo history. |
| `backend/src/main/kotlin/edu/msrit/canteen/backend/features/commerce/CommerceRoutes.kt` | Isolated commerce route module; not mounted by foundation `Application.kt`. |
| `backend/src/main/resources/db/migration/V020__commerce_catalog.sql` | Commerce catalog and seller pickup-policy schema. |
| `backend/src/main/resources/db/migration/V021__bulk_requests.sql` | Bulk requests, quote revisions, approvals, special pickup decisions, and events. |
| `backend/src/main/resources/db/migration/V022__individual_demo_orders.sql` | Customer-owned individual demo history. |
| `backend/src/test/kotlin/edu/msrit/canteen/backend/features/commerce/CommerceIntegrationTest.kt` | Real PostgreSQL/Ktor HTTP integration tests using disposable users and hashed bearer sessions. |
| `docs/backend/COMMERCE_API.md` | API, schema, state and integration documentation. |
| `docs/backend/TEAM_BACKEND_02_RESPONSE.md` | This handoff and verification report. |

No Android `app/`, root Gradle, `Application.kt`, core auth/DTO/transaction code, V001–V019, or Backend 3 file was edited. The local `backend/.env` is ignored and contains only a randomly generated developer-local password; it is not part of Git changes.

## Verification actually performed

| Command/check | Result |
|---|---|
| Foundation SHA / isolated branch check | PASS — exact pinned base; branch `feat/rit-backend-orders-catalog`. |
| Docker/PostgreSQL setup | PASS — PostgreSQL 16 container healthy under dedicated Compose project `rit-backend-2`; a separate `rit_canteen_test` database was created. |
| `backend/gradlew.bat clean assemble --no-daemon --max-workers=1` | PASS — ran with workspace JDK 21. |
| `backend/gradlew.bat test --tests edu.msrit.canteen.backend.features.commerce.CommerceIntegrationTest --no-daemon --max-workers=1` with `RIT_TEST_DATABASE_*` set to the disposable PostgreSQL DB | PASS — 4 tests, 0 failures, 0 errors, 0 skipped. Exercises migrations/fixture seed, 4 sellers/12 items and seller-scoped search, individual prepared-item history/no payment, idempotent bulk creation and changed-payload conflict, cross-seller item rejection, hours/minimum/policy/special pickup, stale and expired revision rejection, hidden unavailable-item reads, concurrent mutual approvals, and `AGREED_AWAITING_PAYMENT`. |
| Full `backend/gradlew.bat test --no-daemon --max-workers=1` with the real disposable PostgreSQL DB | PARTIAL — 11 tests total; 10 passed and 1 failed. The sole failure is inherited `DatabaseFoundationTest.migrationsAndSellerSeedsAreIdempotent()` at `DatabaseFoundationTest.kt:45`: its frozen assertion expects the complete migration list to equal V001–V005, while this feature correctly applies V020–V022. Other foundation tests, including health/readiness behavior, passed. |
| `git diff --check` | PASS — checked before commit. |

The handoff explicitly assigns the frozen migration-count assertion update to the project lead. It was not edited here. No test result is inferred from a skipped/no-database run.

## Lead integration required

1. Import `edu.msrit.canteen.backend.features.commerce.registerCommerceRoutes` and mount it within the existing `route("/api/v1")` in owner-owned `Application.kt` after merge. This branch intentionally does not register production routes.
2. For local demo catalog only, call `seedCommerceFixtures(context)` after `BackendBootstrap.open(config)` has inserted the four seller rows and only when local demo seeding is enabled. Production must remain unseeded.
3. Update the frozen foundation migration test to assert the V001–V005 core migration prefix instead of equality to the entire migration list, then run the complete merged suite.
4. Backend Phase B owns successful login/session issuance and production demo personas. This module deliberately uses only test-created users and hashed opaque sessions; it does not create runtime identities or bypass authorization.
5. Android integration, operational individual-food acceptance/kitchen/hand-over, and verified payments remain separate owner decisions and are not claimed here.

## Deferred or unavailable behavior

- Routes are not yet reachable from the foundation production server until owner registration is integrated.
- `AGREED_AWAITING_PAYMENT` is not paid, reserved, accepted, prepared, ready, delivered or handed over. Pickup schedule returns no confirmed slots because there is no verified payment or operational confirmation.
- The 24-hour quote expiry is an explicit initial implementation default and should be confirmed by the owner.
- Seller hours are unknown until configured. The backend does not invent normal business hours or open status.
- No OTP, real login, external provider, payment, notification, promotion redemption, image hosting, kitchen workflow, or Android client integration is implemented by this contribution.

## Submission

- API document: `docs/backend/COMMERCE_API.md`.
- Feature branch target: `origin/feat/rit-backend-orders-catalog`.
- Commit and push are made only to the feature branch; no merge or main-branch write is performed.
