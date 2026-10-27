# Frontend QA report

## Environment and scope

- Android branch: `feat/rit-complete-frontend-experience` from `89123939c3ad9370b486f3f7251efb26ae778780`.
- SDK: `/home/darshan/.cache/nadr-tooling/android-sdk` (provided through `ANDROID_HOME` at build time).
- Connected device: Moto G60 (`ZD2226B68F`), authorized in ADB at test start.
- Screenshot count ingested into Codex/model context: **0**. Tests use source/Compose semantics and Gradle result XML; no visual screenshot loop was used.
- Changed paths are restricted to `app/**` and `docs/frontend/**`. No backend, API contract, migration or other branch was changed.

## Automated verification

Final results are completed below before branch delivery. Commands:

```bash
ANDROID_HOME=/home/darshan/.cache/nadr-tooling/android-sdk ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:lintDebug :app:lintRelease --no-daemon --max-workers=1 --console=plain
ANDROID_HOME=/home/darshan/.cache/nadr-tooling/android-sdk ./gradlew \
  :app:connectedDebugAndroidTest --no-daemon --max-workers=1 --console=plain
```

| Check | Actual result |
| --- | --- |
| Debug unit tests | **PASS:** 139 tests, 0 failures, 0 errors, 0 skipped (Gradle XML). |
| Debug APK | **PASS:** `app/build/outputs/apk/debug/app-debug.apk`. |
| Release APK | **PASS:** `app/build/outputs/apk/release/app-release-unsigned.apk`; `lintVitalRelease` also passed during assembly. |
| Debug lint | **PASS:** 0 errors, 31 warnings (HTML/XML/text report). |
| Release lint | **PASS:** 0 errors, 29 warnings (HTML/XML/text report). |
| Connected Moto G60 Compose/UI tests | **PASS:** 93 tests, 0 failures, 0 errors, 0 skipped (connected XML). First run found 4 outdated assertions after redesign; they were corrected and the complete suite was rerun successfully. |

## Coverage and boundaries

Unit coverage includes role/seller scopes, individual request approval, pending/failure/success payment transitions, no duplicate confirmation, and seller-scoped one-use pickup redemption. Compose tests cover shared Welcome login, removed seller-start fork, eye button semantics, customer/seller tabs, category placement, and demo checkout/pickup interaction. Existing quote revision, business, analytics and customer tests remain in the regression suite.

The release merged manifest includes `MainActivity` but no `DebugTestingActivity`; the debug merged manifest includes both. A release APK primary-DEX string check found no ZXing QR writer class. These are static boundary checks, not a claim that production authentication or payment is implemented. The first parallel lint invocation was stopped after an unusually long UAST analysis under system memory pressure; the final single-worker lint command completed successfully for both variants. Lint warnings are primarily existing Gradle-version, unused-resource and Compose style suggestions; no errors were reported.

## Known limits and owner verification

The local simulator is in-memory: role switching on the same running app retains state, but process death/relaunch loses unpersisted demo orders. Real backend, OTP/email, provider-verified payment, live seller availability and real pickup are not tested or implemented. Manual visual approval should focus on a compact Moto G60 viewport, light/dark contrast, code/QR legibility and the normal sign-out/back journey. No screenshots were needed for automated test assertions.
