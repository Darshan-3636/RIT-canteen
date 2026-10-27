# Lead demo login, account isolation and catalog imagery report

## A. Git state and team integration

- Base: `bd34ac87548bec9c07465516eff5056f4812687b` from `origin/integration/rit-ui-teams-01-04`.
- Work branch: `feat/rit-demo-auth-catalog-images`. Neither `main` nor the tested integration branch was edited or merged.
- `feat/rit-account-debug-login-ux` was absent from the fetched remote, so no Team 05 commit or response file was merged or fabricated. The lead implemented its intended features directly, reusing the existing customer account/settings/support, seller operations, administrator and Developer Testing Hub UI.
- Implementation, tests, 13 app artwork resources and this report were committed as `3427b2e` (`Add debug demo accounts and complete catalog artwork`, 46 files changed). The final remote SHA and push verification belong in the handoff response. Only this feature branch is a push target; no merge or force-push is performed.

## B. Login team integration

`feat/rit-account-debug-login-ux` was not on the fetched remote. No teammate commit was merged and no `TEAM_05_RESPONSE.md` was fabricated. The lead directly completed the account/login scope in this milestone. Existing profile, account settings, support, notifications, templates, seller operations, administrator and Developer Testing Hub screens were reused as presentation foundations, then connected to the active demo identity and natural navigation. There were no teammate merge conflicts.

## C. Demo accounts

| Stable account ID | Persona | Role | Scope / landing |
| --- | --- | --- | --- |
| `demo-student-arya` | Arya Rao | Customer · Student | Customer Home |
| `demo-student-kiran` | Kiran Shah | Customer · Student | Customer Home |
| `demo-faculty-maya` | Dr Maya Iyer | Customer · Faculty | Customer Home |
| `demo-owner-main` | Main Canteen Owner | Seller owner | Main Canteen dashboard |
| `demo-owner-north` | North Counter Owner | Seller owner | North Counter dashboard |
| `demo-manager-main` | Main Canteen Manager | Seller manager | Main Canteen dashboard; delegated capabilities |
| `demo-staff-main` | Main Canteen Staff | Seller staff | Main Canteen daily operations, view-only by default |
| `demo-admin-platform` | Platform Administrator | Administrator | Platform Overview |

The normal debug Welcome Login opens a masked-password form. All eight seeded email/password pairs were accepted in the Moto G60 device test; an unknown email and incorrect password were rejected. A successful sign-in restores a device-local account ID and routes directly to the authorized dashboard, without using the Hub. Seller capabilities are derived from the active employee and seller; administrator permissions are separate. The dedicated debug Hub remains available for direct preview testing; the old logo-tap shortcut remains removed. Hub role selection signs out any active demo account before applying its own mock identity.

Customer profile, preferences, templates, support tickets and notifications are stored per customer ID in debug-local preferences. Favorites already key by customer ID. Individual demo orders are separated per customer in memory and bulk previews filter by customer ID. Active carts clear on account switch; sample hub mutations are directed to the separate `debug-customer` identity. The synthetic shared catalog/analytics fixtures are public demo content, not private account history.

## D. Customer signup

Debug Get Started creates Student or Faculty customer accounts only. It validates full name, email uniqueness/shape, password length and letter/digit content, and confirmation. New customers receive a generated stable ID; credentials are saved locally with a random salt and PBKDF2-derived hash, never the entered password. The selected affiliation and optional department populate the customer profile. The Moto G60 test created a disposable customer, force-stopped and relaunched the app, confirmed its session/profile survived, logged out and signed back in successfully. The created password is deliberately absent from this report. No verification message, real OTP, backend account or seller/admin entitlement is created. Seller signup remains invitation-only information. Release uses the pre-existing unavailable real-OTP entry, not this local signup.

## E. Role-based navigation

Customer accounts land on the seller-first customer Home. Main Canteen and North Counter owners, the Main Canteen manager and staff land in their seller-scoped operations workspace; the platform administrator lands on Platform Overview. Ordinary Login reaches those destinations without opening the Hub. Customer Profile links to orders, favourites, templates, notifications, support, settings and account management. Seller/admin dashboards expose My account and sign-out. Logout clears the active session, customer projection and cart drafts, and removes protected pages from the back stack. Account switches cannot inherit the prior account's private customer state or seller capabilities. The Hub's separate preview identity remains available only in debug builds.

## F. Image inventory and provenance

All four current sellers and all twelve current menu items have distinct stable-ID mappings. The 13 new images were made with the built-in image-generation tool as original concept imagery, inspected, and optimized to 1200-pixel-wide WebP resources (~1.6 MB total). The other three are pre-existing suitable food photos. These are illustrative concepts, **not** verified RIT vendor or campus photographs. No reference asset was overwritten.

The prompt set used a common direction of realistic ordering-app food photography or inviting Indian campus-canteen counters, natural warm light, accurate product appearance, landscape composition, crop-safe subject placement, and no text, prices, ratings, watermark or brand logo. Seller variants specified (1) Indian college canteen counter, (2) lunch/snacks counter, (3) coffee/light-bites counter and (4) packaged pantry. Food variants specified each named dish or packaged product in the table below. Image-generation originals are retained outside the repository in the local Codex generated-images directory; the WebP derivatives are the shipping app assets.

All 16 drawables decoded successfully in the Moto G60 instrumentation test. “Viewed” below means a screenshot was also visually inspected on that physical device; all other image compositions were inspected locally before import.

| Stable ID | Bundled drawable | Provenance | Moto G60 check |
| --- | --- | --- | --- |
| `main-canteen` | `seller_main_canteen.webp` | New generated concept | Decoded; viewed |
| `north-counter` | `seller_north_counter.webp` | New generated concept | Decoded; viewed |
| `coffee-corner` | `seller_coffee_corner.webp` | New generated concept | Decoded |
| `campus-pantry` | `seller_campus_pantry.webp` | New generated concept | Decoded |
| `masala-dosa` | `catalog_dosa.webp` | Existing suitable food photo | Decoded; viewed |
| `veg-biryani` | `catalog_biryani.webp` | Existing suitable food photo | Decoded; viewed |
| `filter-coffee` | `catalog_coffee.webp` | Existing suitable food photo | Decoded; partly viewed |
| `idli` | `food_idli.webp` | New generated food photo | Decoded |
| `lemon-rice` | `food_lemon_rice.webp` | New generated food photo | Decoded |
| `water-bottle` | `food_water_bottle.webp` | New generated product photo | Decoded |
| `north-lunch` | `food_north_lunch.webp` | New generated thali photo | Decoded |
| `north-snack` | `food_north_snack.webp` | New generated snack-box photo | Decoded |
| `corner-coffee` | `food_corner_coffee.webp` | New generated group-coffee photo | Decoded |
| `corner-biscuits` | `food_corner_biscuits.webp` | New generated packaged-biscuit photo | Decoded |
| `pantry-juice` | `food_pantry_juice.webp` | New generated juice-carton photo | Decoded |
| `pantry-water` | `food_pantry_water.webp` | New generated sealed-water photo | Decoded |

`CatalogArtwork` keys image identity by stable fixture ID; `FoodPhotoContent` and `SellerPhotoContent` resolve bundled drawables. Seller cards, menus, food details, featured food, cart, offers, favorites, order previews, seller editing and analytics use the same photo fields. A locally selected seller/item image wins over the bundled one. Unavailable items retain their imagery and unavailable label. Future unknown IDs fall back to a neutral native icon rather than unrelated art. Resource loading was checked on device; visual crop coverage is recorded below.

## G. Modified files

- Auth/session: `auth/DemoAccount.kt`, debug/release `auth/DevelopmentDemoAccounts.kt`, `ui/screens/DemoAuthScreens.kt`, `ui/screens/DemoRoleAccountScreen.kt`, `navigation/RitAppNavigation.kt`, debug/release `navigation/DevelopmentPreviewAccess.kt`.
- Account isolation and role fixtures: debug/release `customer/DevelopmentCustomerAccount.kt`, debug/release `customer/DevelopmentIndividualOrders.kt`, debug `customer/DevelopmentSellerBusiness.kt`, `ui/screens/CustomerAccountScreens.kt`, `ui/screens/CustomerOrdersScreen.kt`.
- Catalog/image UI: `customer/CustomerCatalog.kt`, `ui/components/CustomerCatalogComponents.kt`, `ui/components/RitContent.kt`, `ui/components/RitControls.kt`, `ui/screens/CustomerHomePreviewScreen.kt`, `ui/screens/CustomerSellerFirstScreens.kt`, `ui/screens/SellerDailyOperationsPresentation.kt`, `ui/screens/PlatformAdminScreens.kt`, and the 13 WebP resources listed above.
- Verification/docs: `CustomerCatalogTest.kt`, `SellerBusinessTest.kt`, `DemoAccountsDeviceTest.kt`, `DemoLoginNavigationUiTest.kt`, `WelcomeRegistrationUiTest.kt`, `DeveloperHubAccessUiTest.kt`, `docs/DEVELOPMENT_TESTING.md`, `docs/IMPLEMENTATION_STATUS.md`, this report.

## H. Tests and visual QA

- Debug unit suite: **134/134 passed**, including the final post-polish rerun; debug APK and Android-test source compilation also passed again.
- Moto G60 (Android 12/API 31, serial `ZD2226B68F`): **88/88 connected UI tests passed** in the complete rerun. The six focused tests covering the initial test-expectation fixes also passed. The tests cover seeded identities, wrong credentials, local signup/persistence, role navigation, account isolation, 16 resource decodes and existing customer/seller/admin regressions.
- Debug/release APK assembly and debug instrumentation-test compilation: passed. Both `lintDebug` and `lintRelease` passed with zero errors using one Gradle worker (20 debug warnings, 18 release warnings). Release manifest exposes only `MainActivity`; no debug Hub route or seeded auth string was found in the release binary scan.
- Real device interaction outside the test runner: final debug APK installed with data preserved; new customer signup, seller-first Home, profile, force-stop/relaunch restoration, logout and login-back passed. Main/North seller photos and Main Canteen menu crops were visually inspected. Dark Welcome/Login/Profile/customer catalog and light seller dashboard/menu were inspected. The seller dashboard's paired cards remained even at 1.3× font scale; text stayed readable. The final APK restored the seller session after relaunch and displayed the polished “Seller owner” label. Device night mode and font scale were restored to their original dark/1.0 settings.
- Initial integration attempts exposed an outdated category-screen test expectation, one below-fold lazy-list test assumption, two missing new-test imports and an intermittent lint analyzer crash with parallel workers. Tests were corrected to the approved seller-first flow, the list test was made scroll-aware, imports were fixed, and single-worker lint completed. No known failing tests remain.

## I. Security

- Seeded passwords and credential verification reside only in `src/debug`; the release source-set implementation has no seeded accounts and always rejects sign-in and signup. Debug routes and the Testing Hub are not registered in release navigation or release manifest. This is not server authorization.
- Signup cannot create owner, manager, staff or administrator roles. Seller capabilities remain scoped to seller membership. A signed-out user cannot use the previous account's route stack.

## J. Remaining issues

Real OTP, backend accounts, server role enforcement, payment, production notifications/support, trusted seller data, and actual vendor photography remain future work. Saved individual-order previews are process-local. All 16 resources decoded on device, but every individual crop was not manually inspected on the phone; TalkBack traversal was not performed. The created disposable QA account remains device-local for further review. The release build's real authentication entry remains intentionally unavailable.

## K. Final assessment

**Partially verified for a development demo**: the implemented flows, builds, lint, complete Moto G60 automated suite and representative visual checks pass. Production authentication/server authorization and exhaustive manual accessibility/artwork review are explicitly outside this debug-demo milestone or remain for owner review.
