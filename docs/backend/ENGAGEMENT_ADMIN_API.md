# Engagement/Admin API — Backend 3

Module: `edu.msrit.canteen.backend.features.engagement` (+ internal `...features.platform`). Based on `FOUNDATION_SHA` `05e55583e38b942b58a208c78dedd0959a101e62`. All paths below are relative to `/api/v1`. JSON, UTC ISO-8601 instants, frozen error envelope `{"error":{"code","message","details":null}}`, unknown request keys rejected with `400 INVALID_REQUEST_BODY`.

This is a **local demonstration backend**. Nothing here delivers push/SMS/email, verifies payments, reports revenue, invites sellers or generates export files.

## Registration (lead action)

```kotlin
import edu.msrit.canteen.backend.features.engagement.registerEngagementRoutes

route("/api/v1") {
    registerEngagementRoutes(context)                 // frozen seam; Commerce not connected
    // after Backend 2 merges:
    // registerEngagementRoutes(context, commerceAdapter)   // commerceAdapter: CommerceReadPort
}
```

`registerEngagementRoutes(context)` delegates to `registerEngagementRoutes(context, UnavailableCommerceReadPort)`. It mounts customer, support, seller analytics and platform admin routes; `registerPlatformRoutes` is internal and invoked from this seam only.

### Commerce port (integration seam)

```kotlin
interface CommerceReadPort {
    fun lookupItem(itemId: String): ItemLookup            // Found(sellerId) | Missing | Unavailable
    fun demoRequestCounts(sellerId: String?, from: LocalDate?, to: LocalDate?): DemoRequestCounts?  // null = no source
}
```

Without an adapter: item favorites return `503 CATALOG_UNAVAILABLE` (fail closed, arbitrary IDs are never trusted); analytics return `demoRequestCounts: null`, `demoRequestCountsStatus: "UNAVAILABLE_NO_COMMERCE_SOURCE"`, `insufficientData: true`. With an adapter, counts are shown but labelled `DEMO_UNVERIFIED`; revenue stays `null`.

## Authorization model

| Caller | Helper used | Scope |
| --- | --- | --- |
| Customer (`/me/**`) | `requireAuthenticatedUser` + in-transaction live-session recheck on writes | Rows where `user_id` = authenticated principal. No client `customerId` is accepted. Another user's row is `404`. |
| Seller (`/sellers/{sellerId}/**`) | `requireSellerCapability` + `hasSellerCapability(connection, …)` recheck on writes | Only tickets explicitly routed to `{sellerId}`. Membership revocation, supervisor-chain revocation and session revocation deny at once. |
| Platform admin (`/admin/**`) | `requirePlatformAdmin` + `isPlatformAdmin(connection, …)` recheck on writes | Live `users.is_platform_admin`. The admin is **not** a seller member: seller routes return `403` for the admin. |

## Customer endpoints

| Method | Path | Success | Notes |
| --- | --- | --- | --- |
| GET | `/me/preferences` | `200 PreferencesDto` | Defaults if never saved: operational `true`, transactional `true`, promotional `false`, personalizationConsent **`false`**, `updatedAt: null`. |
| PATCH | `/me/preferences` | `200 PreferencesDto` | Partial; omitted fields unchanged. |
| GET | `/me/favorites` | `200 {"items":[FavoriteDto]}` | |
| PUT | `/me/favorites/{seller\|item}/{targetId}` | `200 FavoriteDto` | Idempotent. Seller must exist and be active (`404`). Item needs Commerce (`503`/`404`). Max 200 (`422 FAVORITES_LIMIT`). |
| DELETE | `/me/favorites/{seller\|item}/{targetId}` | `204` | Idempotent; only the caller's row. |
| GET | `/me/bulk-templates` | `200 {"items":[BulkTemplateDto]}` | |
| GET | `/me/bulk-templates/{id}` | `200 BulkTemplateDto` | |
| POST | `/me/bulk-templates` | `201 BulkTemplateDto` | 1–50 distinct lines, quantity 1–999, **no bulk minimum**. `409 TEMPLATE_NAME_TAKEN`; max 50 (`422`). |
| PATCH | `/me/bulk-templates/{id}` | `200 BulkTemplateDto` | `name` and/or `lines`. |
| DELETE | `/me/bulk-templates/{id}` | `204` | |
| GET | `/me/notifications?limit&offset&unreadOnly=true` | `200 NotificationsResponse` | In-app inbox only (`deliveryChannel: "IN_APP_ONLY"`). |
| PATCH | `/me/notifications/{id}/read` | `200 NotificationDto` | Idempotent. |

```json
PATCH /me/preferences            {"personalizationConsent":true}
200 {"operationalNotifications":true,"transactionalNotifications":true,"promotionalNotifications":false,"personalizationConsent":true,"updatedAt":"2026-10-27T10:00:00Z"}

PUT /me/favorites/seller/main-canteen
200 {"targetType":"SELLER","targetId":"main-canteen","sellerId":"main-canteen","createdAt":"2026-10-27T10:00:00Z"}

POST /me/bulk-templates          {"sellerId":"north-counter","name":"Club lunch","lines":[{"itemId":"north-lunch","quantity":12}]}
201 {"id":"…","sellerId":"north-counter","name":"Club lunch","lines":[{"itemId":"north-lunch","quantity":12}],"catalogValidated":false,"createdAt":"…","updatedAt":"…"}
```

`catalogValidated` is always `false`: template lines are stored as references and Commerce must revalidate items, quantities and bulk minimums on actual submission.

## Support tickets

Categories `FOOD|SCHEDULE|PAYMENT|ACCOUNT|APP`; statuses `OPEN|ESCALATED|RESOLVED|CLOSED`. No support action reads or changes any payment field (none exists on these tables). No attachments are accepted.

| Method | Path | Who | Success |
| --- | --- | --- | --- |
| GET | `/me/support/tickets?status&limit&offset` | customer (own) | `200 PageDto<TicketSummaryDto>` |
| POST | `/me/support/tickets` | customer | `201 TicketDetailDto`; `200` with the original ticket on a repeated `clientRequestId` |
| GET | `/me/support/tickets/{id}` | customer (own) | `200 TicketDetailDto` |
| POST | `/me/support/tickets/{id}/messages` | customer (own) | `201 TicketMessageDto`; `200` on repeated `clientMessageId`; `409 TICKET_CLOSED` |
| POST | `/me/support/tickets/{id}/escalate` | customer (own) | `200 TicketDetailDto`; OPEN→ESCALATED, idempotent if already ESCALATED, otherwise `409` |
| GET | `/sellers/{sellerId}/support/tickets` | `bulk.view` | `200 PageDto<TicketSummaryDto>` (routed tickets only) |
| GET | `/sellers/{sellerId}/support/tickets/{id}` | `bulk.view` | `200 TicketDetailDto` |
| POST | `/sellers/{sellerId}/support/tickets/{id}/messages` | `bulk.respond` | `201 TicketMessageDto` |
| POST | `/sellers/{sellerId}/support/tickets/{id}/escalate` | `bulk.respond` | `200 TicketDetailDto` |
| GET | `/admin/support/tickets?status&limit&offset` | admin | `200 PageDto<TicketSummaryDto>` incl. `customerUserId` |
| GET | `/admin/support/tickets/{id}` | admin | `200 TicketDetailDto` |
| POST | `/admin/support/tickets/{id}/messages` | admin | `201 TicketMessageDto` |
| PATCH | `/admin/support/tickets/{id}` `{"status":"RESOLVED"}` | admin | `200 TicketDetailDto`; `409 INVALID_TICKET_TRANSITION` |

Admin transitions: OPEN→ESCALATED/RESOLVED/CLOSED, ESCALATED→RESOLVED/CLOSED, RESOLVED→OPEN/CLOSED, CLOSED→(none).

```json
POST /me/support/tickets
{"category":"FOOD","subject":"Cold lunch","message":"Lunch was cold","sellerId":"main-canteen","requestReference":"REQ-1001","clientRequestId":"retry-key-0001"}
201 {"ticket":{"id":"…","category":"FOOD","subject":"Cold lunch","status":"OPEN","sellerId":"main-canteen","requestReference":"REQ-1001","customerUserId":null,"escalatedAt":null,"createdAt":"…","updatedAt":"…"},
     "messages":[{"id":"…","authorRole":"CUSTOMER","mine":true,"body":"Lunch was cold","createdAt":"…"}],
     "statusHistory":[{"fromStatus":null,"toStatus":"OPEN","actorRole":"CUSTOMER","changedAt":"…"}]}
```

Validation: subject 3–120 chars, message 1–4000, `sellerId` an existing active seller, `requestReference` `[A-Za-z0-9._:-]{1,64}`, client IDs `[A-Za-z0-9_-]{8,64}`. Sellers and customers see messages as `authorRole` + `mine` only, not other people's names or emails. A seller or admin reply and an admin status change add a row to the customer's in-app inbox (`SUPPORT_REPLY`, `SUPPORT_STATUS_CHANGED`); the inbox copies the subject, never the message body.

## Platform administrator

All `GET` lists accept `limit` (1–100, default 25) and `offset` (0–10000); bad values return `400 INVALID_PAGINATION`. They return `{"items":[…],"limit","offset","total"}`.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/admin/overview` | Live counts from core `users`/`sellers` and the support/draft tables; `source: "CORE_DATABASE"`. |
| GET | `/admin/sellers?q` | `AdminSellerDto`: id, name, description, active, isMainCanteen, activeMemberCount, createdAt. |
| GET | `/admin/users?q&status` | `AdminUserDto`: id, email, fullName, affiliation, status, platformAdmin, activeMembershipCount, createdAt. **No** password hash, sessions, department. `q` matches literally (LIKE wildcards escaped). |
| GET | `/admin/audit?action&targetType` | `AuditEventDto`, newest first. |
| GET | `/admin/setup-drafts` | Planning drafts. |
| POST | `/admin/setup-drafts` `{"proposedSellerId","sellerName","notes"?}` | `201`; `sellerCreated:false`, `invitationSent:false`. `409` if a seller or open draft already uses the ID. |
| POST | `/admin/setup-drafts/{id}/archive` | `200`, idempotent. |
| GET | `/admin/analytics/summary?from&to` | Platform-scope `AnalyticsSummaryDto`. |

**Account restriction is read-only here.** Changing `users.status` / `is_platform_admin` is a core-table write owned by Backend 1; it is listed as lead integration work, not implemented.

### Audit scope

`admin_audit_events` records: `support.reply_posted` (seller/admin), `support.ticket_escalated` (any actor), `support.status_changed` (admin), `admin.setup_draft_created`, `admin.setup_draft_archived`, `report.selection_saved`. Each row has actor ID and role, action, target type/ID, optional seller ID, time, and a `detail` object holding IDs and status wires only. Ticket bodies, tokens and passwords are never written to audit. Customer ticket creation and customer messages are kept in the ticket history rather than the audit log.

## Seller analytics and reports

| Method | Path | Capability | Result |
| --- | --- | --- | --- |
| GET | `/sellers/{sellerId}/dashboard-summary` | `analytics.view` | `DashboardSummaryDto` |
| GET | `/sellers/{sellerId}/analytics/summary?from=YYYY-MM-DD&to=YYYY-MM-DD` | `analytics.view` | `AnalyticsSummaryDto` (range ≤366 days, seller-local dates) |
| GET/POST | `/sellers/{sellerId}/reports/selections` | `reports.export` | Saved configuration; metrics `DEMO_REQUEST_COUNTS`, `SUPPORT_TICKETS`; `exportAvailable:false` |
| GET | `/sellers/{sellerId}/reports/export` | `reports.export` | **`501 EXPORT_NOT_IMPLEMENTED`** (no XLSX/CSV generator exists) |

```json
GET /sellers/main-canteen/analytics/summary?from=2026-10-01&to=2026-10-27
200 {"scope":"SELLER","sellerId":"main-canteen","from":"2026-10-01","to":"2026-10-27","timezone":"Asia/Kolkata",
     "dataStatus":"UNVERIFIED_OR_INSUFFICIENT","verifiedRevenuePaise":null,"source":"NO_VERIFIED_PAYMENTS",
     "demoRequestCounts":null,"demoRequestCountsStatus":"UNAVAILABLE_NO_COMMERCE_SOURCE","forecast":"INSUFFICIENT_DATA"}

GET /sellers/main-canteen/dashboard-summary
200 {"sellerId":"main-canteen","insufficientData":true,"dataStatus":"UNVERIFIED_OR_INSUFFICIENT","verifiedRevenuePaise":null,
     "demoRequestCounts":null,"demoRequestCountsStatus":"UNAVAILABLE_NO_COMMERCE_SOURCE",
     "supportTickets":{"open":1,"escalated":0,"resolved":0,"closed":0}}
```

Data provenance: `supportTickets` comes from this module's tables (real local data). `demoRequestCounts` comes only from an injected `CommerceReadPort`. `verifiedRevenuePaise` is always `null` because there is no verified payment source. `forecast` is always `INSUFFICIENT_DATA`.

## Schema (migrations V040–V042, `backend/src/main/resources/db/migration/`)

| File | Tables |
| --- | --- |
| `V040__customer_engagement.sql` | `customer_preferences` (PK user), `favorites` (PK `(user_id,target_type,target_id)`), `bulk_templates` (JSONB lines, unique `(user_id,seller_id,name)`) |
| `V041__support_and_inbox.sql` | `support_tickets` (unique partial `(user_id,client_request_id)`), `support_ticket_messages` (unique partial `(ticket_id,author_user_id,client_message_id)`), `support_ticket_status_history`, `notification_inbox` (`delivery_channel` CHECK = `IN_APP_ONLY`) |
| `V042__platform_admin.sql` | `admin_audit_events`, `admin_setup_drafts` (one open draft per proposed ID), `report_selections` |

Foreign keys reference only core `users` and `sellers`; there are none to Commerce tables. Customer extras cascade on user deletion; tickets, messages and audit rows use `RESTRICT`. Status, category, slug and length rules are enforced by CHECK constraints as well as in Kotlin.

## Error codes added

`INVALID_REQUEST_BODY`, `INVALID_PAGINATION`, `INVALID_DATE`, `INVALID_DATE_RANGE`, `INVALID_FAVORITE_TYPE`, `INVALID_TARGET_ID`, `INVALID_TEMPLATE_NAME`, `INVALID_TEMPLATE_LINES`, `INVALID_SUBJECT`, `INVALID_MESSAGE`, `INVALID_SELLER_ID`, `INVALID_REQUEST_REFERENCE`, `INVALID_CLIENT_ID`, `INVALID_STATUS`, `INVALID_REPORT_NAME`, `INVALID_REPORT_METRICS`, `INVALID_SELLER_NAME`, `INVALID_NOTES` (all 400); `NOT_FOUND` (404); `TEMPLATE_NAME_TAKEN`, `REPORT_NAME_TAKEN`, `TICKET_CLOSED`, `INVALID_TICKET_TRANSITION`, `SELLER_ID_TAKEN`, `DRAFT_EXISTS` (409); `FAVORITES_LIMIT`, `TEMPLATES_LIMIT` (422); `EXPORT_NOT_IMPLEMENTED` (501); `CATALOG_UNAVAILABLE` (503). Auth errors are the foundation's `UNAUTHENTICATED`, `SELLER_CAPABILITY_REQUIRED` and `PLATFORM_ADMIN_REQUIRED`.

## Run and test

From `backend/` (see `backend/README.md`):

```sh
cp .env.example .env            # set a local-only DATABASE_PASSWORD
docker compose --env-file .env up -d postgres
docker compose --env-file .env exec -T postgres psql -U rit_canteen -d postgres -c "CREATE DATABASE rit_canteen_test;"
set -a; . ./.env; set +a
export RIT_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/rit_canteen_test RIT_TEST_DATABASE_USER=rit_canteen RIT_TEST_DATABASE_PASSWORD="$DATABASE_PASSWORD"
./gradlew clean assemble
./gradlew test --no-daemon --max-workers=1 --tests 'edu.msrit.canteen.backend.features.engagement.*'
```

The routes are reachable over HTTP from `./gradlew run` only after the lead adds the registration call to `Application.kt`. Until then they are exercised through `testApplication`, using `backendModule(context)` plus `registerEngagementRoutes` under `/api/v1`.

## Known limitations

- No real login yet (Phase B), so all route tests use disposable DB users and sessions with SHA-256-hashed bearer tokens.
- Item favorites and request counts need the Commerce adapter. Deep links from inbox rows are limited to `ticketId`; no Android navigation contract exists.
- No push, SMS or email; no attachments; no seller invitations or email verification; no payouts; no revenue; no XLSX/CSV export; no account suspension endpoint.
