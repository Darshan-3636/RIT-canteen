# Owner-controlled deployment checklist

Do **not** deploy this verification branch automatically. It adds the public read-only `GET /api/v1/sellers/{sellerId}/operating-policy` route. The baseline Render service may return 404 until the owner reviews and deploys a compatible backend commit. The Android app degrades to “hours unverified” and relies on server validation; no local hours are substituted.

1. Review the branch diff and its isolated PostgreSQL test report. Confirm only the intended backend DTO/route/test changes and Android debug integrations are present.
2. Choose an approved merge/deployment sequence. Preserve the existing Render-bound branch until approved; merge backend to the deploy target first, then verify `/health`, `/ready`, `/api/v1/sellers`, and one seller's `/operating-policy` with read-only GETs.
3. Confirm the response contains the configured `minimumUnits`, `specialPickupEnabled`, timezone, actual hours and closures. A 404 means the new route is **not** deployed; do not present pickup policy as verified.
4. Build the cloud debug APK from the reviewed commit with `-PritCloudDemo=true -PritApiBaseUrl=https://rit-canteen-cloud-demo.onrender.com`. Verify the HTTPS origin and no local host, known passwords or debug hub are present in the APK.
5. On an owner-controlled test account only, verify login, seller catalog, customer read endpoints, then separately authorize disposable test writes before attempting orders, favorites, support or seller edits on hosted infrastructure. Never use production accounts as test fixtures.
6. Check server logs for authorization failures and the database migration version. Never run destructive integration tests against Supabase. Keep admin creation and seller-owner provisioning private and owner controlled.
7. Keep payment, QR redemption and fulfillment disabled until the provider/business workflow has separate approval, server persistence, authorization, replay protection and tests.

Rollback: retain the previously deployed immutable commit and its APK. Roll back the application revision through the owner's normal Render process if the new route or response breaks; do **not** reset/migrate the private database from these instructions.
