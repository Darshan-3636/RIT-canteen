# TEAM_03_RESPONSE — Completed Contribution

## Identity and branch
- Teammate/person: TEAM_03
- Project repo URL (nonsecret): https://github.com/Darshan-3636/RIT-canteen
- Shared base SHA: `218f3fbffc11ee02e4180ca10d5161e73a624ea6`
- Feature branch: `feat/rit-seller-analytics-visual-ux`
- HEAD commit SHA: **see final Codex submission message**

## Scope completed
- Planned assignment: Seller Statistics, Charts & Reporting Visual UX — understandable seller insights, graphs, demo forecast and reports. Presentation only; financial calculation, demo data provider and backend untouched.
- Implemented screens/components:
  - **Sales Overview:** Today / 7 Days / 30 Days selector; revenue panel with a daily bar chart (₹0 baseline, every day in range shown, tap a bar for that day); one stat strip (Orders, Average order, Repeat buyers); best sellers with thumbnail, value, share bar and units; grouped "Explore your business" drill-down list.
  - **Sales trends:** existing period chips plus Daily / Weekly / Monthly toggle; bar chart per period; stat strip with best period; partial first/last week or month note.
  - **Items & categories:** Revenue / Units toggle; ranked items; category donut with legend (whole-number shares that always total 100%).
  - **Customers:** unique, repeat and one-time buyers; split bar with legend; one-line definition and privacy note.
  - **Promotions:** stat strip (active offers, discounts given this month) above the existing offer cards.
  - **Forecast:** existing "Illustrative Forecast — Demo Only" projection kept unchanged in its math; drawn legend and date labels placed under their actual points; next-week demand list. Release and the production-empty preview still show "Insufficient historical data".
  - **Report builder:** date presets (This month, Last 7 days, Last 30 days, Custom — Custom reveals the existing date fields); report type and item chips; five-sheet workbook checklist; one-line summary; save action and message unchanged.
  - **Report history:** readable date range, saved time and status pill.
  - New shared presentation components (`SellerAnalyticsCharts.kt`) and display-only helpers (`SellerAnalyticsFormat.kt`).
- Before/after visual changes:
  - Line chart with evenly spaced points (which hid days without sales) and ISO date labels → bar chart on a true time axis with ₹0 days, labelled gridlines and readable day/date labels that thin automatically on narrow screens.
  - Today previously drew a single dot → "Today so far" figure with a hint to choose a longer range.
  - Four identical KPI tiles with repeated "Demo records / Sample customers" captions → revenue panel plus one divided stat strip; the page keeps its single "DEMO DATA · not live sales" marker.
  - Trends, Items and Customers were stacks of text cards → charts, ranked bars, donut and split bar with legends.
  - Report builder required typing `YYYY-MM-DD` → presets; internal "Prompt 24" wording replaced with "XLSX export isn't available yet. It will use verified, seller-scoped sales."
  - Rupee amounts now use one formatter with Indian digit grouping (`₹1,23,456`) and paise only when present. The previous `NumberFormat`-based formatting grouped differently on the JVM than on Android, so it could not be unit-tested reliably.
- Features explicitly NOT implemented: real XLSX generation or download; any new metric or calculation (all figures come from the existing `calculateSales` result); a chart library or Gradle change; analytics instrumentation; trained forecasting; admin analytics; seller operations overview; Support / Notifications / Payment-status pages (in the same file, deliberately unchanged).

## Exact changes
| Relative file path | Created/edited/deleted | Why |
|---|---|---|
| `app/src/main/java/edu/msrit/canteen/ui/screens/SellerInsightScreens.kt` | Edited | Redesigned the eight analytics pages (Overview, Trends, Products, Customers, Promotions, Forecast, Report builder, Report history). Removed helpers only these pages used (`SalesChart`, `OverviewMetric`, `PerformanceBar`, `OverviewLink`, `money`). Support, ticket, notification and payment-status code is byte-identical to base. `SellerInsightScreen(...)` signature unchanged. |
| `app/src/main/java/edu/msrit/canteen/ui/components/SellerAnalyticsCharts.kt` | Created | Native Compose Canvas charts and analytics building blocks: range selector, revenue panel, stat strip, sales bar chart, ranked item row, drill-down list, card, donut, split bar, legends, toggle. Colours derive from existing theme tokens only. |
| `app/src/main/java/edu/msrit/canteen/ui/components/SellerAnalyticsFormat.kt` | Created | Display-only, unit-tested helpers: ₹ formatting, date ranges, gap-filling days with ₹0, axis maximum, label spacing, percentage shares, chart screen-reader summaries. |
| `app/src/test/java/edu/msrit/canteen/ui/components/SellerAnalyticsFormatTest.kt` | Created | 11 unit tests for the helpers. |
| `app/src/androidTest/java/edu/msrit/canteen/SellerAnalyticsVisualUiTest.kt` | Created | 10 Compose UI tests for the redesigned pages. |
| `docs/team_handoffs/TEAM_03_RESPONSE.md` | Created (permitted report exception) | This report. |

## Integration contract for project lead
| Existing owner-owned route/file | New composable/callback/state/fixture hook | Required lead action |
|---|---|---|
| `navigation/RitAppNavigation.kt` → `Route.SellerInsights` (`debug/seller/insights/{page}/{id}?sample=`) | None. `SellerInsightScreen(page, sellerId, identity, sample, ticketId, onBack, onNavigate, onTicket)` is unchanged; pages are reached exactly as before, behind the existing `DebugSellerGate` (`analytics.view` / `reports.export`). | None required. Changes are live through the existing Testing Hub links. |
| Seller Overview entry (Person 02's redesign) | Existing `onAnalytics` → `Route.sellerInsight(SellerInsightPage.OVERVIEW)` | When wiring Person 02's "Sales" card, keep pointing it at the existing analytics route. |
| `ui/theme/RitTheme.kt` (lead-owned) | `chartPalette()` in `SellerAnalyticsCharts.kt` builds series colours from accent tints and neutral tokens because the theme has one accent colour. | Optional: if dedicated chart colour tokens are added to the theme, replace the list in `chartPalette()`. |
- All `LEAD INTEGRATION NEEDED` items: none blocking. Optional items only (Person 02 entry point, theme chart tokens above).
- Requires a new shared dependency? NO.
- Any disputed file ownership? NO. `SellerInsightScreens.kt` also hosts seller Support/Notifications/Payment pages; those parts were not modified.

## Testing performed (real outputs only)
| Command/test | Pass/Fail/Not run | Counts/output or blocker |
|---|---|---|
| Gradle tests | Pass | `./gradlew :app:testDebugUnitTest` — 118 tests, 0 failures (107 existing + 11 new). |
| Assemble debug | Pass | `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL. |
| Assemble release | Pass | `./gradlew :app:assembleRelease` — BUILD SUCCESSFUL. The release APK (signed locally with the debug key for the emulator only) was installed and launched: it opens onboarding without a crash, exposes only the `MainActivity` launcher, `DebugTestingActivity` does not exist, and the package is not debuggable. All analytics routes are registered only inside `if (BuildConfig.DEBUG)` in `RitAppNavigation.kt`, so release users cannot reach any analytics demo data. |
| Lint | Pass (0 errors) | `./gradlew :app:lintDebug :app:lintRelease` — 0 errors, 29 warnings in each variant, none in changed files. Warnings: UnusedResources 13, GradleDependency 10, UseKtx 2, ModifierParameter 2, OldTargetApi 1, AndroidGradlePluginVersion 1 — all in existing files. |
| Compose UI tests | 39 pass / 2 fail (pre-existing) | `./gradlew :app:connectedDebugAndroidTest` — "Finished 41 tests on Pixel_8(AVD) - 16", 2 failures. The same 41 tests also ran via `adb shell am instrument` with identical results. New `SellerAnalyticsVisualUiTest` 10/10, `Prompt8InsightsAdminUiTest` 4/4, `Prompt91DemoUiTest` 4/4 (re-run after the final change). Failures: `CustomerDiscoveryUiTest.homeCategoriesFilterInlineAndDetailsStillOpen` (no node `home_category_rice`) and `Prompt4DiscoveryUiTest.searchMatchesAndCombinedFiltersResetWithoutLeavingSearch` (no node `search_clear_all`). Both fail identically on a clean build of the base SHA on the same emulator, so they are not caused by this branch (customer discovery, outside this scope). |
| Device/emulator if actually run | Pass | Android Emulator, Pixel 8 AVD, Android 16 (API 36), Google Play arm64-v8a image. No physical device. |
| Light/dark | Pass (visual) | Every redesigned page captured in both light and dark (Overview, Trends, Items & categories, Customers, Promotions, Forecast, Report builder, Report history). Two defects found and fixed: translucent unselected bars showed a brighter base strip; with a single monthly bar the partial-period note read "first and last month". |
| Larger font/compact UI | Pass (visual) | 1.3× font: Overview, Items, Report builder. 360 dp width (`wm size 945x2100`) combined with 1.3× font: Overview and Trends — axis labels thin automatically, stat labels wrap to two lines, no clipping. Emulator restored to default size and 1.0× afterwards. |
| Keyboard / insets (form) | Pass (limited) | Report builder → Custom: focused the "Through" field with the on-screen keyboard shown and typed `2026-10-20`; the summary updated live to "1–20 Oct". Deleting characters showed "Enter dates as YYYY-MM-DD". The AVD has a hardware keyboard, so Gboard showed its compact floating toolbar, not a full-height keyboard. The custom date fields sit in the upper part of their pages, but behaviour under a full-height keyboard was not observed. |
| TalkBack | Not run | No TalkBack traversal performed. Every chart exposes a one-sentence `contentDescription` summary; UI tests verify it for the sales bar charts and the category donut (the split bar and forecast descriptions are set in code but not asserted). Chips and toggles are ≥48 dp high and drill-down rows ≥60 dp. |
- Accessibility concerns: TalkBack traversal unverified (needs a person listening; not something automation here can judge). Chart detail for individual bars is available visually by tap; screen readers get the summary sentence (total, highest and lowest period) rather than every bar.
- Screenshots/video file paths (only if actually captured): captured to a local scratch folder for review only; not committed (screenshots belong in the git-ignored `docs/verification/`).

## Security and scope attestation
- [x] Never modified backend, database, payments or production OTP
- [x] Never modified central navigation, shared business state, app release/manifest/Gradle or another team's files
- [x] All edited paths checked against approved allowlist
- [x] Synthetic examples labeled and isolated
- [x] No real secret or real payment/verification claim
- [x] No push/merge to main or force push
- [x] `git diff --check` clean (or disclose exception)

## Known issues and limitations
- Incomplete UI details:
  - The Testing Hub's mock seller roles all belong to North Counter, so screenshots and UI tests exercise that seller only (it has sales every day). Gap-filling for sellers with sparse sales (e.g. Coffee Corner) is covered by unit tests, not on-device views.
  - Best-seller thumbnails show the catalog placeholder for North Counter items because they have no catalog photos.
  - Weekly and monthly groupings can include partial first/last periods; the page says so rather than trimming them.
  - The illustrative forecast chart has no y-axis values by design (it is a shape illustration, not a prediction).
- Known regressions: none observed. The two failing UI tests above also fail on the base SHA.
- Backend-dependent work untouched: verified sales events, seller-scoped analytics API, real XLSX generation and secure download, offer attribution, trained forecasting.
- Needed owner decisions:
  - "Repeat buyers" counts buyers with 2+ orders in the selected period, the only figure the existing calculation provides. PRD 12.1 "returning customers" (based on full history) needs a backend definition.
  - Whether to add dedicated chart colour tokens to the theme.
  - Average order value is shown to the paise (e.g. ₹226.81); confirm whether KPI tiles should round to whole rupees.

## Submission
- Feature-branch URL (fill in final submission message if not available before push): https://github.com/Darshan-3636/RIT-canteen/tree/feat/rit-seller-analytics-visual-ux (after push)
- Owner merge checklist:
  - Confirm only the five allowlisted paths plus this report are in the commit.
  - Open Testing Hub → Seller owner → Seller analytics and each analytics link in light and dark.
  - Run `:app:testDebugUnitTest`, both assembles, both lints, and the UI suite on the integration branch.
  - Separately review the two pre-existing customer-discovery UI test failures.
- STOPPED before any owner integration or Prompt 10 work: YES
