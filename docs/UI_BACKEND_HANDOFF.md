# UI → backend handoff (after Prompt 9)

This is a presentation-contract map, not an API specification or approval of unresolved workflows. The app remains an Android client with debug-only previews. Production identity, persistence, authorization, event delivery and payments do not yet exist.

## Existing boundaries and consumers

| UI area | Current typed model/provider | Production service seam and required truth |
|---|---|---|
| Welcome, OTP, registration | `AuthEntryIntent`, `AuthInput`, `OnboardingFlow`; unavailable OTP action | Verified OTP challenge, stable user ID, session restoration/revocation, institutional eligibility when approved |
| Customer Profile/Settings | `CustomerProfile`, `CustomerPreferences`, `CustomerAccountRepository`; `DevelopmentCustomerAccount` debug fixture; device-local `AppearancePreference` | Authenticated profile read/edit; self-reported affiliation distinguished from verified identity; consent and retention policy; secure data access/deletion workflow. Appearance can stay device-local. |
| Seller/employee mode | `AuthorizedIdentity`, `AppMode`, `ProtectedArea`, `SellerCapability`; `DevelopmentPreviewAccess`, `DevelopmentSellerBusiness` | Server-issued memberships/capabilities, owner→manager→staff delegation, revocation and seller-scoped query/write checks. UI guards are presentation hints only. |
| Catalog/discovery/search | `CustomerCatalogSource`, `PreviewCatalog`, `Discovery` and `SellerBusinessRepository` | Seller-scoped catalog, availability/publication, eligible modes, hours and current prices; authenticated media and stale/offline read cache |
| Favorites, promotions, templates | `FavoritesRepository`, `DevelopmentFavoritesRepository`, `DevelopmentEngagementData`, `CustomerAccountRepository` | User-scoped saves, catalog revalidation, scheduled promotions and approved pricing; optional behavioral events only after valid notice/consent |
| Bulk draft and quotes | `BulkDraftSession`, `BulkOperationsRepository`, `DevelopmentSellerOperations`, `DevelopmentBulkPreview` | Server-authoritative seller minimum/time policy; idempotent submission, immutable quote revisions, version-bound mutual approval/expiry/withdrawal and seller isolation |
| Payments | `paymentEligibility`, explicit blocked/pending presentation | Provider adapter and server-created intent from locked quote; authenticated callback verification, reconciliation and precise pending/failure handling. Client return cannot mark paid. |
| Notifications | `CustomerNotification` and local empty/sample UI; seller preference UI | Durable user/seller-scoped inbox and event-derived push/email attempts; order state independent of delivery |
| Support | `CustomerSupportTicket`, `CustomerIssueCategory`, `DevelopmentCustomerAccount` and `DevelopmentInsights` sample queues | Customer/role-scoped ticket access, category routing, messages/attachments, seller→admin escalation, audit, delivery; never edit verified payment truth through support |
| Analytics/forecast | `InsightModels`, `DevelopmentInsights` synthetic rows | Verified app-payment and order events only; seller/time-zone scoping, data minimization, freshness, real sufficient-history criteria. Walk-in demand is unseen. |
| Reports | `ReportSelection` and local sample configuration | Authorized seller XLSX generation with five sheets, reconciled paise amounts, formula-injection defense, secure download/share and access audit |
| Platform admin | `DevelopmentInsights` seller/user/setup/moderation/audit fixtures | Distinct platform authority, seller onboarding/owner invitation, account restrictions, content review, support and platform health with immutable audit. No silent seller payout control. |

## Mock and release boundary

The dedicated Hub, selectable identities and all `debug/...` routes are guarded by `BuildConfig.DEBUG`; `app/src/debug/` holds mutable sample providers while `app/src/release/` contains inert counterparts. Mock customer profile/tickets/templates do not substitute for an authenticated account. The temporary logo shortcut is gone. Before production release, verify the release manifest and route graph again and replace all client-only permission assumptions with server enforcement.

## Integration order suggested for Prompt 10+

1. Identity/session and server-side role/seller authorization contracts, including explicit errors and revocation.
2. Seller/catalog/profile data and read caching with stale markers; customer profiles and consent/preferences.
3. Seller-scoped bulk state machine with versioned quote and special-pickup validation; durable customer/seller history.
4. Provider-vetted payment adapter, merchant mapping, verified callbacks and reconciliation, then notification outbox and inbox.
5. Support routing and audited administration, followed by real analytics instrumentation and XLSX export. Forecasting remains insufficient-data until a validated model exists.

## Open decisions and integration risks

- Individual prepared-food acceptance, kitchen and handover are unapproved; keep the entry shell nontransactional.
- PhonePe gateway eligibility, independent merchant settlement, refund/dispute remedy and payment/expiry race policy must be validated externally before live money.
- Exact quote-expiry duration, bulk-minimum override floor, seller role defaults, special-pickup surcharges, billing/tax presentation, verified-identity wording and privacy notice/retention require decisions.
- Real seller interviews must validate low-friction request handling, status and permissions before launch.
- Cache authorization and revoked-role invalidation, quote concurrency, idempotency, notification deduplication and XLSX tenant isolation are high-risk seams.
- The themed artwork is concept art; game/IP and historical depiction/publication rights have not been cleared.

Do not infer endpoint names or a production schema from the current Compose route names. The PRD's suggested API/schema are illustrative, not binding contracts.
