# RIT Canteen backend preflight

Audit date: 2026-10-30. Scope: backend, PostgreSQL schema, API boundaries and existing backend tests. This report completes the preflight; its proposed changes are not implemented.

**Result:** the checked-out backend compiles and all **49 existing tests pass** on a new disposable local PostgreSQL cluster. The repository and that local database are at **V047**. The schema and applied Flyway version of Supabase project `tnsvkqrzwapykioogtdu` are **unverified: live comparison is blocked because no authorized read-only connection is configured**. These results permit local implementation planning, not a live migration or deployment.

## 1. Repository and access evidence

| Check | Observed result |
| --- | --- |
| Checkout | `C:\Projects\College\new_canteen_software\RIT-canteen` |
| Branch | `feat/rit-verify-complete-backend-integration` |
| HEAD | `322ca29d510c8daa6e2a04ea2a11680bfc5d4056` |
| Initial working tree | Clean; no existing uncommitted changes to overwrite. |
| Remotes | `origin`: `Darshan-3636/RIT-canteen`; `myfork`: `Srujanmp1366/RIT-canteen`. |
| Remote verification | Read-only GitHub branch listing confirmed the integration branch also points to `322ca29d510c8daa6e2a04ea2a11680bfc5d4056`; no fetch, checkout, merge or push was needed. |
| Recent integration history | `322ca29`: V047 upgrade rehearsal; `90a2b77`: UAT checkout/reconciliation; `83c7428`: demo payment/analytics; `8fa2fce`: synthetic historical data. |
| Supabase access | No Supabase/PostgreSQL connector, configured database environment variables, or local `.env` containing a connection was available; only `backend/.env.example` exists. No credentials were requested or inferred from the project reference. |
| Live schema comparison | **BLOCKED**: applied versions/checksums, additional tables, live constraints/indexes, RLS, grants, extensions and PostgreSQL version cannot be compared. No Supabase connection was attempted. |
| Deliverable changes | This report only. Backend source, existing migrations, PhonePe logic and Android files are unchanged. Local test data and build artifacts are ignored by Git. |

The reviewed GitHub snapshot is [commit 322ca29](https://github.com/Darshan-3636/RIT-canteen/commit/322ca29d510c8daa6e2a04ea2a11680bfc5d4056). Repository source and SQL take precedence over older handoffs: `FOUNDATION_HANDOFF.md` describes Phase A, while `ENGAGEMENT_ADMIN_API.md` still includes historical statements about missing login. The [rollout gate](../cloud-demo/CURRENT_RENDER_SUPABASE_ROLLOUT.md) and [UAT readiness report](PHONEPE_UAT_SANDBOX_READINESS.md) are historical evidence, not proof of today's deployed SHA or live schema.

If authorized read-only access is configured later, inspect metadata and `flyway_schema_history` through an explicit read-only transaction, with timeouts. Compare scripts, versions, checksums, successful/failed entries, constraints, indexes, trigger definitions, RLS and grants against this snapshot. Do not collect account records or payment tokens. **Do not use `BackendBootstrap.open()` for a live inspection:** it automatically runs Flyway and can seed data. `/ready` only proves a database answers `SELECT 1`.

## 2. Existing architecture and schema

The independent [backend build](../../backend/build.gradle.kts) uses Kotlin 2.3.21, Ktor 3.6.0, Flyway 13.10.3, PostgreSQL JDBC 42.7.14 and HikariCP 7.1.0. It does not depend on the root Android Gradle build.

| Component | Current responsibility and reusable boundary |
| --- | --- |
| [Application.kt](../../backend/src/main/kotlin/edu/msrit/canteen/backend/Application.kt) | Bootstrap, JSON/error handling, health/readiness, auth/profile routes; mounts Commerce and Engagement under `/api/v1`. Engagement mounts Platform routes. |
| [BackendContext](../../backend/src/main/kotlin/edu/msrit/canteen/backend/core/BackendContext.kt) | Shared data source, config, clock and live authorization; `transaction` commits or rolls back a JDBC connection. |
| [BackendConfig](../../backend/src/main/kotlin/edu/msrit/canteen/backend/core/BackendConfig.kt) / [Database](../../backend/src/main/kotlin/edu/msrit/canteen/backend/core/Database.kt) | Local/test/cloud-demo/production modes, explicit seed restrictions, redacted configuration, pool size 1–4, Flyway before HTTP, UTC connections. Seller calendar zone is `Asia/Kolkata`. |
| `auth/` | Local password accounts, PBKDF2 hashes, opaque 24-hour bearer sessions stored as SHA-256 hashes, seller capability inheritance, independent platform-admin check. Supabase Auth is not used by this code. |
| `features/commerce/` | Catalog SQL, ordering policy, bulk revisions/approvals, cart and bulk drafts, individual demo lifecycle, separate UAT gateway and attempts. Most operations are JDBC functions; several writes remain inside route handlers. |
| `features/engagement/` and `features/platform/` | Preferences, favorites, templates, support, inbox, analytics, audit reads and admin planning drafts. Routes directly execute SQL. Platform imports Engagement's SQL, error, pagination, audit and analytics helpers. |
| `features/integration/PostgresCommerceReadPort` | Reusable read adapter for visible catalog items, real stored request counts, explicitly simulated sales and UAT totals. No verified-revenue source. |

### Flyway inventory

All 16 files in [the migration directory](../../backend/src/main/resources/db/migration) were inspected. Local `flyway_schema_history` contains these versions, all successful:

`001, 002, 003, 004, 005, 020, 021, 022, 040, 041, 042, 043, 044, 045, 046, 047`.

| Version | Existing migration purpose |
| --- | --- |
| V001–V005 | Users; sellers; hierarchical memberships and trigger; capability grants; sessions. |
| V020 | Categories, seller category assignments, menu items, ordering policy, weekly hours and closures. |
| V021 | Bulk requests, quote revisions/lines, approvals, special-pickup decisions and request events. |
| V022 | Individual demo orders and immutable name/price snapshots on their lines. |
| V040 | Customer preferences, favorites and saved bulk templates. |
| V041 | Support tickets, messages, status history and in-app notification inbox. |
| V042 | Audit events, admin setup drafts and saved report selections. |
| V043 | Versioned individual cart and lines; nullable order idempotency fields for legacy compatibility. |
| V044 | Expands the demo-order status check; adds simulated payment outcome, update time and seller index. |
| V045 | Versioned, single-seller customer bulk draft and lines. |
| V046 | Adds default-empty bulk-request instructions; retains legacy request rows. |
| V047 | Separate PhonePe sandbox payment attempts, source links and retry/active-attempt uniqueness. |

There are **34 application tables**, **34 primary keys**, **55 foreign keys**, **97 CHECK constraints**, **10 UNIQUE constraints** and **76 indexes**, excluding Flyway's own table/indexes. The index total includes constraint indexes and partial unique indexes. One application trigger, `seller_supervisor_guard`, validates the membership hierarchy. These counts were queried from the disposable PostgreSQL 18.6 database after migration, not from Supabase. The repository defines no RLS policies or grants; the local application tables have RLS disabled. Live Supabase access controls remain unknown.

### Entities, relationships and important constraints

This table names all 34 application tables; grouped rows describe related tables together.

| Tables | Identity, relationships and invariants |
| --- | --- |
| `users` | UUID PK; unique normalized email; nonblank password hash/name; `STUDENT/FACULTY` affiliation; `ACTIVE/SUSPENDED/DISABLED`; independent `is_platform_admin`. Indexed by status. |
| `sellers` | Stable TEXT slug PK; `active`; partial unique index permits at most one `is_main_canteen=true`. Existing fixture IDs: `main-canteen`, `north-counter`, `coffee-corner`, `campus-pantry`. |
| `seller_memberships` | UUID PK; user and seller FKs; unique `(user_id,seller_id)`; OWNER has no supervisor, MANAGER references a same-seller OWNER, STAFF references a same-seller MANAGER. Checks plus trigger enforce the child row's shape. Seller/active and supervisor indexes. Multiple owners per seller are permitted. |
| `seller_membership_capabilities` | PK `(membership_id,capability)`; membership FK cascades; lowercase dotted capability syntax. Effective permissions also depend on live supervisors. |
| `sessions` | UUID PK; user FK; unique 32-byte token hash; expiry/revocation checks. User index and partial unrevoked-expiry index. |
| `menu_categories`, `seller_categories`, `menu_items` | Global TEXT category/item IDs; seller/category join PK; menu item FK `(seller_id,category_id)` to that join; unique `(seller_id,id)` for item references. Integer nonnegative paise, checked kind/dietary/availability/visibility, publication flag. Partial visible-menu index. No archive column. |
| `seller_ordering_policies`, `seller_hours`, `seller_closures` | Policy PK/FK seller; bulk minimum 1–999 and fixed Kolkata timezone. Hours PK `(seller_id,day_of_week)` allows one same-day interval and requires opening before closing. Closure PK `(seller_id,local_date)`. |
| `bulk_requests` | UUID PK; customer/seller FKs; positive current version; checked lifecycle status; unique customer/idempotency key and 32-byte request hash. Customer/time and seller/status/time indexes; instructions default empty. |
| `bulk_quote_revisions`, `bulk_quote_lines` | Revision PK `(request_id,version)`; creator/request FKs; pickup/expiry checks. Line PK adds item ID; FK to revision and `(seller_id,item_id)`; name/price snapshots. Line seller is not constrained to the request header's seller. |
| `bulk_quote_approvals`, `bulk_special_pickup_decisions`, `bulk_request_events` | Approvals PK includes actor kind; special decision PK is revision; both reference the exact quote version and actor. Events reference request/actor, with a checked event type and JSON details; event version has no revision FK. |
| `individual_demo_orders`, `individual_demo_order_lines` | Order UUID, customer/seller FKs; checked `DEMO_*` states; `demo_only=true` and `payment_verified=false` remain enforced. Nullable legacy idempotency fields, partial customer/key uniqueness; customer/time and seller/time indexes. Lines reference order and seller/item, snapshot name/price; header/line seller equality is not a DB constraint. |
| `individual_customer_carts`, `individual_customer_cart_lines` | One cart per user, nonnegative version; line PK `(customer_user_id,item_id)` and composite seller/item FK. Cart deletion cascades to draft lines. |
| `bulk_customer_drafts`, `bulk_customer_draft_lines` | One draft per user; nullable seller, version and pickup/instructions checks. Lines reference both `(customer_user_id,seller_id)` and `(seller_id,item_id)`, enforcing a single seller. |
| `sandbox_payment_attempts` | UUID; customer/seller FKs; polymorphic `source_type/source_id` checked in service code, with optional quote version. Unique merchant order ID and `(customer_user_id,source_type,source_id,idempotency_key)`. Partial unique source index covers `INITIATED/PENDING/CANCELLED/SANDBOX_SUCCESS`. Amount at least 100 paise. Customer, seller and source time indexes. |
| `customer_preferences`, `favorites`, `bulk_templates` | User-owned data, with user-delete cascades. Favorites PK `(user_id,target_type,target_id)` and seller FK; item target validated through the Commerce port. Templates have seller FK, JSON line array and unique `(user_id,seller_id,name)`. |
| `support_tickets`, `support_ticket_messages`, `support_ticket_status_history`, `notification_inbox` | Customer and optional seller scope; ticket messages/history reference ticket and actor. Partial unique client request/message keys; user/seller/status/time indexes. Inbox references user and optional ticket; channel constrained to `IN_APP_ONLY`. |
| `admin_audit_events`, `admin_setup_drafts`, `report_selections` | Audit actor/seller FKs, checked role/action and JSON object; actor/time indexes. Draft creator FK and one open draft per proposed seller slug; status only `DRAFT/ARCHIVED`, with no seller created. Report selection seller/creator FKs, JSON metrics, valid date range and seller/name uniqueness. |

Core, order and payment references generally use `ON DELETE RESTRICT`. Cascades are confined to the explicitly declared grant/customer-extra/draft/support relationships; they do not justify deleting business accounts or historical orders.

## 3. Implemented versus missing functionality

There are 84 HTTP method/path registrations: 82 under `/api/v1`, plus `/health` and `/ready`. Source counts: Application 8, Commerce 38, CustomerData 12, Support 13, Analytics 5, Platform 8. The following matrix covers the management boundaries; paths are relative to `/api/v1`.

| Area | Implemented API and behavior | Missing or partial |
| --- | --- | --- |
| Accounts | `POST /auth/register`, `/auth/login`, `/auth/logout`; `GET /auth/me`; `GET/PATCH /me/profile`. Customer-only registration; cloud signup is opt-in. | Verified identity, account recovery and production abuse controls; no public privileged registration. |
| Seller catalog/categories | Public `GET /sellers`, `/{sellerId}`, `/{sellerId}/menu`, `/{sellerId}/menu/{itemId}`. Existing category assignments and visibility rules. | Category creation/edit/assignment APIs and a private management listing including unpublished items. |
| Seller item management | `PATCH /sellers/{sellerId}/menu/items/{itemId}` (`catalog.edit`) and `/availability` (`availability.edit`); name, description, price, publish, dietary and visibility edits. | Create/archive/restore, category/kind/image updates and concurrent-edit versions. Unpublishing is available but is not a full archival workflow. |
| Seller staff and permissions | Membership hierarchy, active flags and live capability evaluator are implemented. Login/me exposes effective memberships. | List/invite/assign/reparent/revoke/reactivate staff, administer grants, owner-transfer policy and associated APIs. `employees.manage` exists but is unused by routes. |
| Seller profile | Public stored name/description; `profile.edit` capability exists. | Seller-owned profile update API. |
| Individual orders | `GET/PUT /me/cart/individual`, `POST .../checkout`; customer `GET/POST /me/individual-demo-orders`; seller GET queue and POST `/{orderId}/demo-status`. Server prices and idempotent cart checkout. | These are demo records, restricted to Main Canteen prepared items. Real fulfillment/reservations and operational inventory integration are absent. Seller queue/action currently use `bulk.view`/`bulk.respond`. |
| Bulk requests | `POST /bulk/requests`; customer and seller list/detail; counteroffers, customer/seller approvals, special-pickup decisions and withdrawal. Versioned `GET/PUT /me/draft/bulk`. | No real fulfillment/payment-to-pickup workflow; seller rejection API absent although `REJECTED` is a stored status. `GET /sellers/{sellerId}/pickup-schedule` intentionally returns an empty schedule. |
| Schedules/closures | `GET /sellers/{sellerId}/operating-policy`; `PATCH .../bulk-policy`; `PUT .../hours`; `PUT/DELETE .../closures/{localDate}` (`hours.edit`). | Multiple shifts, overnight hours and schedule edit versioning are not supported. |
| Seller analytics/reporting | `GET .../dashboard-summary`, `.../analytics/summary` (`analytics.view`); `GET/POST .../reports/selections` (`reports.export`). Stored request counts plus separately labelled simulation/UAT totals. | Verified revenue/cost/margin/inventory analytics. `GET .../reports/export` returns 501 `EXPORT_NOT_IMPLEMENTED`. |
| Platform administrators | Live `users.is_platform_admin`; guarded `/admin/*` routes. Local fixture can provision an admin; cloud seed does not. | Admin provisioning/role-management API and safeguards for last-admin/self-removal. No separate admin table is needed for the current model. |
| Seller onboarding | `GET/POST /admin/setup-drafts`, `POST .../{id}/archive`. | Draft approval, seller creation, owner linking/invitations and onboarding decisions. Existing drafts deliberately create neither sellers nor memberships. |
| Seller activation/suspension | `GET /admin/sellers`; `sellers.active` is honored by authorization/catalog. | Lifecycle write API, reasons/history and concurrency policy. |
| User/membership administration | Paginated/filterable `GET /admin/users`; live user status and membership flags already enforce access. | Admin suspension/reactivation, membership directory/mutations and privilege escalation safeguards. |
| Audit logs | `GET /admin/audit`; audit records for setup draft changes, report selections and support replies/escalation/status. | Audit coverage for catalog, schedules, staff grants and future admin/inventory writes; DB-enforced append-only access. |
| Platform analytics | `GET /admin/overview`, `GET /admin/analytics/summary`; real database counts with unverified sales separated. | Operational financial/inventory metrics and export generation. |
| Supporting customer/support APIs | Preferences, favorites, bulk templates, notification read state; customer/seller/admin ticket list/detail/replies, escalation and admin status changes. | External email/SMS/push delivery and attachments. Seller support uses `bulk.view`/`bulk.respond`. |
| PhonePe UAT | Customer create/list/detail/reconcile/cancel under `/me/sandbox-payments`; seller attempt listing. Cloud-demo-only gateway with server-owned prices and separate UAT records. | Actual provider/device readiness is not established by local fake-gateway tests. Payment logic is outside this task and remains unchanged. |
| Materials and units | None. | Material/unit/conversion tables and APIs. Menu item availability is not a stock balance. |
| Suppliers and purchase orders | None. | Supplier records, PO headers/lines, approvals and receipt matching. |
| Receipts, batches and expiry | None. | Receipt documents, lot identities, expiry dates and available-stock queries. |
| Stock ledger and wastage | None. | Immutable movements, balance concurrency, adjustments, waste reasons and reversals. |
| Reorder alerts | None. | Material thresholds, deduplicated alerts and replenishment state. |
| Recipes and consumption | None. | Versioned recipes, ingredient quantities, yield and exactly-once consumption linked to an approved fulfillment event. |

### Confirmed gaps and coupling to address during implementation

These findings come from source/DDL inspection; they are not evidence that Supabase contains corrupt records.

1. **Historical seller consistency relies on service code.** V021/V022 lines have a valid item/seller FK but no composite FK tying that seller to the request/order header. `bulk_requests.current_version` and event versions also have no FK to their revision. Before stronger constraints, inspect existing rows and preserve anomalies for explicit review; do not manufacture revisions or delete history.
2. **Catalog/favorite ID contracts differ.** V020 accepts item IDs of 1–96 characters; V040 and `CustomerDataRoutes`/`isSlug` permit favorite targets of only 2–63. A valid future item can therefore be impossible to favorite. Align validation and widen the check through a new migration before allowing longer/new item IDs.
3. **Category `active` is not used by catalog/pricing queries.** Adding a category suspension API requires an explicit visibility/ordering contract; changing the existing flag alone will not consistently hide its menu items. Seller-specific category settings also must not mutate another seller's global category.
4. **Supervisor changes need whole-chain validation.** V003 validates the inserted/updated member, not every dependent child when a supervisor's role/seller changes. Runtime authorization fails closed for invalid chains. Future reparent/promote APIs must lock and validate affected children and prevent privilege escalation or accidental loss of all active owners.
5. **Audit coverage is incomplete.** Commerce catalog/policy/hour/closure writes do not call the shared audit recorder. Existing audit rows are not protected by an append-only DB policy. Extend the current audit model rather than create an unrelated second audit system.
6. **Reads can have cross-seller write work.** `CommerceOperations.expireDue` expires all due bulk requests, and list/detail paths invoke it. This couples read latency and transactions across sellers; isolate/scope expiry before expanding operational workloads.
7. **Common rules are duplicated.** Account/auth, Commerce, cart, bulk-draft and Engagement code repeat live-session queries. Errors use three exception families (`ApiProblem`, `CommerceFault`, `ApiFailure`) and different invalid-body codes. Platform depends on Engagement internals. Consolidate these seams incrementally while preserving V1 response shapes.
8. **Individual ordering assumes the existing Main Canteen identity.** The cart response explicitly uses `main-canteen`; onboarding must not rename/reassign that ID or silently generalize individual ordering to every seller. Real order lifecycle and inventory consumption require their own explicit contract.

## 4. Proposed structure and management schema

Keep one Ktor application and one PostgreSQL database. Retain the existing feature registration functions and introduce route → service → JDBC repository/domain separation as each management capability is built. Repository methods take the service's transaction connection; they do not open nested connections for guarded writes.

```text
core/                         data source, clock, transaction helper, shared SQL utilities
api/                          error envelope, request validation, pagination
auth/                         account/session checks and capability evaluation
features/sellers/             profile, membership and operating-policy services
features/commerce/catalog/    catalog routes, service, repository and DTOs
features/commerce/            existing bulk/cart/demo/UAT boundaries retained
features/platform/            admin directory, onboarding and lifecycle services
features/audit/               shared transactional audit writer/read repository
features/inventory/           materials, procurement, receipts, stock and recipes
features/engagement/          existing customer/support behavior
features/integration/         narrow read ports and future approved fulfillment port
```

Move only the code needed by a phase; the diagram is a target, not a prerequisite wholesale refactor. Blocking JDBC calls should have a bounded IO execution policy consistent with the small connection pool. No microservices, second database or replacement identity system is proposed.

### Ownership, authorization and API rules

- **Seller ownership:** retain `sellers.id` as the canonical TEXT tenant key and existing UUID account/membership/order IDs. New seller-owned documents carry `seller_id`; their children use composite seller/document FKs. Resolve the authenticated actor on the server and check capability for the requested seller on every operation. An ID in the URL never grants access. Do not transfer existing menu items or historical documents between sellers.
- **Existing capabilities:** retain `bulk.view`, `bulk.respond`, `operations.history`, `catalog.edit`, `availability.edit`, `hours.edit`, `promotions.manage`, `employees.manage`, `profile.edit`, `analytics.view`, `reports.export`. OWNER receives the known capability set dynamically. MANAGER/STAFF need direct grants intersected with every active same-seller supervisor; suspended users, sellers and revoked/expired sessions fail access checks. Admin status gives no implicit seller membership.
- **Proposed inventory capabilities:** `inventory.view`, `inventory.materials.manage`, `inventory.suppliers.manage`, `inventory.purchase.manage`, `inventory.stock.receive`, `inventory.stock.adjust`, `inventory.recipes.manage`. Extend the existing allowlist intentionally; adding strings alone to grant rows does not enable unknown capabilities. Confirm the automatic OWNER entitlement before rollout; do not seed new manager/staff grants. Owner assignment and admin promotion need stricter rules than delegated `employees.manage`.
- **Transactions:** recheck live authorization inside the mutation transaction; lock the document/version and affected stock rows in a consistent order; commit document changes, ledger/balance changes, idempotency receipt and audit together. Version mismatch returns 409. No provider/network work inside inventory/management transactions. Same-transaction authorization reads alone do not guarantee serialization with concurrent revocation; define locking for sensitive role changes.
- **Idempotency:** preserve existing bulk, cart and UAT retry keys/hashes exactly. New creation/posting operations use an `Idempotency-Key`, canonical request hash and a unique seller/actor/operation/key record. Same payload returns the saved result; a changed payload returns 409. Receipt posting/consumption also need durable unique business-source keys, so expiration of an HTTP retry record cannot double-post stock.
- **Errors/versioning:** retain `/api/v1` and `{"error":{"code":"...","message":"...","details":null}}`; 400 input, 401 session, 403 capability, 404 absent/not-visible, 409 version/idempotency conflict, 422 business rule. Add routes/optional fields compatibly; use an explicit new version for breaking contracts. Preserve integer paise and UTC instants; interpret business dates in `Asia/Kolkata`.
- **Audit:** append actor, action, seller, target ID, correlation ID, safe changed-field metadata and reason for management writes in the same transaction. Do not log credentials, bearer/SDK tokens, payment payloads or support bodies. Reuse `admin_audit_events`; extend permissions/indexes and metadata as needed. Rejected requests belong in sanitized security logs, not fabricated successful business events.

### Seller/admin additions

| Reuse | Proposed additive design and API boundary |
| --- | --- |
| `menu_items`, `menu_categories`, `seller_categories` | Add item archive timestamp/edit version and seller-specific category display/order/active settings with compatibility defaults. Private management list, item create/archive/restore and category assignment endpoints under `/sellers/{sellerId}/...`; retain historical IDs and snapshots. |
| `seller_memberships`, capability rows | Add version metadata and, if invitations are required, a separate invitation table with hashed token, expiry and acceptance state. Seller member/grant APIs use `employees.manage` plus role/supervisor restrictions; preserve membership IDs on reactivation. |
| `sellers`, `users`, `admin_setup_drafts` | Keep `sellers.active`, user status and admin flag as current authorization inputs. Add onboarding decision/lifecycle-history records linked to the existing draft and eventual seller; do not change the meaning of old `DRAFT/ARCHIVED` rows. Explicit admin approve/suspend/reactivate APIs, version checks, audit and last-owner/last-admin safeguards. |
| `admin_audit_events`, `report_selections` | Extend audit coverage and seller/target lookup indexes. Keep report configuration distinct from export generation; analytics must continue separating demo/UAT amounts from verified financial data. |

### Inventory additions

All operational inventory tables below are seller scoped; only immutable unit definitions may be shared. Use UUID document IDs, composite tenant FKs, timestamps and actor references. Amounts remain integer paise; quantities/conversion factors use exact `NUMERIC`, for example `(20,6)`, with explicit rounding and unit-dimension rules.

| Proposed tables | Relationships and invariants | Proposed API family |
| --- | --- | --- |
| `inventory_units`, `inventory_materials`, `inventory_material_conversions` | Material belongs to seller and base unit; unique seller/material code; conversion factors positive and dimension compatible. Packaging conversions can be material specific. Archive reference data once used. | `/sellers/{sellerId}/inventory/materials`, unit/conversion lookups and edits |
| `inventory_suppliers`, `inventory_purchase_orders`, `inventory_purchase_order_lines` | PO supplier and materials belong to the same seller. Positive quantities, explicit draft/approved/closed/cancelled transitions; snapshot purchased unit and price. | `.../suppliers`, `.../purchase-orders` and approval/cancellation actions |
| `inventory_receipts`, `inventory_receipt_lines`, `inventory_batches` | Receipt references supplier/optional PO and immutable posted lines; batch identifies material, supplier lot and expiry. Validate cumulative PO receipts and base-unit conversions. | `.../receipts` and post action; `.../batches`/expiry reads |
| `inventory_stock_movements`, `inventory_stock_balances` | Immutable nonzero signed movements reference receipt/adjustment/waste/consumption source. Unique posting source; exact base quantities; balances updated under row locks in the same transaction. No negative available balance. Corrections append linked reversal entries. | `.../stock`, `.../movements`, explicit adjustment action |
| `inventory_wastage` | Material/batch, positive quantity, reason and actor; posting creates one negative movement. No deletion/rewrite of a posted loss. | `.../wastage` |
| `inventory_reorder_rules`, `inventory_reorder_alerts` | Seller/material threshold and target; positive settings, at most one open alert for a material/rule; explicit acknowledgment/closure. Exclude expired stock from available quantities. | `.../reorder-rules`, `.../alerts` |
| `inventory_recipes`, `inventory_recipe_versions`, `inventory_recipe_ingredients` | Recipe references existing seller/menu item; immutable version/yield and positive material quantities, all within the seller. Store the recipe version used by each consumption. | `.../recipes` and version/publish actions |
| `inventory_consumptions` | Unique approved fulfillment-source/version; atomic ingredient/batch allocation and movements, using earliest suitable expiry where applicable. Partial failure rolls everything back. | Internal fulfillment command; authorized history reads |

Do not infer starting stock from catalog availability or historical orders. Receipt posting and its ledger/balance foundation must ship together. Demo order transitions and PhonePe UAT success must **never automatically consume operational stock**. The consumption phase waits for an approved real fulfillment contract; manual stock administration and recipe definition can proceed independently.

## 5. Safe forward migration strategy

**V048 is the next unused repository version.** V006–V019 and V023–V039 are historical allocation gaps, not versions to backfill on a database already at V047. The original team allocation reserved V040–V059 for Engagement/Admin; agree a central allocation before multiple contributors implement the following provisional plan. Live usage of V048+ is unknown until the blocked comparison is completed.

| Candidate version | Proposed scope; no file has been added |
| --- | --- |
| V048 | Shared management idempotency/audit metadata; stage additional seller-consistency constraints only after anomaly inspection. |
| V049 | Catalog archive/edit versions and seller category settings; compatible item/favorite ID validation alignment. |
| V050 | Membership edit versions and invitation lifecycle, preserving existing memberships/grants. |
| V051 | Admin onboarding decisions and seller/user lifecycle history; reuse existing flags/drafts. |
| V052 | Inventory units, materials and conversion rules. |
| V053 | Suppliers, purchase orders and lines. |
| V054 | Receipts, batches, stock ledger and transactionally maintained balances. |
| V055 | Wastage and explicit adjustment/reversal documents. |
| V056 | Reorder rules and alert lifecycle. |
| V057 | Recipes, immutable versions and ingredient lines. |
| V058 | Consumption source links/deduplication, gated on the fulfillment contract. |
| V059 | Follow-up validation/index work if required; otherwise leave unallocated. Split large work into additional ascending versions as necessary before it is applied. |

Migration rules for subsequent implementation:

1. Leave every existing V001–V047 file and checksum unchanged. Never reset, drop/truncate business tables, regenerate IDs, rewrite historical prices/statuses or run Flyway `clean`/`repair` to bypass a mismatch. Do not enable out-of-order migration to fill old ranges.
2. Prefer new tables and nullable/default-compatible columns. Existing accounts, sellers, grants, drafts, orders and UAT attempts keep their identifiers and semantics. Additive migrations must tolerate populated tables and the old application during rollout; new writes stay disabled until compatible readers are deployed.
3. Inspect nulls, duplicates and cross-seller anomalies before constraints. Stage eligible FK/CHECK additions as `NOT VALID`, then validate after review; new-row enforcement still applies. Build supporting unique indexes only after checking uniqueness. Tightening `NOT NULL` or making a field required needs a separate reviewed backfill, with bounded batches and no inferred business values.
4. Existing-constraint changes, such as widening favorite IDs, belong in a new forward migration with an equivalent-or-broader acceptance rule and regression tests. They must not edit the original SQL or remove stored rows. Additional header/line seller FKs must preserve existing item/order relationships and use explicit validation gates.
5. Keep ordinary PostgreSQL DDL transactional and set bounded lock/statement timeouts. For a large index that truly needs `CREATE INDEX CONCURRENTLY`, use a separately reviewed nontransactional Flyway script and explicit failure/retry handling; do not disable transactionality globally.
6. On local disposable databases rehearse a fresh install and a populated V047 upgrade, restart/checksum validation and the full backend suite. Include representative legacy rows, edited catalog, revoked grants, historical snapshots and pending/cancelled/successful UAT attempts. Verify old data before/after, not just migration success.
7. Before any later live rollout, resolve the read-only comparison, verify the target project/server version, review backup/restore readiness and explicit grants/RLS for new tables, and obtain the separate authorization for deployment/migration. Ktor authorization does not protect tables accessed directly through another database/API role. No such rollout is part of this report.
8. A failed future rollout should stop for diagnosis and a reviewed forward fix; retain existing data and feature-disable compatible new behavior where needed. No automatic destructive down migration is proposed.

## 6. Compatibility assessment

| Existing data/contract | Evidence and required preservation |
| --- | --- |
| Accounts and sessions | Current auth tests pass for persistent identities, changed password/profile retention, independent sessions and revocation. Retain `users` UUIDs, normalized emails, hashes and session semantics; do not replace them with `auth.users` or rerun seeds to reset accounts. |
| Seller IDs and hierarchy | Foundation/auth tests pass for scope, supervision and revoked grants. Preserve the four existing slugs, `is_main_canteen`, membership UUIDs and capability wires. The DB permits additional sellers and multiple owners; onboarding must not overwrite existing rows. |
| Catalog and history | Fresh/legacy catalog tests pass, including preservation of seller edits. Keep global item IDs, seller/category relationships and name/price snapshots. Archive future catalog records instead of breaking historical FKs. |
| Carts, bulk drafts and orders | Commerce tests pass for version conflicts, idempotency, ownership, quote revisions, schedules and demo transitions. V043's nullable legacy keys and V046's empty instructions preserve old request/retry behavior. Do not mark old demo orders paid or consume stock retroactively. |
| V046→V047 | Existing upgrade test migrates a populated disposable schema to V047 twice and verifies preserved user/cart/draft/order/line/request records and false payment verification. This establishes the current upgrade path, not future V048+ compatibility. |
| PhonePe UAT records | V047 and payment code are unchanged. Fake-gateway tests pass for server price, retry behavior, state reconciliation and unverified analytics. Preserve attempt/source/quote IDs, amount, merchant/provider references and states. In particular, `CANCELLED` remains part of the active-attempt unique index because reconciliation can still succeed. Do not alter this index or payment state machine for inventory work. |
| Supabase history | **Unverified.** Neither live rows nor live Flyway checksums were read. Proposed additions have not been run against any live or synthetic V048+ schema because no new migrations exist yet. Actual provider/device UAT also remains untested here. |

## 7. Backend verification results

Executed from `backend/`, using the backend wrapper only:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin --no-daemon --max-workers=1
.\gradlew.bat test --no-daemon --max-workers=1
```

| Check | Result in this preflight |
| --- | --- |
| Toolchain | Windows x64; Adoptium JDK 25.0.4.101; Gradle wrapper 9.3.1. |
| Compilation | **PASS**, 5m 39s. Three existing unnecessary `!!` warnings in `EngagementFeatureTest`; no compilation errors. |
| Initial test attempt | 49 executed, 6 failed, 0 skipped: test fixtures reject database names other than the exact loopback `rit_canteen_fresh`/`rit_canteen_legacy` names. This was an audit setup error, not a source defect. Initial XML/logs were retained locally before rerun. |
| Corrected complete test run | **PASS**, 1m 35s: **49 tests, 0 failures, 0 errors, 0 skipped**, confirmed from JUnit XML. No test/code changes were made. |
| Migration metadata | All 16 migrations through V047 successful in local `rit_canteen_test`; schema counts in section 2 confirmed with read-only metadata queries. |
| Database isolation | New PostgreSQL **18.6** cluster, bound only to `127.0.0.1:55437`, with a generated local password. Dedicated test/fresh/legacy databases; no existing PostgreSQL service/database was used. |
| PostgreSQL 16 | Not run: Docker client is installed but its Linux engine is unavailable, including outside the sandbox. Installed native PostgreSQL was used instead; this does not establish the unknown Supabase server-version match. |
| Live Supabase/Render/provider/device tests | Not run; no authorized live DB connection, no deployment, no real provider calls. Existing fake gateway covers local UAT behavior only. |
| Android | Not compiled, tested, investigated or modified. |

| Test class | Passed |
| --- | ---: |
| `BackendConfigTest` | 10 |
| `CloudDemoSecurityTest` | 1 |
| `DatabaseFoundationTest` | 4 |
| `AuthIntegrationTest` | 6 |
| `CommerceIntegrationTest` | 10 |
| `EngagementFeatureTest` | 12 |
| `CatalogParityIntegrationTest` | 3 |
| `CloudDemoDataSeedTest` | 2 |
| `SandboxMigrationUpgradeTest` | 1 |
| **Total** | **49** |

The sandbox initially could not create Gradle's user-cache lock or launch PostgreSQL; those commands succeeded outside it. Tests exercised Ktor through the existing test host and real local JDBC/Flyway. They create fixtures and, where their existing cleanup does so, remove only their own generated schemas in disposable databases. No shared tables were reset or deleted. No standalone Netty smoke script was run.

### Local test environment retained for the next task

Data directory: `backend/pgdata/preflight-20261030/data`; log: adjacent `server.log`; generated local password: adjacent `password.txt`. `backend/pgdata/` is already ignored. The cluster was stopped successfully after this audit, retaining its data for reuse. Final test artifacts are `backend/build/test-results/test/TEST-*.xml`, `backend/build/reports/tests/test/index.html` and `backend/build/preflight-test-run.log`; the setup-error attempt is retained in `backend/build/preflight-attempt1-test-results/` and `preflight-attempt1.log`.

To reuse this exact disposable cluster from `backend/`:

```powershell
$auditRoot = Join-Path (Get-Location) 'pgdata\preflight-20261030'
$auditData = Join-Path $auditRoot 'data'
& 'C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe' -D $auditData `
    -l (Join-Path $auditRoot 'server.log') -o '-h 127.0.0.1 -p 55437' -w start

$env:RIT_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55437/rit_canteen_test'
$env:RIT_TEST_FRESH_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55437/rit_canteen_fresh'
$env:RIT_TEST_LEGACY_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55437/rit_canteen_legacy'
$env:RIT_TEST_DATABASE_USER = 'rit_preflight'
$env:RIT_TEST_DATABASE_PASSWORD = [IO.File]::ReadAllText((Join-Path $auditRoot 'password.txt'))
$env:RIT_INTEGRATION_DISPOSABLE_FRESH_DB = 'YES'
$env:RIT_INTEGRATION_DISPOSABLE_LEGACY_DB = 'YES'
.\gradlew.bat test --no-daemon --max-workers=1

& 'C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe' -D $auditData -m fast -w stop
```

Use `test --rerun-tasks` if intentionally rerunning against different database contents after a successful cached run; this build does not declare these environment variables as test-task inputs. The exact fresh/legacy names are required by existing test guards. Ordinary tests only require the configured credentials and do not all enforce loopback, so keep every `RIT_TEST_*` URL explicitly local. The first attempt's additional `rit_preflight_*` databases are also isolated in this cluster and were retained; none belongs to the installed system PostgreSQL service.

## 8. Proposed implementation order and gates

1. **Contracts and foundation:** agree migration ownership, seller/category semantics, role-administration restrictions, audit coverage and idempotency rules. Add focused local regressions for the schema/ID gaps before changing behavior. Live-schema verification remains a prerequisite for any shared-database application, not for isolated local development.
2. **Seller catalog and staff:** implement private catalog reads, item creation/archive, category assignments, seller profile and membership/grant workflows using existing tenant IDs and capabilities. Depends on shared authorization/audit/transaction seams.
3. **Admin management:** implement reviewed onboarding approval and seller/user lifecycle commands, preserving draft/history records and requiring an existing platform admin. Add last-owner/last-admin and concurrent-role-change tests before enabling these APIs.
4. **Inventory reference data and procurement:** units/materials/conversions, suppliers and purchase orders. Depends on seller scoping, precise quantities, permissions and shared command idempotency.
5. **Stock operations:** receipts/batches/ledger/balances together, followed by wastage/reversals and reorder alerts. Gate on concurrent receipt/adjustment, overspend, duplicate-posting, expiry and rollback tests.
6. **Recipes, reporting and eventual consumption:** versioned recipes and stock/cost reporting first. Consumption waits for an approved real fulfillment event and exactly-once source contract; preserve demo/UAT separation. Export generation can be a separate later feature.

Before each later migration is considered ready, require fresh-install and populated-upgrade tests, all existing backend regressions, seller/actor isolation, rollback/idempotency tests and an unchanged historical-data comparison. This preflight introduces no business functionality, new migration, deployment or shared-database change.
