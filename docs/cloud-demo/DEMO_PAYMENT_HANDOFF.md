# Demo payment simulation handoff

This checkpoint is code-only. The existing Render service and Supabase project were not changed. No real payment provider, charge, receipt, or verified sale exists.

## What the new build does

- A signed-in Student/Faculty customer saves a main-canteen prepared-food cart as one idempotent demo order, then selects **Simulate Payment** on its order-detail screen.
- Only `RIT_ENV=cloud_demo` accepts the payment simulation API. The order is kept in PostgreSQL with `demo_only=true`, `demo_payment_outcome=SUCCESS`, `status=DEMO_PAYMENT_SIMULATED`, and `payment_verified=false`.
- A retry with the same checkout idempotency key returns the same order. Retrying the same payment outcome returns its existing result, including after seller processing. The Android controls are disabled while a payment call is in flight.
- Customer history and the authorized seller's individual demo-order queue read the persisted order. Seller analytics separately reports the count and fictional total of successful simulated orders. `verifiedRevenuePaise` remains null. These counts use the order's creation date in Asia/Kolkata, not a gateway settlement date.
- Pending/failed simulation outcomes are excluded from simulated-sales totals. Existing synthetic sample orders remain unpaid until explicitly simulated.

## Activate only after owner approval

1. In the existing Render service, privately check the deployed branch/SHA and Auto-Deploy setting. Deploy the exact pushed `feat/rit-verify-complete-backend-integration` checkpoint only after approval; a branch change can trigger a deploy. Use the existing `backend/Dockerfile` service, not a second service.
2. Privately verify `RIT_ENV=cloud_demo`, `RIT_CLOUD_DEMO_SEED_ENABLED=true`, `HTTP_HOST=0.0.0.0`, and `RIT_CLOUD_DEMO_SELF_SIGNUP_ENABLED=true` if new Student/Faculty signup is desired. Keep `DEMO_SEED_ENABLED` false/absent. Do not change the existing private demo identity passwords to troubleshoot.
3. Privately verify Render's `DATABASE_URL` points to the intended Supabase Session Pooler JDBC endpoint with `sslmode=require`, with the exact `DATABASE_USER` and secret `DATABASE_PASSWORD`. Never copy these values into the APK or chat. The payment change adds **no migration**; Flyway V043–V046 must already be successful on the intended database.
4. Once Render shows the intended SHA and `/ready` is 200, install the cloud APK built for `https://rit-canteen-cloud-demo.onrender.com`. The APK alone cannot activate an API route that the hosted backend has not deployed.
5. On the phone, register/login as Student or Faculty, add main-canteen prepared food, review and save the demo order, then tap **Simulate Payment**. Reopen Orders to confirm the same server order ID and `DEMO_PAYMENT_SIMULATED` status. Sign in as the privately provisioned authorized seller to confirm the same ID in the demo queue and a separate simulated-sales count/fictional total in analytics. All of these acceptance actions write disposable demo data to live Supabase, so do them only after separate approval.

The automated end-to-end test uses a unique schema in disposable local PostgreSQL. It covers registration, login, cart, checkout retry, payment retry, database readback, customer history, seller visibility, and analytics without contacting Render or Supabase. The Moto G60 test covers the isolated Compose payment controls; it does not prove a live cloud transaction.
