# Development testing policy

Until the application is complete, every development prompt should end with relevant builds, unit tests, lint, available automated UI/device tests, actual results, tests that could not run, and a short feature-specific manual visual/device checklist. Do not claim physical-device testing unless a connected device was used. Do not start a subsequent prompt while closing out the current one.

## Debug-only testing hub

**Prompt 9.1 update:** Open the separate **RIT Testing Hub** debug launcher. Its customer shortcuts include populated and empty Individual/Bulk carts, unified Orders, independent template creation/editing, and image-led offers. Seller shortcuts include the redesigned synthetic Sales Overview, illustrative forecast, promotions and empty/no-sample insights. Hub Reset clears the separate individual cart/demo-order store, bulk draft, account templates and other local fixtures. Individual and bulk drafts never merge. The sample customer order history is explicitly fictional; saving an individual demo order is local only. The notes below describe earlier milestones and should not be read as current route behavior where Prompt 9.1 supersedes them.

**Prompt 9 update:** The debug Hub now links directly to Customer Profile, Settings, Support, Notifications, Templates and optional RIT Stories. Its Reset action also clears the new in-memory customer account/support/template repository. Select **Demo Customer** for these routes; seller and administrator mock identities cannot enter customer-account screens. The customer profile can also be reached from the customer bottom bar after entering Home. Appearance is a device-local preference that survives an in-memory demo reset; use Settings → Appearance → System to restore follow-system mode. The six-tap logo shortcut remains removed.

**Prompt 8 update:** Open the dedicated **RIT Testing Hub** launcher in the debug APK. The temporary six-tap and Welcome long-press logo gestures have been removed now that normal mock-role and direct-link navigation reaches implemented customer, seller and administrator areas. The historical descriptions below document prior milestones; they are not current access instructions. Release still has no Testing Hub launcher or preview routes.

The debug APK exposes a second launcher, **RIT Testing Hub**. Open that launcher directly; the Welcome/Login logos no longer have developer gestures. Choose a mock identity before entering a protected preview. The hub provides direct links to implemented customer, seller and administrator screens, onboarding replay, explicit returns to Welcome/Login, and a reset that clears the mock login and local demo repositories. Screen-local Compose state is recreated when reopening a preview. All catalog and identity data shown there is explicitly sample data.

| Mock identity | Simulated scope |
| --- | --- |
| Demo Customer | Customer preview screens |
| North Counter Owner · demo | Full North Counter business-management capability set in debug only |
| North Counter Manager · demo | Owner-delegated request, catalog, availability, hours, promotion and team capabilities; no reports/profile by default |
| North Counter Staff · demo | View-only North Counter request/schedule fixture |
| North Counter Operations Staff · demo | A second staff configuration with manager-delegated request response and availability editing; no catalog/team authority |
| Demo Administrator | Platform administrator scope |

These scopes are only for presentation/testing and are never server authorization. Mock identity state is separate from future real authentication state. Debug customer routes require the Customer mock role. Seller routes require the current seller membership and a specific capability, with request lookup scoped to that seller. The administrator's Prompt 8 local UI is implemented, but its production workflow is not.

The release source set supplies an inert mock-access implementation: no identities, no active mock session and no permitted protected area. Its manifest does not include the testing launcher, and production navigation does not register preview destinations. Before production release, keep all shortcuts disabled and verify this boundary again alongside real authentication.

The six-tap shortcut removal condition was met in Prompt 8: the dedicated debug launcher and Hub links cover the implemented customer, seller, staff and administrator features. The logo gesture helpers and shortcut-specific tests were removed. Retain debug mock logins and the Hub until real authentication and role-based testing are established.

The current customer Get Started entry has a **debug-only** link from the unavailable email-OTP screen to Profile Setup. It is for form QA and does not verify an OTP, create a session, or open Customer Home. Profile Continue validates a full name and one Student/Faculty selection, then stops with a preview message; it never opens the old mode picker. The seller entry is informational and follows the PRD's administrator invitation rule; it grants no seller role. Shared Login never chooses a role. Release excludes the profile-preview link and all mock access.

## Repeatable checks

From the project root, with Android SDK configured:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

The connected suite checks role switching/reset; Home Rice → Snacks → All filtering, in-place headings/items and food-detail navigation; absence of Welcome/Login logo shortcuts; Prompt 4 search/filter, favorites, promotions and reorder preview interactions; Prompt 5 cart, seller switch, pickup exception and quote/payment preview interactions; Prompt 6 seller operations; Prompt 7 business management; Prompt 8 seller analytics/reporting and admin navigation; Prompt 9 profile/settings/support/Stories; and Prompt 9.1 unified carts/orders, independent templates, overview/forecast and offer preview. Run the second command only with an authorized Android device or emulator; otherwise report it as not run. Check the release APK manifest separately for absence of the debug launcher.

## Prompt 4 customer-discovery preview

The Testing Hub has direct links for Search, Filters, seeded and empty Favorites, demonstration Promotions, and empty/sample Quick Reorder. These links explicitly select Demo Customer so they can be opened without OTP. The seeded Favorites entry adds an item and seller to that mock customer's isolated demo storage; the empty entry clears those saves. The Hub reset clears both the mock session and all demo favorites. Saved items are local to this debug installation and are **not** account-synchronized.

The Home search field opens one dedicated Search screen. Home category chips still filter the Home list in place. Search combines query, advanced filters and sort over the same typed preview catalog. Promotion details are labeled demonstration content and have no redeem/checkout action. Quick Reorder defaults to honest no-history; its sample-history entry is a debug fixture with a disabled submission action and current catalog revalidation.

Before each production release, verify the release manifest lacks the Testing Hub launcher, release navigation has no debug destinations, and release mock repositories/fixtures remain inert. The temporary six-tap shortcut was removed in Prompt 8; do not restore it in release or use it as a production authentication path.

## Prompt 5 bulk preview

The Hub opens Main Canteen and North Counter carts, prefilled pickup and review, honest empty bulk requests, and distinct fictional negotiation, comparison, editable counterproposal, agreed quote, payment-blocked and expired scenarios. It selects Demo Customer automatically. Cart drafts are in-memory only; Hub reset clears them. North Counter's fictional minimum is ten combined units and Campus Pantry's is twelve, while the default is eight. The debug policy also has fictional weekly hours, a holiday and temporary closure so special pickup can be tested without claiming real seller schedules.

At the Prompt 5 milestone, no draft was sent and no seller response was received; no provider checkout ran. The Orders bottom tab opens an honest real-order empty state, with Quick Reorder still reachable there and through the Hub. Prompt 6 adds a separate debug-local submission/seller-response simulator below; no real request is transmitted. The temporary six-tap shortcut does not relax the release boundary.

## Prompt 6 coordinated seller/customer scenarios

The Hub now has direct seller overview, inbox, request detail, counteroffer, revision comparison, final approval, exceptional-pickup decision, schedule, history and lifecycle-fixture links. Select Owner or Manager to respond; Staff is view-only in this sample. Select Customer and open “Customer side of local scenarios” to inspect the same in-process records. The Hub reset restores deterministic seeded records and clears locally submitted demo requests. The customer review screen can create a **debug-local** request, which then appears in the seller-scoped inbox; this never sends a network request. Use a North Counter draft with at least ten combined units to exercise the North Counter mock seller role.

Every local action is version-bound. Seller counters create a new immutable revision with an updated line mix, unit prices and pickup; stale prior approvals are cleared. An exceptional pickup requires a separate seller decision for that exact version. Seller approval and customer approval of the same current version result only in “agreed, payment unverified”. No real checkout or payment verification is exposed. The single “verified-paid” record is an explicitly marked fixture, not a transition the mock actors can produce. These scenarios are memory-only and reset on process restart or Hub reset; they are not real messages, reservations, orders or authorization.

The existing Prompt 5 static quote previews remain separate fictional examples; use the new Prompt 6 coordinated scenario screens when testing an actual customer/seller sequence. The earlier “no draft is sent” statement above remains true for real services, but a valid draft can now be submitted to this isolated local simulator. The temporary six-tap shortcut stays because administrator and future seller-management feature access is incomplete; release retains inert simulator stubs and no debug routes.

Prompt 6 final check on 21 October 2026: 52 debug unit tests and 16 Moto G60 Compose UI tests passed; debug/release APK builds passed; each lint variant had 0 errors and 26 retained warnings. The unsigned release APK manifest had only the normal launcher. Screenshots in `docs/verification/prompt6/` cover dark and light seller screens. The final debug APK was reinstalled and the phone's original dark-mode setting restored. The suite did not perform TalkBack or a separate small-device/font-scaling pass.

## Prompt 7 seller business-management preview

Select the North Counter Owner in the Hub, then open Seller management or direct links for menu, new/edit item, availability, categories, hours, closures, minimum, employees/manager/staff permissions, promotions and business settings. Staff view-only and delegated Operations Staff are distinct resettable mock fixtures. The explicit unauthorized-action link takes a restricted identity to the management guard for denial testing. The Hub reset restores catalog, seller profile, policies, promotions, employees, mock role, bulk draft and local coordinated request fixtures.

Menu items and public profile images are transient picker content-URI previews, not uploads. A new food item starts unpublished; publish it before expecting it in customer discovery. Changing an existing item to unavailable keeps it visible but blocks requests unless visibility is changed to hidden. Hiding a category hides its products from browsing and new bulk requests; removing a populated category is refused. The weekly schedule, closed dates and bulk minimum feed the existing customer pickup/cart policy. The hours/closure editors show potentially conflicting demo request references but never rewrite quote snapshots or auto-accept special pickup. Seller promotions can be drafted, published and expired in the same local source the customer offers screen reads; no discount is applied to checkout.

All management mutations recheck the typed seller capability in the local repository, including subordinate permission changes; route visibility alone is not authorization. Effective staff permissions always intersect their supervising manager's current grants. This debug model cannot provision a real account and must be replaced by server enforcement. The release implementation returns no employees/capabilities and refuses every mutation; debug routes are registered only behind `BuildConfig.DEBUG`.

Prompt 7 final check on 22 October 2026: 75 debug unit tests and 20 Moto G60 Compose UI tests passed (four Prompt 7 UI tests); debug/release APK builds passed; both lint variants had 0 errors and 27 non-blocking warnings. Release manifest inspection found only the normal launcher and no Testing Hub activity. The final debug APK was reinstalled after instrumentation. `docs/verification/prompt7/` contains dark/light, 1.3× text-size and simulated 900×1920 compact-layout screenshots. The phone's original dark-mode, 1.0 text-size and 1080×2460 display-size settings were restored. TalkBack was not performed.

## Prompt 8 analytics and administrator preview

Select Owner for seller analytics, report configuration/history, seller support, notifications and payment status. Default Manager and Staff sample roles have no financial analytics or report grants; the owner may delegate `analytics.view` or `reports.export` within the existing hierarchy. The owner sample sees only North Counter's synthetic sales. Select Demo Administrator for platform overview, seller directory/setup, users, aggregate/seller analytics, escalations, moderation, settings and local audit. The admin is not a seller owner. Hub links include populated and empty seller analytics; the no-sample state is the truthful production design.

`DevelopmentInsights` contains four deterministic synthetic paid-sample rows entirely separate from bulk operations and its paid-looking display fixture. It groups by Asia/Kolkata date, filters seller and range, deduplicates sample order IDs, and derives gross, discounts, net, average, product/category and unique/returning-buyer aggregates. No real provider event, walk-in sale, unpaid quote or individual-order preview contributes. The seller trend chart groups daily, weekly or monthly points natively. Promotion conversion remains unavailable without event attribution. Forecasting always shows insufficient historical data; no model or confidence exists.

Report setup validates date and seller-item selection and may save a **local sample configuration** to exercise history, but never writes or downloads an XLSX. The five planned worksheets and future export boundary are shown. Seller support ticket creation, admin seller setup, ticket status/notes and moderation review are resettable local examples; no invitation, notification, refund or production account/content change is sent. Hub reset clears this state. Release `DevelopmentInsights` is inert and all its navigation is debug-only.

Prompt 8 final check on 22 October 2026: 93 debug unit tests and 23 Moto G60 Compose UI tests passed; debug and release APK builds passed; both lint variants had 0 errors and 27 non-blocking warnings. Release manifest inspection found only the normal launcher; the final debug APK was reinstalled. `docs/verification/prompt8/` contains dark and light screenshots, including 1.3× text-size admin views. The phone's original dark mode and 1.0 font scale were restored. TalkBack and a separate compact-display-size pass were not performed. See `docs/IMPLEMENTATION_STATUS.md` for the feature and security boundaries.

## Prompt 9.1 urgent-demo scenarios and data truth

Select Demo Customer in the Hub. A prepared Main Canteen item can be added to the **Individual** cart; packaged and secondary-seller items cannot. The **Bulk** cart is independent and continues to use one seller, its current seller-specific combined-unit minimum, availability and pickup policy. Quantity changes update totals. A conflicting seller choice presents an immediate Keep Current Cart / Clear Cart & Switch dialog. Cancel leaves the current bulk lines untouched; confirm clears only that bulk draft. Neither action modifies the individual cart.

The customer bottom bar now opens one Cart and one Orders destination. Cart switches modes without transferring products. Orders filters All/Individual/Bulk; its initial individual cards and bulk paid-looking record are labeled sample fixtures, and new individual cards are local demo saves rather than submitted orders. Bulk requests still come from the separate local negotiation simulator. Saving a named bulk template opens an independent seller/item/quantity editor and does not need an active cart or order minimum; using a template revalidates the current catalog and asks before replacing a different seller's bulk draft.

The synthetic analytics fixture now contains 99 unique sample sale rows over the trailing 30 Asia/Kolkata dates for four sellers. North Counter and main-canteen item mixes, quantities, gross/discount/net and sample buyer IDs are derived from those rows, not entered independently on dashboard cards. Today/7 Days/30 Days controls query the same rows as the graph and comparisons. Unpaid bulk requests and individual demo saves contribute **zero** to sales. The forecast projection is a visibly separate illustrative debug drawing, not a model or production prediction; `sample=false` and release continue to show no verified history. One seeded report-history card is a saved example configuration, **not** a generated XLSX. Customer offer cards and seller previews are presentation fixtures with no coupon redemption or automatic price change.

These screens use debug-only navigation and repositories. The release mock implementations refuse reads/writes and do not register the Hub, local orders, analytics demos or checkout bypasses.

Prompt 9.1 final check on 22 October 2026: 107 debug unit tests and all 31 Moto G60 Compose UI tests passed after updating two older navigation/layout assertions and one card-semantics assertion. Debug and release APK assembly and both lint variants passed; lint reported 0 errors and 28 non-blocking warnings per variant. The unsigned release APK manifest exposes only `MainActivity` as launcher and no `DebugTestingActivity`; the debug APK exposes both. The release mock-order/insight/authentication source-set implementations remain inert, and the six-tap shortcut is absent. The final debug APK was reinstalled on Moto G60 `ZD2226B68F`.

`docs/verification/prompt9_1/` contains dark/light phone captures of populated individual and bulk carts, seller-switch dialog, combined Orders, independent template editing, 30-day and Today seller analytics, illustrative forecast, seller promotions and customer offers. The device's original dark mode and 1.0 font scale were restored. TalkBack, compact-display and real network/payment/OTP workflows were not tested; the latter are intentionally not implemented.
