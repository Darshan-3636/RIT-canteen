# Feature-to-backend audit (verification branch)

Baseline: `21d57494acb3eacc7d297dfed64666e9ca622cf8`. This matrix traces the debug **cloud** build, not the offline UI fixture build. `/api/v1` prefixes all routes. `P` means state survives Android process restart; `S` means it survives a backend restart (assuming its PostgreSQL database persists). Server tests use disposable localhost PostgreSQL; no authenticated write was made to Render. `SERVER_BACKED_AND_VERIFIED` refers to source tracing plus local integration tests, **not** live-account phone acceptance.

| Screen/action | Cloud source → endpoint | Gate | P/S | Status and evidence / outstanding work |
|---|---|---|---|---|
| Welcome/login/logout/restore | encrypted bearer, `/auth/login`, `/auth/me`, `/auth/logout` | account/session | Y/Y | SERVER_BACKED_AND_VERIFIED: AuthIntegrationTest, app auth tests; live credentials not used |
| Signup | none | disabled in hosted demo | N/N | BLOCKED_BY_EXTERNAL_PROVIDER: owner-approved registration/provisioning design needed |
| Profile read/edit | `/me/profile` GET/PATCH | own bearer | Y/Y | SERVER_BACKED_AND_VERIFIED: update only after successful response |
| Four-seller directory | `/sellers` | public read | Y/Y | SERVER_BACKED_AND_VERIFIED: local 4-seller DB test and public Render GET |
| Seller menus / 72 products | `/sellers/{id}/menu` | public read | Y/Y | SERVER_BACKED_AND_VERIFIED: 72-row parity test; image mapping remains bundled by stable ID |
| Home categories, search, product details | in-memory projection of fetched menus | public read | Y/Y* | SERVER_BACKED_NOT_VERIFIED on phone; server catalog is authoritative, UI selection is transient (*reloads after restore) |
| Product prices / availability | fetched menu; server revalidates writes | customer bearer for orders | Y/Y | SERVER_BACKED_AND_VERIFIED in local Commerce tests; stale price may reject submission |
| Favorites toggle/list | `/me/favorites` GET/PUT/DELETE | own bearer | Y/Y | SERVER_BACKED_NOT_VERIFIED on device; new UI transport, no local success fallback |
| Preferences toggles | `/me/preferences` GET/PATCH | own bearer | Y/Y | SERVER_BACKED_NOT_VERIFIED on device; new UI transport, server-confirmed result |
| Notifications list | `/me/notifications` GET | own bearer | Y/Y | SERVER_BACKED_NOT_VERIFIED on device; read/mark-read UI not connected |
| Support list/create/detail/reply/escalate | `/me/support/tickets` and child routes | own bearer; backend scope | Y/Y | SERVER_BACKED_NOT_VERIFIED on device; creation/reply use client idempotency IDs |
| Bulk templates list/save/edit/delete | `/me/bulk-templates` and `/{id}` | own bearer | Y/Y | SERVER_BACKED_NOT_VERIFIED on device; server validates ownership; templates are not an order |
| Individual prepared-food cart | transient UI + fetched prices | customer for submission | N/N | LOCAL_ONLY until submission; cart is intentionally not persisted |
| Individual demo-order submit/history | `/me/individual-demo-orders` POST/GET | own bearer | Y/Y | SERVER_BACKED_AND_VERIFIED locally; demo order is unpaid, not kitchen-fulfilled |
| Bulk cart / review | transient UI + fetched catalog | customer for submission | N/N | LOCAL_ONLY draft; seller-specific minimum rechecked by backend |
| Pickup policy/hours/closures | new `/sellers/{id}/operating-policy` GET | public read | Y/Y | SERVER_BACKED_AND_VERIFIED locally; **not deployed on Render baseline**. Until deployed, app says unverified, never displays invented cloud hours |
| Bulk request create/history | `/bulk/requests` POST; `/me/bulk/requests` GET | own bearer, idempotency key | Y/Y | SERVER_BACKED_AND_VERIFIED locally; ambiguous timeout requires history reconciliation |
| Customer approval/withdrawal | `/bulk/requests/{id}/customer-approval`, `/withdraw` | own bearer, expectedVersion | Y/Y | SERVER_BACKED_AND_VERIFIED locally; backend rejects stale versions |
| Seller bulk inbox/counteroffer/approval/special decision | seller-scoped bulk routes | membership + capability | Y/Y | SERVER_BACKED_AND_VERIFIED locally; other-seller access denied |
| Seller existing-item edit/availability | seller menu PATCH routes | `catalog.edit` / `availability.edit` | Y/Y | SERVER_BACKED_AND_VERIFIED locally; creation/category management not API-backed |
| Seller hours/minimum/closures | seller policy PATCH, hours/closures PUT/DELETE | seller capability | Y/Y | SERVER_BACKED_AND_VERIFIED locally; new read-back awaits deployment |
| Seller dashboard/schedule/history | bulk list and `/pickup-schedule` | seller capability | Y/Y | SERVER_BACKED_NOT_VERIFIED on phone; other analytics widgets not connected |
| Seller employees/promotions/category management | local fixture UI only; no mounted API | seller capability in preview only | N/N | MISSING_BACKEND_ENDPOINT: cloud local mutations disabled, cannot claim save |
| Seller analytics/reports/support | backend dashboard, analytics, report selections, support routes exist; Android `DevelopmentInsights` not wired | seller capability | Y/Y on server, N/N UI | SERVER_BACKED_NOT_VERIFIED API; LOCAL_ONLY UI explicitly unavailable in cloud. Android wiring remains |
| Platform admin sellers/users/audit/setup | `/admin/*` exists; current Android admin fixture not wired | server platform-admin | Y/Y on server, N/N UI | SERVER_BACKED_NOT_VERIFIED API; cloud admin fixture explicitly unavailable. Owner-controlled admin provisioning and Android wiring remain |
| Promotions discovery | bundled illustration only | public | N/N | MOCK_OR_FIXTURE in local build, empty in cloud; no operational backend promotion API |
| Sample negotiations/order history | sample fixtures | local preview | N/N | MOCK_OR_FIXTURE disabled in cloud; server history is separate |
| Payment/QR/collection | no provider or authoritative verified-paid route | not available | N/N | BLOCKED_BY_EXTERNAL_PROVIDER; cloud checkout simulation disabled; workflow stops at `AGREED_AWAITING_PAYMENT` |

The backend has 38 passing isolated-PostgreSQL tests (including policy and engagement). Android cloud mode has 150 test cases, 0 failures, 54 fixture-only skips. The local-fixture mode ran 150 cases with 0 failures and 3 cloud-only skips; those results must not be counted as cloud feature verification. The Moto G60 was not connected during this pass, so phone visuals, force-stop/reopen, and live authenticated paths remain unverified. See `INTEGRATION_COMPLETION_REPORT.md` for final run details.
