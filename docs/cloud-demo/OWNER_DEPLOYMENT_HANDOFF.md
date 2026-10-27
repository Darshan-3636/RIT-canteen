# Owner deployment handoff — resume here

## Verified setup status at handoff

- Supabase Free project `rit-canteen` already exists in the `RIT Canteen` organization. The owner privately saved the database password and the **Connect → Direct or ORM → Session pooler → URI** string. The exact region sublocation, pooler host, port, and username must be read from that dashboard.
- Render account exists and GitHub repository access is connected. Repository: `Darshan-3636/RIT-canteen`.
- No Render web service has been created; **there is no public API URL yet**.
- The developer has not signed into either provider, entered credentials, deployed, or run an independent phone test.

## Step 1 — Create the free Render service

1. Open Render Dashboard → **New** → **Web Service** → choose `Darshan-3636/RIT-canteen`.
2. Set branch `feat/rit-free-cloud-fullstack-demo`.
3. Choose Docker runtime, root directory `backend`, Dockerfile path `./Dockerfile`.
4. Choose **Free** compute only. No disk, upgrade, paid IPv4, custom domain, or other paid feature. Choose a region near the existing Supabase project only if a clear matching option is offered.
5. Before creation, open Render **Billing** and verify the workspace cannot incur charges. Render currently bills outbound bandwidth above the included allowance when a payment method is linked; database traffic to Supabase counts as outbound. Its documented spend limit is for pipeline minutes, not a bandwidth cap. For strict ₹0, proceed only if over-limit behavior will suspend services rather than charge (for example, no payment method is attached). Otherwise stop before creating the service. [Render bandwidth limits](https://render.com/docs/outbound-bandwidth)
6. Create the service after verifying the summary shows Free and the zero-charge condition is satisfied.

Render documents the free web service at 512 MB RAM / 0.1 CPU with spin-down and no persistent disk; these limits can change. Re-check the current dashboard and [Render Free plan documentation](https://render.com/docs/free). If any step requires payment, stop rather than accepting a charge.

## Step 2 — Enter private environment fields in Render

In service **Environment**, add exactly these names. Secret values are entered by the owner directly into Render and must never be sent to a later assistant.

| Name | Public/non-secret setting or private source |
|---|---|
| `RIT_ENV` | `cloud_demo` |
| `HTTP_HOST` | `0.0.0.0` |
| `PORT` | Render-managed; leave unset |
| `DATABASE_URL` | `jdbc:postgresql://<pooler-host-from-Supabase>:<pooler-port>/postgres?sslmode=require` |
| `DATABASE_USER` | Exact username from the Supabase Session Pooler URI (often includes a project reference; do not guess) |
| `DATABASE_PASSWORD` | The already-saved Supabase password, privately entered and marked secret |
| `RIT_CLOUD_DEMO_SEED_ENABLED` | `true` |
| `RIT_CLOUD_DEMO_CUSTOMER_EMAIL` | Owner-chosen new disposable demo email, not any public fixture email |
| `RIT_CLOUD_DEMO_CUSTOMER_PASSWORD` | Owner-created strong private password (16–128 characters; upper/lowercase, digit, symbol) |
| `RIT_CLOUD_DEMO_OWNER_EMAIL` | A different new disposable demo email |
| `RIT_CLOUD_DEMO_OWNER_PASSWORD` | Different strong private password |
| `DB_POOL_SIZE` | `2` |

Do not set `DEMO_SEED_ENABLED=true` and do not create an admin account. Keep email addresses and both demo passwords private. Passwords must not match each other or the published local fixture passwords.

## Step 3 — Convert Supabase Session Pooler URI privately

1. In Supabase open organization **RIT Canteen** → project **rit-canteen** → **Connect** → **Direct or ORM** → **Session pooler** → **URI**.
2. Copy the host, port, username, and database name from that live URI. Use Session mode on port normally `5432`, not Transaction mode `6543`. Supabase says Session mode is appropriate for persistent app connections on IPv4-only networks. Consult [Supabase connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres).
3. Set the JDBC URL host/port/database, e.g. the safe shape above. Put the exact provider username in `DATABASE_USER`. Keep the database password only in `DATABASE_PASSWORD`.
4. Enable `sslmode=require`; this encrypts the connection. Supabase notes that full certificate verification requires `verify-full` and the root certificate, which is not wired into this demo configuration.
5. Never paste the full URI/password in an issue, source file, chat, screenshot, or log. If the password has URI-reserved characters, the provider URI may encode them; the separate Render password field takes the actual password, not its URI-escaped spelling.

## Step 4 — Wait for deploy and validate

Wait for Render state **Live**. The service runs Flyway and inserts 4 sellers, 15 categories, and 72 illustrative items, then creates only the two privately configured identities. Expected public GET results:

```text
https://<actual-service>.onrender.com/health  -> HTTP 200, {"status":"ok"}
https://<actual-service>.onrender.com/ready   -> HTTP 200, {"status":"ready"}
```

Copy the exact generated HTTPS origin into your private notes. There is no valid URL to use until this point. If startup fails, read Render logs for sanitized configuration/migration errors. Review the environment values privately. Do not expose environment dumps. A failed database readiness check is 503. Do not reset/drop schemas; review migration and pooler configuration without destructive changes.

Render Free may sleep; the first GET can wait for a cold start. Retry health GETs only after waiting. Never blind-retry an order POST after a timeout.

On the local 512 MB / 0.1 CPU cold-start check with a fresh database, migrations plus initial fixture/account seeding took about two minutes before health checks answered. Warm `/health` and `/ready` several minutes before the demonstration. A restarted service with migrations already applied starts faster.

Keep both demo passwords stable across restarts. The bootstrap hashes them once; changing the environment value later does not change the existing account password.

## Step 5 — Set illustrative seller hours

Build/install the APK below, sign in as the private owner, open Main Canteen seller operations / hours, and set the weekly illustrative schedule. Do this before bulk ordering. Hours remain UNKNOWN until explicitly configured, by design. Use a future pickup time inside that schedule and Asia/Kolkata timezone.

## Step 6 — Build cloud APK and install

From repository root, substitute the real HTTPS URL:

```sh
./gradlew -PritCloudDemo=true -PritApiBaseUrl=https://<actual-service>.onrender.com :app:assembleDebug
```

The cloud flag without a URL or with a non-HTTPS/non-origin URL fails Gradle configuration. APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This is an installable **debug** APK, not release-signed. Transfer it to the phone using a private method, install, and permit this installation source if asked. No URL credential or backend secret is embedded.

## Step 7 — Independent phone acceptance checklist

After transferring/installing, unplug the phone from USB. Turn off Wi-Fi and use mobile data if possible. No laptop server, ADB reverse, or local DB may be running for functional checks.

1. Customer login succeeds; wrong password fails.
2. App browses four server sellers and server item/price/availability data.
3. With owner hours configured, customer creates a bulk request; record remote ID/version.
4. Owner login sees that same request and counteroffers or approves the current quote revision.
5. Customer reloads and approves the latest version; seller approves where required.
6. State ends at `AGREED_AWAITING_PAYMENT` only. No payment, kitchen fulfillment, pickup, or QR redemption is claimed.
7. Force-stop/reopen, log in, and verify persisted request. If practical, verify again after a Render restart.
8. Seller availability edit appears after customer refresh.
9. Wrong roles, stale version, offline errors and server failures remain honest.

## Not executed by the developer

No account login or provider dashboard action; no Render service creation; no Supabase credentials read or entered; no public deployment; no real service URL; no cloud health/auth/catalog call; no cloud APK with a real endpoint; no physical independent-phone verification. This is deliberate. The backend code was prepared for owner-operated deployment, not deployed.
