# Cloud demo execution report

## Source and status

- Status: code complete and locally verified; push status is recorded in the final handoff; **not deployed**.
- Required base: `d80d0de87655f50ea992357d1491f794d6dfde5d` (`integration/rit-fullstack-local-demo`).
- Branch: `feat/rit-free-cloud-fullstack-demo`.
- Provider accounts were not accessed. No passwords, database URI, token, or API key is included in this repository.

## Changes

- Added an explicit `cloud_demo` backend mode with private first-create customer and Main Canteen owner identities. It rejects fixture identities in the cloud demo DB, rejects public registration, keeps roles server-derived, and does not reset passwords or restore disabled/revoked access on restart.
- Tuned Hikari to a configurable pool of 2 by default, added Render `PORT` support, PostgreSQL SSL and non-loopback checks, and a small multi-stage Docker image for the standalone backend.
- Added explicit Android cloud-demo URL injection with HTTPS-origin validation and longer bounded request timeouts. Local debug transport remains loopback-only.
- Added private operator and phone demo instructions under this directory.

## Verification

Executed with JDK 21 (the host default JDK 27 causes an existing Kotlin/Java target mismatch):

- `cd backend && ./gradlew test --no-daemon --max-workers=1` — passed. 10 non-database test cases passed, including cloud config/reserved fixture identity checks and an HTTP test that confirms cloud registration returns 403 before database access; 27 database-dependent cases were skipped because no isolated `RIT_TEST_DATABASE_URL` was configured.
- Re-ran with `RIT_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/rit_canteen_test`, `RIT_TEST_DATABASE_USER=rit_canteen`, `RIT_TEST_DATABASE_PASSWORD=disposable-local-test` and `--rerun-tasks` against a no-volume PostgreSQL 16 container bound only to loopback — passed: 34 passed, 3 fresh/legacy database cases skipped because their separate databases were not configured, 0 failures. Container was stopped and automatically removed. No Supabase/provider database was used.
- `./gradlew -PritCloudDemo=true -PritApiBaseUrl=https://example.invalid :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1` — passed, 147 tests, 0 skipped/failed, and cloud-configured debug APK compiled with a reserved `.invalid` test host only. The cloud UI now explains wake-up behavior and disabled account registration; an unused local-fixture account-hint list was removed.
- `./gradlew :app:assembleDebug --no-daemon --max-workers=1` — passed after that check to restore the local loopback APK. Output: `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew -PritCloudDemo=true :app:tasks --no-daemon --max-workers=1` — failed at configuration as expected because the HTTPS URL was omitted.
- `./gradlew -PritCloudDemo=true -PritApiBaseUrl=http://example.invalid :app:tasks --no-daemon --max-workers=1` — rejected as expected because cloud mode forbids HTTP.
- `./gradlew -PritCloudDemo=true -PritApiBaseUrl=https://example.invalid :app:assembleDebug --no-daemon --max-workers=1` — passed as a compile-path check using the reserved `.invalid` test domain only; the local APK was rebuilt afterward. This is not a live endpoint or deliverable APK.
- `git diff --check` — passed.
- APK DEX scan for the known local fixture passwords — none found. This scan ran against the locally configured APK; the cloud build was separately compiled with the reserved `.invalid` hostname and was not distributed.
- `docker build --pull=false -t rit-canteen-cloud-demo:local .` from `backend/` — passed on the final Dockerfile/source. The runtime image contains `/app/bin/rit-canteen-backend`; checked with `docker run --rm --entrypoint ls rit-canteen-cloud-demo:local -l /app/bin`.
- Local cloud-mode smoke: ran that Docker image on a temporary Docker network against a no-volume PostgreSQL 16 test database with a temporary self-signed TLS certificate and test-only credentials. `/health` and `/ready` returned 200; both private accounts logged in with customer/owner roles; registration returned 403; SQL confirmed 4 sellers, 15 categories, 72 items, 2 users, 0 admin users, and 0 published local fixture users. Restarting the app preserved the owner identity and readiness. Both containers and the network were removed.
- Resource check: ran the same image with `--memory=512m --cpus=0.1`. It reached `/health` and `/ready` after roughly two minutes on the first fresh-schema boot; Docker reported 162.3 MiB / 512 MiB resident use at readiness. This is a local container check, not a Render benchmark. Cloud HTTP timeouts were set to 165 seconds read / 180 seconds total to accommodate this wake/bootstrap case.

The local PostgreSQL integration/smoke checks above used disposable containers only. No owner Supabase or hosted checks have run; the owner's Supabase project was not a test target.

## Pending owner-only actions

Render service creation, Supabase credentials entry, public HTTPS deployment, seller-hours setup, cloud APK configuration with the actual URL, and independent-phone acceptance are intentionally not performed here. The service URL does not exist yet.
