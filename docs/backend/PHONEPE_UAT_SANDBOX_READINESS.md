# PhonePe UAT sandbox readiness (not production payments)

This branch adds an opt-in, **cloud_demo-only** PhonePe UAT path. It has not been deployed or applied to live Supabase. The existing click-through payment simulation remains distinct. Neither path sets `paymentVerified=true`, creates verified revenue, charges real money, or authorizes seller settlement.

## Boundary and flow

1. A signed-in Student/Faculty saves an individual demo order or reaches a fully approved, current bulk quote. The server loads its owner, seller, version and price from PostgreSQL; the Android amount is never trusted.
2. `POST /api/v1/me/sandbox-payments/{INDIVIDUAL|BULK}/{sourceId}` requires an idempotency key. V047 stores one attempt and one merchant order ID; a second in-flight or successful attempt for the same order is rejected.
3. The backend authenticates with **PhonePe pre-production only**, creates the SDK order, and returns the provider order ID and short-lived SDK token to that customer. Merchant client credentials remain server-side.
4. The **debug cloud-demo** APK initializes PhonePe SDK 5.4.0 in `SANDBOX` mode. Its activity result is only a prompt to call our server's `/reconcile` endpoint. A server-to-PhonePe status response, with a matching server-snapshotted amount, is required before `SANDBOX_SUCCESS` is stored.
5. The customer can reopen the order/request and check status after app restart. Seller-scoped sandbox attempts and analytics are read from PostgreSQL, separated from fictional click-through totals and verified revenue.

Provider failures and ambiguous outcomes remain pending or unavailable; they are not converted to success. A failed provider state can be retried with a new key. No production PhonePe host or release-build SDK path is included.

## Activation dependencies — do not guess or commit credentials

PhonePe's OAuth contract requires **Merchant ID, Client ID, Client Secret, and Client Version**. The owner currently has only the first three. The documentation's sample `client_version=1` is an example, **not a verified value for this merchant**. Request the actual UAT Client Version from PhonePe Business/Integration Support before enabling. Do not paste any secret into chat or source control.

After separate owner authorization to deploy and migrate:

1. Review the branch diff, tests and V047 migration. Apply V047 through the normal backend Flyway startup only after approval. This adds a table/indexes without resetting existing data.
2. In Render's **private** environment, verify `RIT_ENV=cloud_demo`, Supabase's server-side `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and customer signup policy. Set `PHONEPE_UAT_MERCHANT_ID`, `PHONEPE_UAT_CLIENT_ID`, `PHONEPE_UAT_CLIENT_VERSION`, and `PHONEPE_UAT_CLIENT_SECRET` privately. Never place these values in Android build properties.
3. Deploy the tested commit and verify `/health` and `/ready`. Install the new cloud debug APK. For simulator scenarios, follow PhonePe's official UAT Test App instructions; do not use a real payment app/account for test transactions.
4. On the phone with **no USB forwarding and no local server**, sign in as a Student/Faculty, save a small individual order, start PhonePe UAT, try success/failure/pending in the simulator, return to the app, and check the backend-confirmed status. Reopen customer history and authorized seller view; verify `paymentVerified=false` and distinct sandbox analytics in PostgreSQL/API.
5. Inspect logs only for sanitized error codes. Never log access tokens, SDK tokens, client secret, provider response bodies, or database credentials.

The PhonePe UAT end-to-end device test is **blocked until the merchant's actual Client Version is available and this branch is authorized for deployment**. The test app is installed. A fake-gateway test against disposable PostgreSQL covers server price, idempotency, pending/failure/success reconciliation, owner isolation, seller visibility, and unverified analytics, but does not prove PhonePe's live UAT behavior.

## Verification on 28 October 2026

- Disposable local PostgreSQL: backend build and 48 tests passed (zero skipped/failures), including V047 migration, registration/login, cart, order persistence, seller visibility, UAT retry and reconciliation, analytics separation, and environment gating.
- Android cloud debug and release variants built against `https://rit-canteen-cloud-demo.onrender.com`; 153 unit cases reported, 54 intentionally skipped in this build, zero failures; lint completed with no errors.
- Moto G60: four focused Compose UI tests passed, including distinct UAT controls; the latest cloud debug APK was installed and launched to Welcome. The official PhonePe simulator package `com.phonepe.simulator` is installed.
- Not tested: actual PhonePe UAT token creation, SDK handoff, simulator success/failure/pending, live Render/Supabase persistence. The owner reports the simulator is installed; the **Client Version and deployment permission** remain outstanding. No live migration, Render configuration, or Supabase write was performed.

Official references: [Android SDK setup](https://developer.phonepe.com/payment-gateway/mobile-app-integration/standard-checkout-mobile/android-sdk/sdk-setup), [OAuth authorization](https://developer.phonepe.com/payment-gateway/mobile-app-integration/standard-checkout-mobile/api-reference/authorization), [Create SDK order](https://developer.phonepe.com/payment-gateway/mobile-app-integration/standard-checkout-mobile/api-reference/create-order-token), [Check order status](https://developer.phonepe.com/payment-gateway/mobile-app-integration/standard-checkout-mobile/api-reference/check-order-status), [UAT sandbox](https://developer.phonepe.com/payment-gateway/uat-testing-go-live/uat-sandbox).
