# Frontend QA report — Hub removal and 72-item catalog

## Scope and environment

- Branch: `feat/rit-complete-frontend-experience`, based on reviewed remote head `0343ec1b0b4ff655368fd659392da1433632b5ed`.
- Changed paths: `app/**` and `docs/frontend/**` only. No backend, API contracts, migrations, main branch, payments or production authentication changed.
- SDK: `/home/darshan/.cache/nadr-tooling/android-sdk`; Moto G60 serial `ZD2226B68F`.
- Screenshots ingested into the model context: **0**. UI checks used Compose semantics, package/manifest inspection and Gradle XML; four generated food photos were inspected locally as assets, not device screenshots.

## Commands and results

```bash
ANDROID_HOME=/home/darshan/.cache/nadr-tooling/android-sdk ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:lintDebug :app:lintRelease --no-daemon --max-workers=1
ANDROID_HOME=/home/darshan/.cache/nadr-tooling/android-sdk ./gradlew \
  :app:connectedDebugAndroidTest --no-daemon --max-workers=1
```

| Check | Actual result |
| --- | --- |
| Debug unit tests | **PASS:** 146 tests, 0 failures/errors/skipped (Gradle XML). |
| Debug APK | **PASS:** `app/build/outputs/apk/debug/app-debug.apk`. |
| Release APK | **PASS:** `app/build/outputs/apk/release/app-release-unsigned.apk`; release is unsigned and not installable as-is. |
| Debug lint | **PASS:** 0 errors, 31 warnings. |
| Release lint | **PASS:** 0 errors, 29 warnings. |
| Moto G60 connected UI tests | **PASS:** 67 tests, 0 failures/errors/skipped (connected XML). The normal-login navigation class separately passed all 10 tests before the full run. |
| Final debug APK install and normal launch | **PASS:** `adb install -r` returned `Success` without uninstalling/resetting data; installed package query returned **1** launcher (`edu.msrit.canteen/.MainActivity`); `am start` launched it and `dumpsys` reported it resumed/foregrounded. |

The first device suite found stale 12-/16-image assertions, a race in session reset, and Back-test expectations that queried a finished activity. After correcting these, a subsequent run passed 66/67 tests; its only failure was a remaining Compose query after customer-root Back removed the hierarchy. Another attempted suite was interrupted when the Moto G60 screen locked and turned off; device logs did not show an RIT Canteen crash. The device was unlocked, and USB stay-awake was temporarily enabled for the final clean 67/67 run. The original stay-awake setting (`0`) was restored and rechecked afterward. These failed attempts are not counted as passing verification.

## Static boundaries and coverage

- Merged debug and release manifests each have exactly **one** `MAIN` + `LAUNCHER` activity, `edu.msrit.canteen.MainActivity`. Neither contains `DebugTestingActivity` nor the old RIT Testing Hub label. Source search across main/debug/release finds no `DeveloperPanel`, `DebugTestingActivity`, `startInDeveloperHub` or `Route.DeveloperPanel` reference.
- Unit tests cover 72 distinct stable item IDs, 18 per seller, semantic category and packaged-kind separation, and explicit image assignments. The four unchanged seller IDs and 12 old item IDs remain. The 60 new WebP files are distinct SHA-256 binaries at 640×480 and 2.8 MB total; the existing 12 image resources are also unique.
- Connected tests use the ordinary `MainActivity` login flow for catalog browse/search/detail/cart, seller item management and role permissions, role login/logout/back, admin destinations, image decoding, and one-launcher package checks. Obsolete Hub-entrypoint tests were retired; the replacement suite is smaller than the previous milestone's 93 device tests, so this report does not claim identical feature-by-feature coverage for removed Hub shortcuts.
- Release source-set mock authentication remains inert. The 60 added catalog IDs are Android demo-only and absent from the current 12-item backend seed; local pricing, availability, checkout and payment simulation are **not** server-authorized or real commerce.

## Manual owner spot checks

1. Confirm one RIT Canteen icon on the launcher; remove any manually pinned stale old Hub shortcut if one remains after in-place upgrade.
2. Browse all four seller menus and visually approve the distinct photos, names, prices and category labels.
3. In Campus Pantry, verify juice and water appear together in Beverages; inspect Snacks/Packs separately.
4. Check customer cart and seller menu editing on the compact Moto G60 screen, including light/dark contrast.
5. Sign in as customer and seller, then use Back and sign-out; no Hub, prior privileged screen or duplicated launcher should appear.

No real backend authentication, payment provider, live inventory or production item image hosting was tested or implemented by this checkpoint.
