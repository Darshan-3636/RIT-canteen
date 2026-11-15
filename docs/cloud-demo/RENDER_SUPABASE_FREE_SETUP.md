# Render Free + Supabase Free setup

> Historical service-creation guide. The Render service now exists; use [the current rollout gate](CURRENT_RENDER_SUPABASE_ROLLOUT.md) to verify its settings and deploy the integration branch. Do not create another service or use the old branch named below.

This is an operator procedure for the existing Supabase Free project `rit-canteen` and Render account already linked to GitHub. The developer has not created a Render service or accessed either account. No live hostname exists yet.

## 1. Confirm free selections

Render currently documents a Free web service plan with 512 MB RAM and 0.1 CPU, and notes that free services spin down, have no persistent disk, and can be restarted. Confirm the dashboard still offers **Free** at creation and choose no add-ons, disk, paid compute, paid IP, custom domain, or upgrade. [Render free services](https://render.com/docs/free) · [Render compute plans](https://render.com/docs/compute-plans)

**Strict zero-cost safeguard:** Render documents that outbound bandwidth beyond the workspace's included amount can be billed when a payment method is attached; traffic from this service to Supabase is outbound traffic. The documented pipeline spend limit covers build minutes, not outbound bandwidth. Before deploying, open Render **Billing** and confirm the account cannot incur charges (for example, no payment method is attached and over-limit behavior is suspension). If that cannot be assured, stop before creating the service. Do not rely on the word “Free” alone as a guarantee of zero charges. [Render bandwidth limits](https://render.com/docs/outbound-bandwidth) · [Render Free plan limits](https://render.com/docs/free)

Supabase free projects can pause after inactivity and have plan limits. This demo is disposable; verify the current project is still on its Free plan. Do not upgrade. [Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres)

## 2. Read the private Session Pooler parameters

1. Sign in to the existing Supabase account and open organization **RIT Canteen**, project **rit-canteen**.
2. Open **Connect** → **Direct or ORM** → **Session pooler** → connection type **URI**.
3. Use the displayed host, port, username, and database exactly. Session pooler generally uses port `5432` and a username containing the project reference; never construct either value from memory. Do not choose Transaction pooler (`6543`).
4. Keep the saved database password private. No one should paste it into chat, source files, or this document. If converting the provider URI, percent-decode the password only in a private password manager or local secure editor. Put the URI in Render as a JDBC URL containing only the host/port/database and `?sslmode=require`; put username and raw password in their separate secret fields. Example shape only:

   - `DATABASE_URL=jdbc:postgresql://<SESSION_POOLER_HOST>:5432/postgres?sslmode=require`
   - `DATABASE_USER=<SESSION_POOLER_USERNAME_FROM_SUPABASE>`
   - `DATABASE_PASSWORD=<enter privately in Render; never paste here>`

If the original URI includes query options, retain only options documented for the PostgreSQL JDBC driver and required here. Supabase recommends Session pooler for IPv4-only clients; `sslmode=require` encrypts traffic. [Supabase connection modes and SSL](https://supabase.com/docs/guides/database/connecting-to-postgres)

## 3. Create the Render Docker Web Service

1. In Render Dashboard select **New** → **Web Service**.
2. Choose repository `Darshan-3636/RIT-canteen`.
3. Set branch to `feat/rit-free-cloud-fullstack-demo`.
4. Set **Language/Runtime** to **Docker**.
5. Set **Root Directory** to `backend`.
6. Set **Dockerfile Path** to `./Dockerfile` (relative to root directory). Leave Docker build and start commands at their Dockerfile defaults.
7. Choose a service name such as `rit-canteen-cloud-demo`, choose the closest region offered to the existing Supabase project if the dashboard makes it clear, and select **Free**. Do not attach a disk or add a paid feature. If location choices do not match or imply charge, keep the zero-cost option and accept latency rather than upgrading.
8. Before creating, verify the summary says Free. Create the service.

The image installs the standalone Gradle distribution and runs `./bin/rit-canteen-backend` as an unprivileged user. Render supplies `PORT`; the app listens on `0.0.0.0`. [Render Docker services](https://render.com/docs/docker)

## 4. Set environment variables privately

Open service **Environment** and add the following. Enter passwords directly in the dashboard secret input; the placeholders below are labels, never literal values.

| Name | Value or rule |
|---|---|
| `RIT_ENV` | `cloud_demo` |
| `HTTP_HOST` | `0.0.0.0` |
| `PORT` | Leave to Render. Do not override unless Render instructs you to. |
| `DATABASE_URL` | JDBC URL built from the exact private session-pooler host/port/database, with `sslmode=require`. |
| `DATABASE_USER` | Exact session-pooler username from Supabase. |
| `DATABASE_PASSWORD` | The database password, entered privately; mark secret. |
| `RIT_CLOUD_DEMO_SEED_ENABLED` | `true` |
| `RIT_CLOUD_DEMO_CUSTOMER_EMAIL` | A new private demo-only email address, not a published fixture email. |
| `RIT_CLOUD_DEMO_CUSTOMER_PASSWORD` | Unique 16–128 character password with upper/lowercase, digit and symbol; mark secret. |
| `RIT_CLOUD_DEMO_OWNER_EMAIL` | A different private demo-only email address. |
| `RIT_CLOUD_DEMO_OWNER_PASSWORD` | Different unique strong password; mark secret. |
| `DB_POOL_SIZE` | `2` |

Do not set `DEMO_SEED_ENABLED=true`; that flag is only for local mode. Do not provide any admin account. The JVM memory cap is included in the Docker image. Save changes and let Render deploy.

## 5. Check startup and health

Render logs should show a successful service startup after Flyway migration. No secret should appear in logs. First startup creates the four catalog sellers and 72 illustrative menu items, then creates only the two private accounts. Seeding uses insert-once behavior and preserves edits. It will fail closed if credentials are absent/weak, if an account email collides with a different identity, or if old known local fixture identities exist in this database.

In the service page, copy the generated `https://<service>.onrender.com` URL only after deployment reaches **Live**. In a browser check:

```text
https://<service>.onrender.com/health  -> 200 {"status":"ok"}
https://<service>.onrender.com/ready   -> 200 {"status":"ready"}
```

`/ready` returning 503 means PostgreSQL is unavailable or paused. Check Render logs and the private pooler host/username/password/SSL values. Do not print the environment or share screenshots containing secret fields. Free services can sleep; the first request after idle may take a while. Wait and retry **GET** health checks. Do not retry a timed-out POST order blindly.

## 6. Configure seller hours before bulk ordering

Sign into the Android app later as the private Main Canteen owner. Open seller operations / hours and save the actual demo schedule for `main-canteen` in Asia/Kolkata. Hours are deliberately UNKNOWN until saved; bulk requests will be rejected until then. Never imply the hours represent the real canteen. The schedule survives service restarts in Supabase.

## 7. Persistence and cost controls

Render's filesystem is ephemeral, so all durable records are in Supabase. Never reset/drop the database to troubleshoot. Keep both providers on free selections, watch dashboard usage, and stop if any action proposes a charge. Keep the configured demo passwords unchanged across restarts: bootstrap never rehashes an existing account. This is an unmoderated public demo API with basic login but no production-grade rate limiting; share APK/account credentials only with intended demonstrators and rotate the demo passwords after the event.
