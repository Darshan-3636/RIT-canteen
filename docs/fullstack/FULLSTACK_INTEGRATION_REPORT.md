# Full-stack local demo integration report

## Sources and scope

- Android parent: `be2bf40ba0bf8b42e013c478367093e8b09e6993` (`feat/rit-complete-frontend-experience`).
- Backend parent: `45181a11ebb81898af6a97347f92b3c5c89db363` (`integration/rit-backend-v1`).
- Two-parent merge checkpoint: `9f8543814e14785bdddf5211e0166acd21499b8c`. Both histories are ancestors. No source branch or `main` was changed.
- This integration changes Android source and `docs/fullstack/*` only. The backend's Kotlin, SQL, migrations and established contracts remain untouched. Private `backend/.env` is ignored.

## Actual environment

PostgreSQL 16 runs in an isolated local container with disposable, separately named app/test/fresh/legacy databases on loopback port 5436. Ktor is bound to `127.0.0.1:8080` (not deployed). The Moto G60 is connected via authorized ADB and `adb reverse tcp:8080 tcp:8080`. The debug APK uses that forwarded loopback URL; release has neither this client nor Internet/cleartext permission. Bearer storage uses Android Keystore AES-GCM, with `/auth/me` verification at login/restore. App prices, ownership, roles and capabilities are read from the server; bundled imagery remains keyed by item/seller ID.

## Results

| Verification | Actual result |
| --- | --- |
| Backend `clean build` with PostgreSQL | Passed: 32 tests, 0 failed, 0 skipped. |
| `verify-integration.sh --smoke-only` | Passed: health/readiness, registration, login/profile/logout/revocation, eight demo personas. |
| `verify-mounted-backend.sh` | Passed: 72 rows, role/tenant checks, quote flow, engagement/admin, unsupported-route honesty and restart persistence. |
| Android debug/release assemble, debug unit tests, debug/release lint | Final run passed: 147 tests, 0 failed/skipped; lint 0 errors (34 debug warnings, 30 release warnings). |
| Moto G60 connected suite | Final run passed: 69 tests, 0 failed/skipped. Includes server login/72-item catalog, individual `DEMO_SAVED` write/read, customer–seller quote through revision 2 to unpaid agreement, and seller availability edit observed by customer. |
| Manifest boundary | One `MAIN/LAUNCHER` in debug and release; debug alone has `INTERNET`; release has no local backend URL or Developer Testing Hub. |

The suite includes activity recreation with a verified session, and the backend mounted smoke suite proves persistence across a Ktor restart. The first targeted post-suite run was blocked when the Moto G60 entered its lock/AOD state; it was not bypassed. After the owner unlocked the phone, the targeted server-login/catalog test passed (1/1). Gradle's connected-test cleanup had removed the app, so the tested debug APK was reinstalled. Through the normal Login screen, the disposable customer account reached **Choose Seller**; after `am force-stop` and relaunch without clearing data, **Choose Seller** and the server-backed Main Canteen listing returned without another login. This separately verifies process-death session restoration on the device, not an offline or backend-restart scenario.

## DTO and trust-boundary examples

- `GET /sellers/{sellerId}/menu` supplies stable IDs, seller ownership, category ID, price paise, kind, dietary, availability and publication. Android maps category IDs to its 15 approved labels and IDs to bundled photos. Artwork does not authorize a stale price or an unavailable item.
- `GET /auth/me` supplies the user UUID, affiliation, seller membership and capabilities. An entered email or a locally selected role cannot grant seller/admin routes. The server still checks every operation.
- `POST /me/individual-demo-orders` returns `DEMO_SAVED` with `demoOnly=true`, `paymentVerified=false`; Android stores the returned ID/lines/paise as demo history, never as paid fulfillment.
- A seller item edit uses **both** catalog PATCH and, when availability changed, the separate availability PATCH. On the disposable database the phone changed `plain-dosa` to `UNAVAILABLE`; a subsequent live server GET returned `UNAVAILABLE`, and the customer UI reflected it.
- Bulk response `id`, `version`, `status`, `pickupAt`, `expiresAt`, `minimumUnits`, `subtotalPaise`, approvals and lines drives current presentation. The API exposes the **current revision**, not a complete revision history or customer identity to seller clients. Android must not fabricate history. `AGREED_AWAITING_PAYMENT` remains unpaid.

## Honest limitations and handoff

The connected P0 surface includes real login, catalog, profile, individual demo history, bulk request/version actions and seller menu/policy edits. Several P1 screens remain local fixture presentations even though backend routes exist: favorites, preferences/notifications, templates, support, analytics and admin. Seller employee/promotion/item creation and uploads have no matching backend write API. Payment and QR/pickup remain unavailable as backend operations. See `ANDROID_BACKEND_SCREEN_MATRIX.md` for screen-level status. Do not present synthetic analytics as verified revenue. Hours have a server write route but no read-back route; local hours editor is not proof of current stored hours.

Use `MOTO_G60_LOCAL_DEMO_RUNBOOK.md` for local setup and teacher-demo sequence. The final pushed commit SHA is reported in the task handoff; this file intentionally does not guess its own future SHA.
