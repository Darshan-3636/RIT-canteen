# RIT Canteen

Native Android canteen app (Kotlin, Jetpack Compose) with a Kotlin/Ktor + PostgreSQL backend in [`backend/`](backend/README.md). It is a working college-pilot build, not yet a production service.

## What works

- **Customers:** open straight on the canteen menu; separate individual and bulk carts; bulk requests with seller counteroffers; one Pay Now step; order status with prep time, a 4-digit pickup PIN and a pickup QR code; in-app notifications, saved items, offers and support tickets.
- **Sellers:** Orders page with New / Ready / Done tabs and handover by QR scan or PIN; dashboard with live new-order count; menu, availability and image management; promotions; inventory; insights; support replies.
- **Platform admin:** seller and membership management, support handling, audited CSV reports.
- **Backend:** email/password accounts with role-scoped sessions, catalog, carts, orders, bulk requests, notifications, analytics and admin APIs, Flyway migrations (V001–V054).
- **Payments:** PhonePe Standard Checkout against PhonePe's **UAT sandbox only**. Outside local development, an order is marked paid only by checking its status with PhonePe; the simulated-payment shortcut works only in local development.

## Not done yet

- Email OTP / email delivery (login is email + password).
- Live (production) PhonePe payments.
- Production verification of sellers, and a production deployment.

## Build and test

Android: use a JDK supported by the Gradle wrapper (Android Studio's bundled JDK works) and Android SDK API 36. Set `ANDROID_HOME` or add an untracked `local.properties` with `sdk.dir`, then run:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

With a device or emulator connected, run `./gradlew :app:connectedDebugAndroidTest`.

The debug app talks to a local backend at `http://127.0.0.1:8080` by default (use `adb reverse tcp:8080 tcp:8080` for a USB phone). For a hosted backend, build with `-PritCloudDemo=true -PritApiBaseUrl=https://<host>`.

Backend: any JDK 17 or newer builds it (bytecode targets Java 17, matching the Docker image). See [`backend/README.md`](backend/README.md) for PostgreSQL setup, environment variables and integration tests:

```sh
cd backend && ./gradlew test
```

See [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) for current status and history, and [`docs/pilot/IMPLEMENTATION_PROGRESS.md`](docs/pilot/IMPLEMENTATION_PROGRESS.md) for the pilot rollout checklist.

No open-source license has been granted for this repository or its visual assets.
