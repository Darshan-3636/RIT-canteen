# Seller and platform management API

Implemented locally on the integrated backend; requires **V048**. This implementation's single migration replaces the earlier proposed Seller/Admin allocation in the design report. No inventory migrations are included. V001-V047 and PhonePe gateway/attempt behavior remain unchanged.

All paths below start with `/api/v1`. Authentication uses the existing opaque bearer session. Public menu responses retain their fields. Errors retain `{"error":{"code":"...","message":"..."}}`.

## Seller resources

Use `S = /sellers/{sellerId}`. All queries/commands enforce seller ownership and live capabilities. Platform admins do not implicitly have seller-route access.

| Method/path | Capability | Contract |
| --- | --- | --- |
| GET `S/catalog/items`, GET `S/catalog/items/{itemId}` | `catalog.edit` | Private items include unpublished/archived rows and `version`; collection uses `limit` 1-100 and `offset` 0-10000. |
| POST `S/catalog/items` | `catalog.edit` | Required `id,categoryId,name,pricePaise,kind,imageKey`; optional description/dietary/publication/availability/visibility. Existing global item IDs cannot be reused by another seller. |
| PATCH `S/menu/items/{itemId}` | `catalog.edit` | Existing fields plus optional `categoryId,kind,imageKey,expectedVersion`; historical items cannot change kind. Existing response unchanged. |
| PATCH `S/menu/items/{itemId}/availability` | `availability.edit` | `availability`, optional `expectedVersion`; archived items must first be restored. |
| POST `S/catalog/items/{itemId}/archive` or `/restore` | `catalog.edit` | Required `expectedVersion`. Both leave the item unpublished. No historical references are deleted. |
| GET `S/catalog/categories` | `catalog.edit` | Assigned global categories with seller label/order/active state and version. |
| PUT `S/catalog/categories/{categoryId}` | `catalog.edit` | Assign/update an existing global category. Body: `expectedVersion,displayName?,sortOrder,active`. Use version 0 for a new assignment; `active=false` archives the assignment. |
| GET/PATCH `S/profile` | `profile.edit` | GET returns current profile and `version`; PATCH accepts `name,description,expectedVersion`. Seller ID/activation/main-canteen flag are not editable. |
| GET `S/memberships`, POST `S/memberships`, PATCH `S/memberships/{id}` | `employees.manage` plus hierarchy rules | See membership body below. List is paginated. |
| PUT `S/memberships/{id}/capabilities` | `employees.manage` plus hierarchy rules | `capabilities,expectedVersion,reason`; replaces direct grants. |
| GET `S/operations/version` | `hours.edit` | Current aggregate policy version; existing operating-policy GET supplies settings/hours/closures. |
| Existing PATCH `S/bulk-policy`, PUT `S/hours`, PUT/DELETE `S/closures/{date}` | `hours.edit` | Optional `If-Match: "version"`; every successful command advances the same policy version and records audit. Existing response shapes retained. |
| POST `S/bulk/requests/{requestId}/reject` | `bulk.respond` | `expectedVersion` (quote revision), `reason`; only current unexpired SUBMITTED/COUNTEROFFER requests can be rejected. |
| GET `S/analytics/operations` | `analytics.view` | Stored catalog/member counts and individual-demo/bulk counts by status; optional ISO `from,to`, maximum 366-day range. Revenue remains unverified. |
| GET `S/reports/export` | `reports.export` | CSV; optional `selectionId,from,to`. Defaults to the last 30 Kolkata calendar days. Saved selection must belong to the seller. At most 366 days; support/request metrics are explicitly labelled. |

Active global category, active seller assignment, publication, archive and availability states now apply consistently to public catalog, cart/draft availability, favorites lookup and checkout pricing. Item IDs permit 1-96 lowercase slug characters, including favorites/templates. Historical order lines retain their snapshots. Category names are global; seller-specific naming uses `displayName`. Administrators create global categories.

### Membership commands

POST requires an existing `userId`. PATCH retains the original user and seller; it **replaces the mutable membership configuration**, so send the intended role, supervisor, active state and full grant list:

```json
{
  "role": "STAFF",
  "supervisorMembershipId": "existing-manager-membership-uuid",
  "active": true,
  "capabilities": ["bulk.view", "catalog.edit"],
  "expectedVersion": 0,
  "reason": "Assigned counter duties"
}
```

- OWNER can manage managers/staff; OWNER assignment and modification use admin routes. OWNER receives the existing allowlist automatically, so an explicit OWNER grant list must be empty.
- MANAGER can manage only directly supervised STAFF and delegate only effective capabilities they hold. STAFF cannot administer memberships even if an old `employees.manage` grant exists. Seller operators cannot edit their own membership or promote themselves.
- Supervisors must belong to the same seller and have the preceding role. Active members require active accounts/supervisor chains. Reassign dependents before changing a supervisor's role. Parent edits have an additional deferred database guard.
- Use PATCH with `active=false` to deactivate; no membership deletion. Reactivation requires explicitly reviewed configuration/grants. Revoked parent grants cease to be effective for descendants immediately.
- An active seller must retain an active owner's ACTIVE account. Multiple owners are supported. Add a replacement owner through the admin route, reparent managers if necessary, then deactivate the former owner. Suspend the seller first if no replacement exists.

## Platform administrator resources

All require the live platform-admin flag and an ACTIVE account. Existing searchable `/admin/users`, `/admin/sellers`, overview, analytics and setup-draft creation/list/archive remain available.

| Method/path | Contract |
| --- | --- |
| GET `/admin/sellers/{sellerId}` | Lifecycle/profile detail with version. |
| GET `/admin/users/{userId}` | Status/admin flag with version; no credentials returned. |
| GET `/admin/setup-drafts/{id}` | Decision, owner, created seller and version. |
| POST `/admin/setup-drafts/{id}/approve` | `expectedVersion,ownerUserId,reason`. Creates the proposed seller **inactive**, OWNER membership and default policy in one transaction; archives the draft and records review metadata/audit. Existing active account required; no email/invitation is fabricated. |
| POST `/admin/setup-drafts/{id}/reject` | `expectedVersion,reason`; archives with decision, creates no seller. |
| POST `/admin/sellers/{sellerId}/activate` or `/suspend` | `expectedVersion,reason`; activation requires a viable owner. Hours/catalog may then be configured by that owner. |
| POST `/admin/users/{userId}/status` | `status,expectedVersion,reason`; status is ACTIVE/SUSPENDED/DISABLED. Suspension/disabling revokes sessions; reactivation does not revive them. |
| PUT `/admin/users/{userId}/platform-admin` | `enabled,expectedVersion,reason`; cannot remove the last ACTIVE admin. |
| GET/POST `/admin/sellers/{sellerId}/memberships`, PATCH `.../{id}`, PUT `.../{id}/capabilities` | Same bodies/invariants as seller commands; admin may assign OWNER. Administrative access is explicit. |
| GET/POST `/admin/catalog/categories`, PUT `/admin/catalog/categories/{id}` | Creation/replacement body: `id,name,sortOrder,active`. Deactivation affects every assigned seller; no deletion. |
| GET `/admin/dashboard/metrics` | Platform operations counts; optional `from,to` bound order counts. Member/catalog/account counts are current state. No verified revenue is inferred. |
| GET `/admin/audit` | Existing pagination/action/targetType plus `sellerId,targetId` filters. Successful management, order-decision and export actions are audited. |

Approval is one decision per draft. Repeating a stale version returns 409; GET the draft to recover the created seller after a lost response. Duplicate item IDs, memberships and proposed seller IDs are rejected without creating duplicate entities. Existing payment/cart/bulk idempotency remains unchanged.

## Transactions and compatibility

Management services own JDBC transactions and reuse existing authorization/SQL/audit helpers. Account/seller/grant lifecycle changes take an exclusive PostgreSQL advisory transaction lock; ordinary guarded seller/admin writes take its shared counterpart before row locks. This serializes permission changes against writes without adding a service or database. Concurrent owner removals and stale catalog edits are covered by integration tests. The lock is intentionally coarse for lifecycle changes; monitor contention before introducing finer locking.

New edits require versions; legacy catalog and operations callers may initially omit their optional precondition. They retain last-writer behavior until migrated. New catalog/private state reads provide versions without changing existing response DTO shapes. Non-destructive migration defaults preserve existing rows.

Individual seller transitions retain demo semantics and the existing `bulk.view`/`bulk.respond` permissions: simulated-payment -> ACCEPTED/REJECTED; ACCEPTED -> READY -> COMPLETED. Repeating an already reached supported status is a no-op. Invalid/cross-seller transitions fail, and no transition verifies payment or deducts stock. Real paid fulfillment and inventory remain outside this implementation; the existing confirmed-pickup placeholder is not populated with unpaid orders.

Validation failures use 400, missing/expired sessions 401, denied privileges 403, absent/out-of-scope entities 404, and stale/conflicting lifecycle/constraint changes 409. Existing commerce business-rule errors retain 422. Mutations and audit insertions commit together. No shared Supabase migration or deployment has been performed.

## Local verification (2026-10-31)

`compileKotlin compileTestKotlin test --no-daemon --max-workers=1`: **PASS**, 2m39s, **63 tests, zero failures/errors/skips**, using disposable PostgreSQL 18.6 on `127.0.0.1:55437`. Fourteen management tests cover permissions, ownership, lifecycle, concurrent edits, CSV, restart preservation and a V047 upgrade preserving account/order/UAT data. Existing regressions and fake-gateway tests also pass. Final artifacts: [HTML test report](../../backend/build/reports/tests/test/index.html), [run log](../../backend/build/management-test-final.log) (ignored local files).

All 16 V001-V047 migration files match the integrated HEAD. Live Supabase metadata remains independently unverified because no authorized read-only connection is configured. Android and PhonePe gateway/payment-operation files are unchanged. Earlier test runs exposed outdated export/migration/status expectations and audit-fixture cleanup; those were corrected before the final successful run.
