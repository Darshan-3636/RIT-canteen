# Cloud demo security boundary and scope

## Environment boundaries

| Mode | Intended use | Fixture behavior |
|---|---|---|
| `local` | Loopback laptop development | Existing `DEMO_SEED_ENABLED=true` may create published, disposable local identities. Never expose this mode publicly. |
| `test` | Automated tests | No local or cloud demo fixture seed. |
| `cloud_demo` | Short-lived public demonstration only | Requires `RIT_CLOUD_DEMO_SEED_ENABLED=true`, SSL PostgreSQL URL, `0.0.0.0`, and four private customer/owner identity settings (two email/password pairs). Public registration is disabled. |
| `production` | Future production work | Demo seed flags are refused. This code is not a production deployment plan. |

The cloud demo uses the existing PBKDF2-HMAC-SHA256 password hasher, random salts, opaque 24-hour bearer tokens, SHA-256 token hashes at rest, and request-time live account/session/membership checks. The server creates one customer and one Main Canteen OWNER. It never grants admin rights. Account conflicts fail closed. Existing account passwords are not rehashed at restart; inactive users, removed memberships, or public fixture identities are not repaired or re-enabled.

The cloud catalog is illustrative and seeded idempotently. The initial weekly seller hours remain UNKNOWN. Do not treat catalog names, prices, availability, or schedule as verified live vendor information.

## Secrets and public exposure

- Database credentials and demo account passwords belong only in private Render environment fields. Do not put them in Git, APKs, screenshots, logs, or chat.
- The Android APK contains only a public HTTPS base URL. It never connects directly to PostgreSQL or contains a service-role/database key.
- Published local fixture emails/passwords are deliberately absent from the cloud database. Cloud startup refuses to proceed if those old local fixture account emails are found.
- HTTPS terminates at Render; PostgreSQL connections require SSL. Hikari pool defaults to two connections.
- The configured `sslmode=require` encrypts PostgreSQL traffic but does not verify the server certificate/hostname. Full certificate verification is not configured in this demo.
- `health` returns app liveness; `ready` only reports whether a database query works. Neither reveals secret configuration.
- Public signup is disabled in cloud demo. The cloud app hides the registration form and explains that accounts come from the owner; the server also returns `403 REGISTRATION_DISABLED` for direct registration requests.
- Keep private password values stable after the accounts are created. Bootstrap never rehashes or resets an existing account password at restart.

## Limitations

There is no application-level login rate limiting, CAPTCHA, abuse monitoring, or WAF policy in this emergency demo. Do not distribute credentials broadly; rotate them after the demonstration and delete/retire the disposable deployment when no longer needed. Free resources may sleep, restart, pause, or enforce plan limits. This service is not suitable for real student/vendor accounts or production use.

Favorites, preferences, notification screens, bulk templates, support, analytics/reports, and platform admin screens can still show local fixture data. Seller employee/promotion editing, catalog creation/upload, verified payment, pickup redemption, kitchen fulfillment, and spreadsheet export are not complete server-backed operations. The negotiated quote ends at `AGREED_AWAITING_PAYMENT`; no payment, revenue, preparation, pickup, or QR redemption is verified.
