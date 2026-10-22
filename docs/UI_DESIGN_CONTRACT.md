# RIT Canteen UI design contract — Prompts 2.1–9

The approved images in `finalized assets` control the visual treatment. The PRD controls behavior when sample copy or controls in an image conflict with product rules. This contract records the current native Compose translation; later screens should be checked against their corresponding approved image before implementation.

## Primary references

- Light welcome: `finalized assets/batch 1/ChatGPT Image Oct 20, 2026, 02_06_41 AM-1.png`.
- Light customer home: `finalized assets/batch 1/ChatGPT Image Oct 20, 2026, 01_37_46 AM-5.png`; `02_06_53 AM-6.png` is a close alternate.
- Deep dark customer treatment: `finalized assets/batch 5/dark_customer_home.png`.
- Light loading treatment: `finalized assets/batch 5/special_loading.png`.
- Seller and admin visual language: `finalized assets/batch 3/49_seller_overview.png` and `finalized assets/batch 4/83_admin_dashboard.png`; dark variants are in Batch 5.
- Branding: `finalized assets/ramaiah logo` (transparent WebP despite its extensionless name).

## Extracted working tokens

These are implementation approximations sampled visually from the mockups, centralized in `RitTheme.kt` so they can be refined through device comparison.

| Token | Light | Dark |
|---|---|---|
| Canvas | `#FFFFFF` | `#090B0F` |
| Primary text | `#101622` | `#F7F8FA` |
| Secondary text | `#69717E` | `#9DA5B0` |
| Accent | `#FF5A2D` | `#FF6343` |
| Warm card | `#FFF5EF` | `#211C1B` |
| Standard card | `#FFFFFF` | `#191E25` |
| Outline | `#E2E4E8` | `#303640` |

- Type: bold, geometric-looking sans hierarchy; 32 sp display, 24 sp section heading, 18 sp card/title, 15 sp body, 12 sp auxiliary copy. Compact quick-entry cards use 12 sp bold titles and 10 sp captions. No font file was supplied, so the current app uses Android's sans serif as a provisional substitute.
- Spacing: 4, 8, 12, 16, 24, 32 dp scale; main horizontal gutters around 20–24 dp.
- Corners: 12 dp small cards, 20 dp action cards, 24 dp imagery cards, pill buttons and search.
- Icons: simple rounded line weight, orange active/white on accent; native Canvas icons are used instead of emoji.
- Buttons: orange filled primary, outlined secondary, minimum 48 dp touch area. No additional decorative gradients or glass effects.
- Images: onboarding uses three reconstructed clean artwork-only resources; all text, pagination, and controls are native Compose. The source mockups remain untouched. Other older crops are debug reference thumbnails, not production full-screen backgrounds.
- Navigation: customer four-item bottom bar follows the approved Home / Orders / Favourites / Profile arrangement. Actual role access is determined by authenticated permissions later.

## Consistency and product rules

- Keep the light composition white and spacious; use deep near-black backgrounds and charcoal surfaces in dark mode. Do not use Android dynamic color to replace the RIT accent.
- Preserve the official transparent logo without redrawing its geometry.
- Allow native status/navigation insets, scrolling, keyboard movement, back behavior, screen-reader labels, and scalable text to alter exact screenshot pixels when needed.
- Remove sample ratings, review counts, seller inventories, sales values, or success states from preview content. The reference artwork is illustrative, not evidence of operating data.
- Use email OTP copy. The Batch 1 OTP image incorrectly refers to a registered mobile number.
- The main-canteen individual-order action remains unavailable until its operational workflow is approved. Other sellers and packaged goods are bulk-only.
- Do not add game or educational artwork to ordinary operational screens. The themed illustrations belong to the three onboarding features and the debug-only RIT Stories preview.
- Seller and admin screens should inherit this palette, logo, spacing, card treatment, and role-scoped navigation while following their own approved layouts; Prompt 1 does not implement them.

## Current visual deviations and device checks

- The exact typeface is unavailable; Android sans serif is used.
- The splash screen combines the clean loading reference with the approved campus photograph because there is no single supplied splash mockup with both.
- The Login entry uses the Batch 1 envelope motif and Batch 5 email wording without its educational artwork. The Send email OTP action reports service unavailability; no code is sent.
- The debug customer preview has a small required preview label and omits the reference's invented ratings and prices.
- Dark mode applies the Batch 5 near-black surfaces and accent to the shared home structure. The Batch 5 dark reference uses a different arrangement of entry cards; a device comparison is still needed before claiming exact fidelity.
- The mandatory feature slides remain Order Food / Bulk Orders / Explore Sellers. Minecraft shows students selecting food at a voxel canteen against the user's alternate rectangular RIT facade, with subdued shader lighting rather than heavy gloss; Valorant uses muted, matte gameplay-like rendering for a group and seller planning a large request with repeated trays; and Among Us shows a top-down in-game-style food-court map with crewmates exploring three connected seller rooms. Feature copy remains unchanged, independent of theme assignments, and makes no unsupported checkout/fulfillment promise.
- Onboarding shows artwork in an upper panel and native copy/actions on a lower surface, so no UI fragments are hidden by crops or blur. Skip and Back remain native; a three-part progress indicator and a native Next/Get Started action replace the original mockup controls.
- Welcome has exactly three visually distinct entry options: primary Get Started for Student/Faculty, a compact seller invitation card, and a lower-emphasis shared Login link. The three food-discovery quick-entry cards remain confined to customer Home. The temporary debug logo gestures were removed in Prompt 8; the separate debug Testing Hub launcher remains, and the Welcome/Login logos are non-interactive.
- Both email entry intents reach the same email-OTP boundary, with separate headings. No send success, verification, session, or role is fabricated.
- The edge-to-edge root paints the full window behind system-bar insets. Day/night launch themes and system-bar icon contrast follow the app palette; bars are not hidden.
- Dark-mode screenshots on a Moto G60 verified Welcome, the debug panel, both email-entry intentions, and the final three story-led artwork slides. The earlier pale Turing status-icon contrast defect was fixed before the final theme selection. Light mode, keyboard, small-screen and accessibility checks remain.
- Native font substitution, aspect-ratio-aware illustration framing, reduced-motion transitions, accessible hit areas, and small-screen wrapping can differ from fixed 1290×2340 mockups. The debug OTP preview uses the actual Android keyboard rather than drawing the mockup keypad.

## Prompt 3 catalog patterns

- Customer discovery reuses 20 dp gutters, warm rounded entry cards, near-black/white surfaces, RIT coral accents, native Canvas icons, LazyColumn/LazyRow with stable keys, a four-item bottom bar and a single Compose `NavHost`. The Home presents the three actions before imagery and sample content; search is explicitly scoped to local preview data.
- Home category chips filter the food cards directly below on the same screen. One selected-category state drives the highlighted chip, results heading and items; All restores the full fixture list. No separate category page or second selector is used for this interaction.
- Menus have a native seller heading, honest preview status, local search, data-driven category chips and one reusable item card with photograph or neutral placeholder, seller/dietary line, price and eligibility. Full seller menus have no item-count limit. Unavailable published items can show an explicit label or be hidden by fixture policy.
- Seller cards and customer profiles show capability pills and explicit unknown hours/live status instead of invented open badges or ratings. The Main Canteen is visually distinguished by its individual-prepared-food-*planned* capability. Missing seller photos are placeholders, not fictional photography.
- Food details enlarge the matching standalone food image, identify seller, prepared/packaged classification, dietary data and applicable modes, and end in an always-disabled ordering action. Prompt 4 adds a separate debug-preview favorite control; no quantity selector, cart or checkout is active.
- Reference deviations are intentional: the generated menu/details mockups show ratings and enabled Add-to-Cart controls, while the app has no verified ratings or approved individual-order workflow. Directory reference photos and opening-hours claims are omitted pending real seller assets/data. Native layout and current-system font scaling supersede exact screenshot dimensions.
- Moto G60 dark and light screenshots in `docs/verification/prompt3/` show clean system bars and no observed clipping at current device settings. Large-font, TalkBack and compact-device review remain to be done.

## Prompt 4 discovery and engagement patterns

- The approved search visual becomes one focused Search destination reached from Home. Home's selected category remains inline on Home and is passed as an initial Search filter only when the user opens Search; Search has its own saved query/filter/sort state. A horizontally scrolling chip row exposes combinable filters and active-filter removal without inventing ratings or popularity.
- Food and seller favorite controls reuse the catalog cards and use 48 dp touch targets. The Favourites destination has honest empty and saved states, with visible debug-local/no-sync framing rather than a fictional customer account.
- Promotions use restrained coral-accent cards and explicit demonstration, validity and seller labels. Home contains only a small offers entry. No false discount total, coupon redemption or personalized promotion claim is displayed.
- Quick Reorder uses the same rounded-card language with a no-history state by default and a separate debug sample. Historical prices are distinguished from current preview prices; missing/unavailable items and ordering eligibility are not silently treated as reorderable. The submit action stays disabled.

## Prompt 5 bulk-order preview patterns

- Keep a single seller-scoped draft cart. Quantity controls count combined units across prepared and packaged products from that seller, with the seller's own minimum shown beside a provisional catalog estimate. Switching sellers with lines requires an explicit clear-and-switch action; another seller's products never count toward the minimum.
- Pickup uses native date/time pickers and quick choices in Asia/Kolkata. Weekly hours, holiday and temporary closure examples are prominently labeled fictional demo policy, never live availability. Out-of-hours pickup is an explicit special request with a reason and seller-approval warning, not an implicitly bookable slot.
- Review keeps editing links and exact quantities, pickup, instructions and estimate visible. Real submission remains disabled because there is no authenticated order service. Prompt 6 adds an explicitly labeled debug-local submission into the same-device scenario simulator; the older static quote timeline remains a separate fictional example.
- Negotiation displays immutable revision snapshots and a side-by-side change explanation for items, quantities, pickup, special status and amount. Counterproposal editing saves only local UI state. The final quote and payment-eligibility views show exact-version agreement and expiry conditions but block acceptance and payment until authoritative server confirmation.
- Batch 2 bulk mockups guide coral accents, rounded cards and section hierarchy; generated sample order numbers, seller responses, payment states and success copy are not treated as operational facts. Empty orders and expired quotes use honest native states. Preserve light/dark tokens, native system bars and scrollable content.

## Prompt 6 seller operations patterns

- Batch 3 `49`–`57` and Batch 5 dark seller overview guide the compact seller hierarchy: identity/availability notice, actionable count cards, status-filtered request rows, complete detail, inline timeline, counteroffer form, date-grouped schedule and searchable history. Keep the existing light/dark tokens and native Compose components; no screenshot is embedded as a screen.
- Request, proposed quote, agreed-unpaid quote and verified-paid fixture use distinct labels. A requested pickup time is not a reservation. A seller-approved quote is not yet a mutual agreement; a mutually approved quote is not paid. Detailed sales/revenue widgets in reference art are excluded.
- The counteroffer editor displays seller-local eligible items, quantities, quoted unit prices, native pickup pickers, exceptional timing reason, previous-versus-proposed terms and an explicit debug-only action. Approval cards expose the exact revision and the separate exceptional-pickup decision. Transactional instructions contain no game art or jokes.
- Owner and Manager see response controls when the mock capability permits; Staff sees a view-only explanation in the current sample. Seller membership and capability determine visible routes, but server authorization must replace this preview before release. The six-tap Hub shortcut remains temporarily while administration/management UI is incomplete.

## Prompt 7 seller business-management patterns

- Use the same clean header, 20 dp gutters, coral primary action, rounded outlined cards and light/deep-dark tokens for menu, categories, availability, hours, closures, minimum, employees, promotions and settings. Batch 3 `59`–`71` determine layout hierarchy only; fields and actions follow the PRD. The menu uses a search field, horizontally scrollable category selection and a lazy item list so large catalogs remain navigable.
- Keep item availability, unavailable-item visibility and publication visibly distinct. A new item is a draft until published. Prepared/packaged eligibility is a non-editable platform rule. Photo selection shows a local preview and an explicit no-upload note. Category removal must not silently cascade into item deletion.
- Hours and holidays display Asia/Kolkata local dates/times and explicitly identify affected demo pickup requests without implying a revised quote or accepted special request. The seller-specific minimum is labeled as combined units across one seller.
- Team rows distinguish owner, manager, view-only staff and delegated staff. Permission chips only offer grants available from the supervisor; denied actions still fail in the local model. Invite/add language is explicitly debug-local, with no email or account creation. Promotions distinguish draft/upcoming/active/expired states without checkout savings claims. Payment settings present unverified provider status only, never merchant credentials or settlement controls.

## Prompt 8 analytics and platform administration patterns

- Batch 4 `72`–`82` guide restrained seller financial cards, hand-drawn native chart axes/points, filtered metric rows, workbook configuration and support/preference panels. Charts and metric cards are labeled **synthetic sample**, with an explicit no-verified-history state; no demo quote is financial evidence. Date controls use Asia/Kolkata local dates. Forecasting always says “Insufficient historical data.”
- Batch 4 `83`–`94` guide a distinct platform-admin header and grouped overview, seller/user directories, analytics, support detail, moderation, settings and audit views. Admin actions are local review or setup drafts only, not live account enforcement. Support payment complaints cannot change payment state.
- Keep native 20 dp gutters, clean white/deep-black surfaces, coral highlights and rounded cards. Seller and admin screens have descriptive section labels, keyboard-safe scrolling, non-decorative charts and explicit roles. The six-tap and long-press logo shortcuts are removed after direct Hub access covers all implemented roles; the separate debug launcher stays. Release logo taps have no privileged effect.

## Prompt 9 customer account, gallery and accessibility patterns

- Batch 5 `38`–`48` guide Orders, notifications, support, profile and settings hierarchy. Customer Profile now uses the shared 20 dp gutter, outlined 20 dp cards, native coral controls and four-tab bottom navigation. Profile edit identifies affiliation as self-reported and photographs as local preview selections. Mock-account changes are not verified identity updates.
- Appearance is a device-local Light/Dark/System setting applied through the root theme and system-bar icon contrast. Optional personalization and marketing are off by default; the UI does not start tracking or send notification deliveries.
- Customer support uses the same restrained status/card language as seller/admin support. New tickets, replies and escalations are local debug examples only; payment state is untouched. Empty notification, ticket, template and offline-boundary screens make their service limitations explicit.
- Optional RIT Stories is reachable from Customer Profile without entering onboarding. Gallery thumbnails and detail views show the ten approved themes without cropping away legacy HUD/text. Only the three selected onboarding artworks and other catalogued clean variants are marked clean; legacy references are visibly labeled as not production-ready. Publication/licensing review remains mandatory.
- Reused buttons and single-line fields now use minimum rather than fixed heights so larger system fonts can expand them. The customer bottom bar exposes selected-state semantics. Analytics charts already supply text summaries for TalkBack. Actual TalkBack traversal and compact-layout findings are recorded separately in `docs/UI_QA_REPORT.md`.
