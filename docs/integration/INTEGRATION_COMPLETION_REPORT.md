# Verification-branch integration report

## Scope and baseline

Worktree branch: `feat/rit-verify-complete-backend-integration`, based exactly on `21d57494acb3eacc7d297dfed64666e9ca622cf8`. The existing Render-bound branch and `main` were not modified or pushed. Public Render checks were read-only; no hosted account/order/database mutation was performed. The branch commit SHA is the commit containing this report (see `git rev-parse HEAD` after checkout).

## Existing functionality confirmed in source and isolated tests

The baseline already has server authentication/session restoration, `/me/profile`, four-seller/72-item catalog, individual demo-order creation/history, customer and seller bulk request/history/negotiation/approval, seller existing-item edits and availability, seller hours/minimum/closure writes, and backend engagement/admin routes. Android holds its bearer encrypted in Android Keystore-backed storage. Backend authorization and ownership are enforced by membership/role and user-scoped queries, not Android role selection. The local PostgreSQL suite exercises auth, migration, catalog parity, Commerce, Engagement and admin security. A public GET to Render `/health`, `/ready` and `/api/v1/sellers` returned 200; this is **not** proof of authenticated live flows.

## Changes in this branch

- Connected customer favorites to `/me/favorites` GET/PUT/DELETE, using the active bearer, server-confirmed snapshots and no cloud SharedPreferences fallback. Sign-out clears in-memory account-scoped state.
- Connected customer preferences, notification list, bulk templates and support tickets to `/me/preferences`, `/me/notifications`, `/me/bulk-templates` and `/me/support/tickets` routes. Ticket creation/replies carry persisted client-generated idempotency IDs. UI mutates visible state only after server confirmation; read failures are surfaced. These paths compile and their backend routes are integration-tested, but **phone/live-account end-to-end acceptance remains pending**.
- Added a public read-only `/api/v1/sellers/{sellerId}/operating-policy` response from the existing policy/hours/closures tables, with an isolated PostgreSQL integration test. Android fetches it when available and no longer shows fictional cloud operating hours when unavailable. This endpoint is **not yet deployed** on the verified Render baseline; existing backend submission remains authoritative.
- Disabled cloud-mode local payment/QR/pickup success simulation, synthetic sales/admin fixtures, fixture employees, fixture promotions and sample quote projection. The cloud checkout route shows an unavailable state. Local-fixture mode retains its preview tests.
- Qualified the official base-image names in the backend Dockerfile for non-interactive Podman builds; no runtime startup/migration behavior changed.

## Deliberately incomplete and owner-gated

Seller analytics/reports/support and platform-admin Android pages still do not project the available backend routes. Seller employee/promotion/category creation has no complete mounted API, and cloud local mutation is disabled. Backend admin tests use isolated identities only; there is no live admin provisioned. Public registration is disabled pending an approved identity lifecycle. Promotions are illustration-only and hidden in cloud mode. Individual orders are explicitly demo/unpaid. Bulk orders stop at `AGREED_AWAITING_PAYMENT`; no verified payment, QR redemption or kitchen fulfillment exists. No provider credential or payment bypass was added.

## Verification evidence

- Backend `clean build` on **three disposable localhost PostgreSQL databases**: 38 tests, 0 failures, 0 skips. The new operating-policy test reads a changed minimum, special-pickup flag, hours and closure and checks unknown-seller 404. Container was stopped afterward.
- Android local-fixture `:app:testDebugUnitTest`: 150 tests, 0 failures, 3 skips (cloud-only boundary cases).
- Android cloud-debug `:app:testDebugUnitTest`: 150 tests, 0 failures, 54 skips (local-fixture-only cases); cloud boundary cases passed. See Gradle XML reports for case-level evidence.
- Android cloud `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug`, `:app:lintRelease`: **BUILD SUCCESSFUL**. Lint: 0 errors, 34 debug warnings and 30 release warnings. Release has no mock-auth transport.
- Backend Dockerfile build with non-interactive Podman: **succeeded**, local image `localhost/rit-canteen-verify-backend:local` (`be4da51751f446f085561254dca261c09eb0c81c79c937f2f5a189723c8b5a9c`). No deployment was invoked.
- Public live read-only GET: `/health` 200, `/ready` 200, `/api/v1/sellers` 200, `/api/v1/sellers/main-canteen/operating-policy` **404** (new route not deployed).
- Moto G60: **not tested in this pass**. `adb devices -l` showed no attached device. UI visual, force-stop and authenticated live checks remain pending.

## APK and compatibility

Cloud debug artifact: `app/build/outputs/apk/debug/app-debug.apk` (generated locally; not committed), **23,032,159 bytes**, SHA-256 `185155f66612f8bc3ff3809afc7173cefb8121e270cca127d522785bb0c3c34c`. BuildConfig contains `https://rit-canteen-cloud-demo.onrender.com` and `RIT_CLOUD_DEMO=true`; no localhost URL in cloud mode. The new policy GET is optional to app startup and returns an unverified-hours UI if absent, but the complete policy display and its acceptance test require the new backend commit to be deployed. Do not present this APK as fully accepted on a physical phone until the owner performs `ANDROID_PHONE_ACCEPTANCE.md`.

## Security and next merge action

No live service writes, private Supabase/Render settings, credentials or tokens were accessed. Mock local success paths are blocked in cloud mode; server bearer and permissions remain authoritative. Review the diff and new route, deploy the backend only with owner approval, confirm its read-only policy endpoint, then build/install the matching APK and perform the short phone checklist. Do not merge this branch into the Render-bound branch or `main` before approval. See `LIVE_DEPLOYMENT_CHECKLIST.md` for exact order.
