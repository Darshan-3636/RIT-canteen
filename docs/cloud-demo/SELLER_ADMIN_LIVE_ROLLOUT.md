# Seller and admin cloud rollout handoff

**Prepared 2026-10-31; not deployed.** The management and Android work is based on integrated commit `322ca29d510c8daa6e2a04ea2a11680bfc5d4056`. Review the feature-branch commit before deployment. Do not include private `.env`, `local.properties`, `.android-local/`, test databases, or credentials.

## Current evidence

- The Android cloud debug APK builds with `-PritCloudDemo=true -PritApiBaseUrl=https://rit-canteen-cloud-demo.onrender.com`. Its generated configuration contains the HTTPS API origin and no PostgreSQL credentials. It installed and launched on device `2312DRAABI`.
- Read-only hosted requests returned 200 for `/health`, `/ready`, `/api/v1/sellers`, and the main canteen operating policy. The new private seller catalog, seller profile, and admin dashboard routes returned 404. The hosted service is therefore behind the local management API. Readiness confirms a database connection; it does not independently prove which Supabase project or Flyway version is live.
- Local V048 migration and management API tests passed: 15 management integration tests on disposable PostgreSQL 18.6, including a V047 upgrade preserving identities, orders, and sandbox payment attempts. Android cloud build, 153 unit tests (54 skipped by cloud-mode guards), and lint passed. No hosted write or live migration was run.

## Change requiring approval

`backend/src/main/resources/db/migration/V048__seller_and_admin_management.sql` is the next local migration after V047. SHA-256: `D277D1366F3C855278B16032780B057735E056E9411C2CEDA68BECD80D4BD529`. It adds versions and archive/review metadata to existing seller, catalog, membership, policy, user, and setup-draft tables; adds an audit index and membership trigger; and replaces the favorites target CHECK with a wider item-ID rule. It does not drop tables or delete rows. The favorites constraint replacement still requires live metadata review before rollout. The backend deployment must contain this exact V048 plus the management routes; Flyway runs on backend startup. Preserve V001–V047 and the PhonePe integration.

## Approval gates and rollout order

1. Privately verify the intended Supabase project and read-only `flyway_schema_history`: latest successful migration V047, no failed or newer migration, and no drift in the V048 target tables/constraints. Live metadata remains unverified in this workspace. Do not share database passwords or secret keys in chat.
2. Review the feature-branch source and V048 SQL, then approve Render deployment explicitly. Confirm Render's linked branch, deployed SHA, and auto-deploy behavior before deployment. Publishing the review branch does not authorize a live migration or deployment.
3. Deploy the approved backend commit to Render, allowing Flyway to apply V048 once. Confirm the deployed SHA and successful Flyway V048 in private logs/metadata. If migration fails, stop; do not repair or reset live tables automatically.
4. Use read-only checks for `/health`, `/ready`, public catalog, and the new private endpoints (unauthenticated requests should be denied rather than 404). Test authenticated seller/admin mutations only with separately approved disposable hosted accounts.
5. Build/install the Android cloud APK from the same approved source and the Render HTTPS origin. The phone calls Ktor on Render; only Ktor connects to Supabase PostgreSQL. No Supabase database URL or credentials belong in the APK.

The current APK is `app/build/outputs/apk/debug/app-debug.apk` in this workspace. It can browse the deployed public API, but seller/admin management cannot work against the current older Render deployment.
