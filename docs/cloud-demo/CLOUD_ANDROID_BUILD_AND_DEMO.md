# Cloud Android build and phone demo

The network-backed transport is in the Android **debug** source set. The cloud APK is an installable debug build, not a signed production release. It includes the existing single launcher and bundled menu images; it does not contain a database URL/password, app API key, account password, or published local fixture account hints. The hosted service also refuses to start if known local fixture identities exist in its database.

## Build after Render has a real HTTPS URL

From the repository root, replace the placeholder with the actual Render URL copied from the service dashboard. Do not include `/api/v1`, a trailing path, username, password, query, or fragment.

```sh
./gradlew -PritCloudDemo=true -PritApiBaseUrl=https://<actual-service>.onrender.com :app:assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat -PritCloudDemo=true -PritApiBaseUrl=https://<actual-service>.onrender.com :app:assembleDebug
```

Gradle fails configuration if `ritCloudDemo` is enabled without a valid HTTPS origin. Local debug builds without these properties continue to use only `http://127.0.0.1:8080`; runtime validation refuses any other local endpoint. Cloud requests have bounded 20-second connect, 165-second read, and 180-second total timeouts for a sleeping free instance.

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This runbook uses Android Studio/Android SDK installed for the project. If Gradle reports a missing SDK or JDK, install the repository's Android SDK requirements locally; no provider tool is needed. The APK is not Play Store/release-signing ready.

## Install without USB

1. Transfer the APK privately to the phone using a local file transfer method (USB cable transfer, Nearby Share, or private cloud storage). Do not post it publicly with the demo passwords.
2. On the phone, open the APK and allow installation from that one source if Android prompts. Install **RIT Canteen**.
3. Turn off Wi-Fi and use mobile data for the functional demonstration when available. A normal cable file transfer is fine, but unplug before testing. No ADB reverse, laptop server, or local PostgreSQL is involved.

## Owner acceptance walk-through (about 5–10 minutes after service is warm)

1. Open the app and sign in with the privately created customer email/password. The server must return the customer identity; selecting a UI role never grants authority.
2. Browse the four sellers and 72 server catalog items. Prices and availability come from the server; product photos are bundled app images keyed by catalog item.
3. Choose `main-canteen`, add items and create a bulk request for a pickup time that falls inside the owner-configured hours. Record the server request ID/version. A timeout after POST is ambiguous: first reload the request list before deciding what to do; never make a blind duplicate POST.
4. Sign out, sign in as the private Main Canteen seller owner, open the same request, and counteroffer or approve the current quote revision.
5. Sign out and return as customer. Reload the server request and approve the current version. If seller approval is required, switch back and approve it.
6. Confirm the terminal business state says **AGREED_AWAITING_PAYMENT**. It is not paid, collected, kitchen-fulfilled, or QR-redeemed.
7. Force-stop and reopen the app, sign in, and verify that the same request is still present. If practical, wait for or trigger a Render restart and verify again. Supabase owns persistence.
8. If testing a seller availability edit, make it as owner and refresh the customer catalog to confirm the server change.

Wrong credentials/role, server errors, offline state, or stale versions must show an honest failure. An agreed quote is not a sale. Keep real payment and personal information out of this demo.

## Troubleshooting

- **APK build says URL required/invalid:** use the exact Render `https://` origin with `-PritCloudDemo=true`; exclude endpoint path and credentials.
- **First request waits:** Render Free may have spun down, and the first deployment must apply migrations and seed the demo. This local 512 MB / 0.1 CPU cold-start check took about two minutes before the server answered health checks. Open `/health` and `/ready` a few minutes before the demonstration, then wait and retry safe GETs. Do not retry order submission without first reloading state.
- **`/ready` or app calls fail:** inspect Render health/logs and the owner's private Supabase pooler values; never send credentials to the developer.
- **Bulk request reports hours unknown/outside hours:** sign in as Main Canteen owner and configure a real illustrative weekly schedule, then choose a valid future pickup time in Asia/Kolkata.
- **Images appear:** they are local bundled assets, not evidence the image itself was fetched from the server.
