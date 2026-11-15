# Frontend implementation response

Branch: `feat/rit-complete-frontend-experience`, isolated from exact Android base `89123939c3ad9370b486f3f7251efb26ae778780`. The full branch-head SHA is recorded in the final delivery response and can be verified with `git rev-parse HEAD`; a commit cannot contain its own SHA. No backend code or contracts were modified.

## Implemented native Android UI and local debug behavior

- Shared, prominent Log In on Welcome; customer signup remains secondary; seller self-registration fork is removed. Normal debug email/password authentication still derives the customer's, seller's or administrator's destination from the existing account role. The password visibility control is now an accessible 48dp trailing eye inside its field.
- Customer seller selection is search-only. Food category selection appears inside a chosen seller's menu. The customer Cart now retains Home/Cart/Orders/Saved/Profile navigation. Customer tab reselection restores the primary destination without stacking repeated roots.
- Seller Dashboard has no root Back arrow and shows seller-scoped action, new, waiting and pickup summaries plus individual demo requests. Seller Orders separates individual demo orders, bulk requests and pickup verification. Seller Dashboard/Orders/Menu/Insights (if capability granted)/Account have tab navigation. History includes distinct local simulated order states. All seller ID fallbacks to `north-counter` were removed from protected routes; denied routes return to the authorized root rather than the Testing Hub.
- `DemoCheckoutRepository` and its debug-only in-memory implementation support Main Canteen prepared-food individual request submission, seller response, agreed-current-version bulk demo checkout, outcome-controlled local payment simulation, opaque single-use code, QR display, customer tracking and seller-scoped manual verification/collection. Failure and pending issue no code; success cannot duplicate a confirmation. Wrong seller, wrong customer, staff without response capability, stale quote and redeemed code are denied. Demo checkout remains reachable through normal role login; its visible Testing Hub reset entry has been removed.
- Release source-set checkout implementation is inert; QR generation library is a debug-only dependency. Release cannot sign in with mock accounts or access debug routes.

## Current hotfix: one launcher and 72-item seller catalog

- The separate debug Testing Hub launcher, activity, composable and navigation route are removed. Both build variants have only the ordinary RIT Canteen launcher. Sign-in and sign-out clear earlier navigation roots; role-scoped demo accounts remain available through the normal debug Login screen. Customer Stories were preserved in `RitStoriesScreens.kt`.
- The four seller demos now have 18 distinct items each: 72 total, with the original 12 stable IDs preserved. Customer browse/search, details/cart, and seller menu management consume the expanded catalog. Menu category chips are derived from each seller's actual items; the FoodKind preparation/packaging attribute is separate from consumer categories. Both Campus Pantry juice and water appear together under Beverages.
- Sixty item-specific WebP photos were newly generated and added alongside the 12 existing item photos. Each new asset was separately prompted, compressed to 640×480, and mapped explicitly to one `FoodPhoto` entry. See `DEMO_CATALOG_EXPANSION.md` and `DEMO_CATALOG_IMAGE_MANIFEST.md`. These photos are demo artwork, not backend-hosted production images.

## Files changed

This checkpoint changes `app/**` and `docs/frontend/**` only. Key new or changed files are `customer/CustomerCatalog.kt`, `navigation/RitAppNavigation.kt`, `ui/screens/{CustomerSellerFirstScreens,SellerBusinessScreens,RitStoriesScreens}.kt`, 60 `res/drawable-nodpi/demo_food_*.webp` assets, catalog unit tests and normal-launcher device tests. It deletes the debug Testing Hub activity/manifest, Hub-only screen and obsolete Hub-directed instrumentation classes. No backend files or contracts changed.

## Boundaries and limitations

This is **implemented local debug simulation**, not backend-integrated customer/seller operations. Shared demo state survives role switching in one running process, not app process death or a second phone. Individual kitchen acceptance/readiness, live stock, real OTP/email, backend role enforcement, payment-provider verification, actual money, real pickup and verified revenue are **not implemented**. The release build's inert stubs are not production transaction functionality. See `FRONTEND_BACKEND_INTEGRATION_NOTES.md` for replacement and trust boundaries.

## QA and owner checks

Final-source checks passed: 146 unit tests and 67 connected Moto G60 tests, zero failures/errors/skips; debug/release builds and lint passed with zero errors. The final debug APK was installed in place and normal `MainActivity` launched; the installed package resolves to one launcher. Exact commands, warning counts and earlier corrected test failures are in `FRONTEND_QA_REPORT.md`. APK locations in the isolated worktree are `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`. Owner spot checks should focus on the single app icon, food-photo visual approval, each outlet's menu identity, and compact-screen/contrast quality. The Android-only 60 added IDs do not exist in the current backend seed; server catalog integration must reconcile them before production use.
