# Finalized asset map — RIT Canteen

## Prompt 9 customer completion references

Batch 5 `38_my_orders`–`39_order_detail` were checked against the existing shared demo request/detail flow; they do not authorize labeling an unpaid request as a paid order. `40_notifications`, `41_help_support`, `42_report_problem`, `43_support_conversation`, `44_my_profile`, `45_edit_profile`, `46_settings`, `47_appearance_preferences` and `48_account_management` guided the newly reachable customer account UI. `special_offline` guides the honest online-only transaction boundary. These are native Compose screens, not embedded screenshots. The generated references' verified email, paid transaction, delivered ticket and live notification claims were deliberately omitted.

The optional RIT Stories gallery reuses the ten catalogued game/education theme resources described below. Detail views use `ContentScale.Fit`, so legacy embedded HUD/text is visible rather than masked. CODM, Aristotle, Newton, Stoic and Einstein remain explicitly marked legacy reference art; all game-inspired and portrait depictions still require publication/licensing review. The original `finalized assets/` directory was not modified.

## Prompt 8 seller analytics and administrator references

Batch 4 `72_seller_analytics_overview`–`79_excel_reports_downloads` guided seller KPI hierarchy, trend/chart spacing, product/customer/promotion report sections, forecast empty state and five-worksheet report setup. `80_seller_support_inbox`–`82_seller_notification_settings` guided seller help, payment-status and preference cards. `83_admin_dashboard`–`94_admin_audit_system_health` guided the separate platform overview, seller/user directories, admin analytics, escalations, content review, settings and audit hierarchy. Batch 5 dark admin/seller references supplied palette guidance. All were inspected as visual references; no screenshot was embedded or modified.

Generated reference revenue, paid-order counts, merchant verification, ready-to-download workbooks and trained forecasts are **not** product facts. Native Compose screens use a labeled synthetic sale fixture or genuine empty state, disabled real export/provider actions, and resettable local administrative review examples. Platform-admin settings do not expose unapproved financial controls.

## Prompt 6 seller-operation reference mapping

| Native debug-preview screen | Finalized visual reference | Functional adaptation |
|---|---|---|
| Seller overview | `finalized assets/batch 3/49_seller_overview.png`; `finalized assets/batch 5/dark_seller_overview.png` | Compact action/waiting/unpaid/upcoming counts only from marked demo records; fictional availability is not a live open claim. Revenue and sales analytics are omitted. |
| Incoming request inbox and detail | `batch 3/50_bulk_requests_inbox.png`, `51_bulk_request_detail.png` | Seller-scoped status/search filters and one complete request view with original and latest terms, timeline and guarded actions. |
| Counteroffer, comparison and approval | `batch 3/52_create_counteroffer.png`, `53_negotiation_history.png`, `54_awaiting_payment.png` | Native item/quantity/unit-price and date/time editing, immutable local revisions, explicit special-timing decision, same-version seller/customer approvals and honest unpaid state. |
| Upcoming schedule and history | `batch 3/55_scheduled_bulk_orders.png`, `56_scheduled_bulk_detail.png`, `57_order_search_history.png` | Date grouping, lifecycle status and request detail navigation; only the specially labeled paid fixture looks verified, with no fabricated real sale. |

These files were inspected as visual guidance. Their screenshot text, metrics, checkout controls and unapproved individual-order fulfillment buttons were not copied. No original reference image was modified and no new raster seller asset was required.

All paths below are relative to the repository root. The original `finalized assets/` directory is preserved unchanged. Its full inventory is 113 PNG reference images, four ZIP bundles of duplicate batch exports, two README files, and one extensionless WebP logo.

| Source group | Inspected contents | Prompt 1 use |
|---|---|---|
| `finalized assets/batch 1/` | Ten light portrait mockups: customer home, welcome, OTP, profile setup, entry choice, an alternate home, menu, sellers, item detail, and search | Welcome and home composition; envelope motif and food photography |
| `finalized assets/batch 2/` | Seventeen bulk flow screens (`21`–`37`), three flow overview images, ZIP | Prompt 5 bulk visual guidance; native debug-only local draft/quote previews, not backend ordering |
| `finalized assets/batch 3/` | Twenty-four seller screens (`49`–`72`), ZIP | Shared seller visual language and future seller mode |
| `finalized assets/batch 4/` | Twenty-three seller analytics/admin screens (`72`–`94`), README, ZIP | Shared admin visual language and future admin mode |
| `finalized assets/batch 5/` | Thirty-six PNGs: campus, five game-inspired onboarding pages, five educational pages plus signup variant, customer screens `38`–`48`, four dark screens, nine special states; README and ZIP | Loading/dark references and clean campus photographic crop; themed onboarding deferred to Prompt 2 |
| `finalized assets/ramaiah logo` | Transparent 330×430 WebP logo | Copied without redesign to `app/src/main/res/drawable-nodpi/rit_logo.png` |

## First-screen reference mapping

| App screen | Primary visual reference | Notes |
|---|---|---|
| Splash/loading | `batch 5/special_loading.png` and `batch 5/01_onboarding_rit_photo.png` | No one file contains the requested loading-plus-campus composition; Compose combines the two visual cues. |
| Welcome/landing | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_41 AM-1.png` | Logo, food hero and color treatment remain; current flow uses Get Started, a compact seller invitation card, and shared Login. |
| Customer home | `batch 1/ChatGPT Image Oct 20, 2026, 01_37_46 AM-5.png` | `02_06_53 AM-6.png` is a close alternate. Sample ratings and prices are omitted. |
| Customer home dark | `batch 5/dark_customer_home.png` | Defines deep dark canvas, charcoal cards, muted copy, orange action treatment. Its content arrangement differs from the light home. |
| Email sign-in entry | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_46 AM-3.png` and `batch 5/11_educational_signup_variant.png` | Envelope motif and verified email wording used; mobile OTP copy and educational image are excluded. |
| Future seller/admin shells | `batch 3/49_seller_overview.png`, `batch 4/83_admin_dashboard.png`, `batch 5/dark_seller_overview.png`, `batch 5/dark_admin_overview.png` | Inspected for shared tokens only; no seller/admin screen is exposed in Prompt 1. |

## Derived Android image resources

All these resources are separate copies or crops of approved originals. The crop removes mockup text/controls so Compose draws interactive content itself.

| Resource | Original and transformation |
|---|---|
| `rit_logo.png` | Lossless conversion from `finalized assets/ramaiah logo`. |
| `rit_campus.webp` | `batch 5/01_onboarding_rit_photo.png`, crop `1290x1560+0+0`; excludes the lower baked onboarding headline and button. |
| `rit_welcome_food.webp` | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_41 AM-1.png`, crop `941x435+0+960`; food photography only. |
| `rit_food_dosa.webp` | Light home reference `01_37_46 AM-5.png`, crop `200x170+31+1345`. |
| `rit_food_biryani.webp` | Same home reference, crop `200x170+324+1345`. |
| `rit_food_coffee.webp` | Same home reference, crop `200x170+595+1345`. |

The exported ZIPs repeat standalone images and are not application resources. Batch 1 has near-duplicate home compositions rather than an exact byte duplicate. Batch 3 and Batch 4 both contain a file numbered `72`, but they depict different seller audit and seller analytics screens. The Batch 5 dark customer home is a distinct layout variant. Batch 5 README states all sample values and identities are fictional.

## Prompt 2.1 onboarding and account-entry mapping

The mandatory flow remains exactly three feature slides. Product copy lives in `OnboardingManifest.content`; the independent `themeAssignments` map now associates Order Food → Minecraft, Bulk Orders → Valorant, and Explore Sellers → Among Us. Each scene visibly demonstrates the feature. The other seven themed concepts remain in `ThemeCatalog`, with all ten accessible in the debug-only RIT Stories gallery. Five older theme crops are marked reference-only; they must not be used as production full-screen backgrounds until cleaned and reviewed.

| Production resource | Source/reference | Transformation |
|---|---|---|
| `onboarding_minecraft_shader.webp` | `batch 5/02_onboarding_minecraft.png`, user's alternate-building reference image, first food-selection edit | Voxel canteen food-selection scene with student/seller interaction and rectangular blue-window RIT facade, re-lit with subtle shaders and less gloss. |
| `onboarding_valorant_game.webp` | `batch 5/03_onboarding_valorant.png`, RIT campus reference, first group-request edit | Muted matte in-game-style group request discussion, pictured platter choices, many portions and carried trays. |
| `onboarding_among_us_map.webp` | Among Us gameplay visual direction; Batch 5 concept retained as reference | New top-down 2D food-court map with connected dosa, juice and packaged-sweets seller rooms and exploring crewmate sprites. |

All three selected story-led outputs were inspected visually, then converted mechanically to WebP at quality 86. The realistic Among Us edit and subsequent outdoor flat poster were rejected; the selected map follows the user's request for the game's own top-down visual language. Glossy Valorant and Minecraft variants were replaced with more restrained in-game/shader treatments. Earlier clean Minecraft/Turing/PUBG variants are retained as non-selected references. No source file in `finalized assets/` was overwritten. Reconstruction is concept art, not a verified depiction of the real campus; verify architectural accuracy and IP clearance before release.

Batch 5 includes a separate RIT campus photo and ten themed concepts: five game-inspired and five educational. Older resource crops (`onboarding_rit.webp`, `onboarding_minecraft.webp`, `onboarding_valorant.webp`, `onboarding_pubg.webp`, `onboarding_among_us.webp`, `onboarding_codm.webp`, `onboarding_aristotle.webp`, `onboarding_newton.webp`, `onboarding_turing.webp`, `onboarding_stoic.webp`, `onboarding_einstein.webp`) remain in `app/src/main/res/drawable-nodpi/` for provenance and reference. Only the ten game/education themes are catalogued; the separate RIT campus photo is used as the splash crop.

The older WebP files were cropped and downsampled from the reference mockups in Prompt 2. They are now retained solely for the debug catalog and legacy reference. They must not be represented as clean production assets: CODM retains HUD markings, and the old Turing crop retains printed flowchart text. `onboarding_minecraft_shader.webp`, `onboarding_valorant_game.webp`, and `onboarding_among_us_map.webp` supply mandatory onboarding artwork; prior `*_clean.webp` and rejected `*_story.webp` variants remain non-selected resources.

| Prompt 2 screen | Primary finalized reference | Adaptation |
|---|---|---|
| Email entry | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_46 AM-3.png`; `batch 5/11_educational_signup_variant.png` | Keeps existing envelope/form design and email wording. Batch 1 incorrectly says mobile OTP, so the app states email-only. |
| OTP preview | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_46 AM-3.png` | Six native cells backed by a single numeric input and the Android keyboard; no fake number pad or sent-code countdown. |
| Profile setup preview | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_49 AM-4.png` | Full name, optional department/photo, self-declared student/faculty cards. |
| Mode selector preview | `batch 1/ChatGPT Image Oct 20, 2026, 02_06_51 AM-5.png` | Two approved visual cards; neither grants permissions. The reference's unqualified ordering promise remains non-functional. |

The source is concept art, not clearance for public distribution. Batch 5 itself calls for licensing, historical/portrait accuracy, and production review. Game-inspired character/HUD imagery may implicate third-party IP. The educational wordplay is not presented as authentic historical quotation. A publication decision and replacement/clearance review remain required before shipping these assets.

## Prompt 3 customer-discovery imagery

The light Home reference (`batch 1/01_37_46 AM-5.png`, alternate `02_06_53 AM-6.png`), menu (`02_06_55 AM-7.png`), seller directory (`02_06_57 AM-8.png`), food detail (`02_06_59 AM-9.png`) and search/category visual (`02_07_02 AM-10.png`) guided hierarchy, card proportions, chips, food-photo placement and navigation. The dark customer Home in Batch 5 guided the near-black palette. Generated UI text, mock ratings, open badges, Add/Cart controls and unverified seller photos were **not** carried into functional screens.

Three new standalone illustrative catalog photographs were generated with the built-in image tool, inspected, and resized to WebP. They are not crop reconstructions of UI screenshots:

| Resource | Subject and fixture use |
|---|---|
| `app/src/main/res/drawable-nodpi/catalog_dosa.webp` | Masala dosa with sambar/chutney; Home feature and item cards/details. |
| `app/src/main/res/drawable-nodpi/catalog_biryani.webp` | Vegetarian biryani; matching fixture item only. |
| `app/src/main/res/drawable-nodpi/catalog_coffee.webp` | Filter coffee; matching fixture items only. |

Generation prompts specified photorealistic editorial food photography, natural canteen lighting, horizontal 4:3 framing and no UI, text, logo, watermark or people. Other foods and unverified seller identities use native neutral placeholders. `rit_campus.webp` and the older `rit_food_*.webp` remain historical prior-stage resources, but are not consumed by Prompt 3 catalog screens because they were cropped from composite UI references. Seller imagery, a verified clean campus photo, true prices and actual menu content still require sourcing and approval.

## Prompt 4 search and engagement references

The Batch 1 search/category image (`ChatGPT Image Oct 20, 2026, 02_07_02 AM-10.png`) guides the clean search field, category chips and result-card hierarchy; Batch 5 `dark_customer_home.png` guides deep dark surfaces and contrast. Batch 5 `special_no_results.png` informs the spacious empty states without copying unverified text or functionality. Search, Favorites, Promotions and Quick Reorder reuse the three approved standalone catalog food photos where the fixture item matches. No new artwork or original finalized asset was changed for Prompt 4. Offer banners are native text/cards, not fictional seller photography; promotion and historical-order content is explicitly debug demonstration data.

## Prompt 5 bulk-order references

Batch 2 `21_bulk_order_cart`, `22_bulk_minimum_validation`, `23_pickup_scheduling`, `24_special_pickup_request` and `25_bulk_request_review` informed the seller-scoped cart, combined-unit minimum, date/time selection, special-request warning and local draft review. Batch 2 `27_bulk_negotiation_chat`, `28_counteroffer_comparison`, `29_customer_counteroffer`, `30_final_agreed_quote`, `31_payment_request`, `32_payment_verification` and `35_bulk_order_history` informed the versioned timeline, comparison, counterproposal, agreement, payment-gate and empty/history structure. There is no standalone `26_bulk_request_submitted` asset in the inspected reference tree; the app does not manufacture a submission success state.

Batch 5 `dark_bulk_negotiation` and the special-state references for empty orders, unavailable item, offline, pending/failed payment and expired quote informed tone and honest state treatment. Prompt 5 reuses native components and approved catalog photographs; it does not copy screenshot controls, fake seller messages, payments, success claims or original reference files. All quote scenarios and scheduling exceptions are explicit debug-only fixtures.

## Prompt 7 seller business-management references

Batch 3 `59_menu_management`, `60_add_edit_food_item`, `61_categories_tags`, `62_availability_publishing`, `63_promotions_dashboard`, `64_create_edit_promotion`, `65_featured_items_banners`, `66_seller_business_profile`, `67_working_hours_closures`, `68_bulk_order_configuration`, `69_employee_directory`, `70_invite_employee` and `71_permission_management` were inspected as seller-management guidance. Batch 5 `46_settings` informed the restrained settings-card hierarchy. The app uses native Compose text, chips, fields and cards rather than screenshot crops. In particular, the reference's real-invitation language, merchant/payment controls, order metrics, inventory implications and publication/audit claims were not treated as functional specifications. Item and seller images selected through Android's photo picker are explicitly local debug previews, not production assets. No original finalized reference file was modified.
