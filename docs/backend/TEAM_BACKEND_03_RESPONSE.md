# Team Backend 03 response — Engagement/Admin

- **FOUNDATION_SHA:** `05e55583e38b942b58a208c78dedd0959a101e62` (verified: `git merge-base HEAD 05e5558…` = the same SHA)
- **Branch:** `feat/rit-backend-admin-services` (pushed to `origin`). The feature code commit is `4469e36`; this report is in the commit after it. The final HEAD SHA is in the handoff message.
- **API reference:** `docs/backend/ENGAGEMENT_ADMIN_API.md`

## Files changed (feature-owned only)

- `backend/src/main/kotlin/edu/msrit/canteen/backend/features/engagement/`: `EngagementRoutes.kt` (public seam), `CommerceReadPort.kt`, `EngagementDtos.kt`, `EngagementSupport.kt`, `CustomerDataRoutes.kt`, `SupportRoutes.kt`, `AnalyticsRoutes.kt`
- `backend/src/main/kotlin/edu/msrit/canteen/backend/features/platform/PlatformRoutes.kt` (internal `registerPlatformRoutes`)
- `backend/src/main/resources/db/migration/V040__customer_engagement.sql`, `V041__support_and_inbox.sql`, `V042__platform_admin.sql`
- `backend/src/test/kotlin/edu/msrit/canteen/backend/features/engagement/EngagementFeatureTest.kt`
- `docs/backend/ENGAGEMENT_ADMIN_API.md`, `docs/backend/TEAM_BACKEND_03_RESPONSE.md`

Not touched: `app/**`, root Gradle, `Application.kt`, `core/**`, `api/**`, `auth/**`, `V001`–`V005`, the foundation tests, Docker/compose, `README.md`.

## Public symbols

- `fun Route.registerEngagementRoutes(context: BackendContext)`: the frozen seam, using `UnavailableCommerceReadPort`.
- `fun Route.registerEngagementRoutes(context: BackendContext, commerce: CommerceReadPort)`: the integration overload.
- `interface CommerceReadPort`, `sealed interface ItemLookup`, `data class DemoRequestCounts`, `object UnavailableCommerceReadPort`.

## Test evidence (local, 27 Oct 2026)

Environment: macOS arm64, Temurin JDK 21, `postgres:16-alpine` via `backend/compose.yaml`, separate disposable DB `rit_canteen_test`.

| Command | Result |
| --- | --- |
| `./gradlew clean assemble` | success |
| `./gradlew test --no-daemon --max-workers=1` (with `RIT_TEST_DATABASE_*`) | 19 tests: **18 passed, 1 failed, 0 skipped** |
| `EngagementFeatureTest` (12 tests) | **12/12 passed** against real PostgreSQL |
| `BackendConfigTest` (4) | 4/4 passed |
| `DatabaseFoundationTest` (3) | 2 passed, **1 failed: the expected inherited conflict** (below) |
| `./gradlew run` + `curl /health`, `/ready` | `200 {"status":"ok"}`, `200 {"status":"ready"}`; Flyway history on app DB `001–005, 040, 041, 042` all success |

**Inherited assertion conflict (not a feature failure):** `DatabaseFoundationTest.migrationsAndSellerSeedsAreIdempotent` expects the applied list to be exactly `[001…005]` and now sees `[001…005, 040, 041, 042]`. As instructed, the frozen test was not edited. The lead should change it to assert the core prefix.

Feature tests cover:
- Student A and B isolation for preferences, favorites, templates, tickets and inbox. Another user's rows return `404`; foreign deletes do nothing.
- Personalization consent and promotional notifications default to off. Unknown body keys, such as a client `customerId`, are rejected with `400`.
- Item favorites fail closed with `503` without Commerce. With an injected port, `Found` succeeds and `Missing` returns `404`.
- Templates have no bulk minimum, reject duplicate names (`409`) and validate lines and seller.
- Support end to end, customer → seller → admin:
  - idempotent `clientRequestId` and `clientMessageId`
  - staff with only `bulk.view` can read but not reply
  - a seller from another seller account gets `403` or `404`
  - customer escalation; admin reply and status change; invalid transition `409`; closed ticket `409`
  - status history and author roles are correct
  - inbox rows are `IN_APP_ONLY`
- No payment field in the ticket API or support schema.
- Audit records reply, escalation and status change without copying the message body; setup-draft create and archive are audited.
- Every `/admin/**` GET and the draft POST return `403` for customer, owner and staff, and `401` without a token. The admin gets `403` on seller routes.
- The user directory exposes only minimal keys. Search treats `%` literally. Pagination and status input errors return `400`. Overview counts match the real `users` count.
- Analytics: revenue is always `null`, `UNVERIFIED_OR_INSUFFICIENT`, `NO_VERIFIED_PAYMENTS` and `INSUFFICIENT_DATA`. The dashboard reports `insufficientData: true` without a port; wired counts are labelled `DEMO_UNVERIFIED`. Export returns `501`; a `VERIFIED_REVENUE` metric is rejected; bad date ranges return `400`; missing capabilities return `403`.
- Revoking the manager's membership denies the manager and, through the supervisor chain, the staff member; the owner is unaffected. A revoked session returns `401` on reads and writes. Removing the admin flag returns `403`.
- Customer state persists across a fresh backend pool, simulating a restart.

## Not tested / not delivered

- **Live HTTP smoke of the engagement routes:** not possible until the lead mounts `registerEngagementRoutes` in `Application.kt`. The routes are exercised only through `testApplication`.
- **Seeded-persona flow (Phase B login → ticket → seller/admin reply):** blocked on Phase B login. It is covered by equivalent disposable sessions only.
- Commerce integration (a real `CommerceReadPort` adapter), Android wiring, and concurrent-retry race tests.
- Deliberately not implemented: push, SMS or email delivery; attachments; seller invitations; payouts; revenue; XLSX export; account suspension (a core `users` write).

## Lead integration actions

1. In `Application.kt`, inside `route("/api/v1")`, add `registerEngagementRoutes(context)`. After Backend 2 merges, use `registerEngagementRoutes(context, <CommerceReadPort adapter>)`.
2. Implement the adapter: `lookupItem` reads the Commerce catalogue; `demoRequestCounts` reads demo bulk requests in `Asia/Kolkata` dates.
3. Update `DatabaseFoundationTest` to assert the core migration prefix rather than the full list.
4. Optionally, add a Backend 1-owned admin endpoint for `users.status` changes and call `recordAudit` patterns for it.
5. Commerce should consider routing seller-facing bulk-request disputes to `support_tickets.seller_id` using `requestReference`.

## Deliverable status

The Engagement/Admin module is complete for standalone scope and is backed by PostgreSQL. It is ready for lead integration. It is **not** claimed as integrated with Commerce, Phase B auth, `Application.kt` or Android.
