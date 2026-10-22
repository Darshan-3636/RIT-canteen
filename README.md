# RIT Canteen

Native Android canteen prototype built with Kotlin and Jetpack Compose. It includes customer discovery, separate individual and bulk carts, bulk-request negotiation previews, seller operations, analytics, and a platform-administration preview.

This is a **development demo, not a production ordering or payment app**. Email OTP, live seller data, verified payments, backend synchronization, and individual-order fulfillment are not connected. Sample orders, identities, analytics, offers, and the Developer Testing Hub are available only in debug builds. Release builds do not provide mock authentication or preview routes.

## Build and test

Use a JDK compatible with the Gradle wrapper and Android SDK API 36. Set `ANDROID_HOME` or add an untracked `local.properties` with `sdk.dir`, then run:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

With an authorized Android device or emulator, run `./gradlew :app:connectedDebugAndroidTest`. The debug build installs two launchers: **RIT Canteen** and **RIT Testing Hub**. Open the Hub to select a mock role, load demo carts, navigate to implemented screens, and reset preview data. The old logo-tap shortcut has been removed.

See [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md), [`docs/DEVELOPMENT_TESTING.md`](docs/DEVELOPMENT_TESTING.md), and [`docs/UI_SCREEN_INVENTORY.md`](docs/UI_SCREEN_INVENTORY.md) for feature boundaries and verification history. Local design-reference originals and physical-device screenshots are intentionally not included in this code repository.

No open-source license has been granted for this repository or its visual assets.
