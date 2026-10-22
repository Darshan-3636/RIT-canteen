# RIT Canteen UI screen inventory — Prompt 9.1 update

Status describes a reachable native interaction, not the number of reference images or source files. All `debug/...` routes are development previews pending authenticated services; “Complete” here means UI-complete, not production-operational. Reference artwork is design guidance and sometimes depicts unsupported success states.

| Planned area/state | Role | Route or entry | Reference | Audit status | Prompt 9 action |
|---|---|---|---|---|---|
| Three-slide onboarding | All | `onboarding` | Batch 5 themes 02/03/05 | Complete | Retest sequence and back |
| Welcome three choices | All | `welcome` | Batch 1 welcome | Complete | Retest entries |
| Customer OTP registration/login boundary | All | `email-entry/{intent}` | Batch 1 OTP | Complete | Retain unavailable-service truth |
| Student/Faculty profile setup | Customer applicant | `debug/profile-preview` | Batch 1 profile setup | Complete | Retest no role-selection bypass |
| Seller invitation entry | Seller applicant | `seller-onboarding-entry` | PRD §4.3 | Complete | Retain admin-only onboarding boundary |
| Home and inline category filter | Customer | `debug/customer-home-preview` | Batch 1 home; Batch 5 dark home | Complete | Retest same-page chips |
| Prepared-food entry shell | Customer | `debug/order-food` | Batch 1 menu | Complete | Main Canteen eligible prepared-food entry |
| Individual prepared-food cart and review | Customer | `debug/cart/individual`, `debug/individual/review` | Batch 1/5 customer ordering | Complete | Local line editing, subtotal and explicitly demo-only save |
| Production individual checkout and fulfillment | Customer | No active route | PRD §8–9 | Intentionally Deferred | No verified payment, acceptance or handover policy invented |
| Seller directory/profile/menu | Customer | `debug/sellers`, `debug/sellers/{id}` | Batch 1 7–9 | Complete | Navigation review |
| Item detail/eligibility | Customer | `debug/items/{id}` | Batch 1 item detail | Complete | Retain checkout boundary |
| Search, filters and empty results | Customer | `debug/search` | Batch 1 search; Batch 5 no-results | Complete | Retest combined filters |
| Favorites populated/empty | Customer | `debug/favorites` | Batch 5 dark customer | Complete | Link from account |
| Promotions | Customer | `debug/promotions` | Batch 5 customer imagery | Complete | No fabricated redemption |
| Quick reorder | Customer | `debug/quick-reorder/...` | Batch 2/5 order history | Complete | Link from account; no silent submit |
| Named bulk templates | Customer | `debug/customer/account/templates/none` | PRD §5.3 | Complete | Debug-local seller-scoped save/reuse; real account sync deferred |
| Unified Cart, independent modes | Customer | `debug/cart/{mode}` | Batch 2 21–22; Batch 5 | Complete | Individual/Bulk tabs and separate drafts; seller conflict confirmation |
| Bulk cart/minimum/seller selection | Customer | `debug/bulk/cart` | Batch 2 21–22 | Complete | Regression |
| Pickup/special request/review | Customer | `debug/bulk/pickup`, `/review` | Batch 2 23–25 | Complete | Regression |
| Negotiation/revisions/final quote | Customer | `debug/bulk/negotiation` etc. | Batch 2 27–30 | Complete | Keep seller/paid distinctions |
| Payment eligibility/expired | Customer | `debug/bulk/payment`, `/expired` | Batch 2 31–32 | Complete | No real payment success |
| Unified Orders and demo details | Customer | `debug/orders`, `debug/orders/individual/{id}`, `debug/bulk/orders`, `debug/customer/demo-requests` | Batch 5 38–39 | Complete | All/Individual/Bulk filters and clearly labeled local/sample records |
| Notification inbox | Customer | `debug/customer/account/notifications/none` | Batch 5 40 | Complete | Honest empty and explicitly local example states |
| Profile overview and editing | Customer | `debug/customer/account/profile/none`, `/edit/none` | Batch 5 44–45 | Complete | Debug-local profile with self-reported affiliation and photo picker |
| Customer settings/appearance/privacy | Customer | `debug/customer/account/settings/none` and child pages | Batch 5 46–48 | Complete | Theme, alerts, opt-out and account-service boundary |
| Customer support list/form/detail | Customer | `debug/customer/account/support/none`, `/ticket-new/none`, `/ticket/{id}` | Batch 5 41–43 | Complete | Local category, attachment, reply and escalation simulation |
| Optional RIT Stories/detail | Customer | `debug/rit-stories`, `debug/rit-stories/{theme}` | Batch 5 themes 02–10 | Complete | Reachable from Profile; legacy assets labeled |
| Seller overview/request inbox/detail | Seller | `debug/seller/overview`, `/inbox`, `/requests/{ref}` | Batch 3 49–51 | Complete | Navigation/permission regression |
| Seller counteroffer/revisions/approval/special pickup | Seller | `debug/seller/requests/{ref}/...` | Batch 3 52–54 | Complete | Version and status regression |
| Seller schedule and history | Seller | `debug/seller/schedule`, `/history` | Batch 3 55–57 | Complete | Navigation review |
| Seller individual-order dashboard | Main canteen seller | No active route | PRD §8–9 | Intentionally Deferred | Workflow pending seller validation |
| Seller menu/item/categories/availability | Seller | `debug/seller/management/...` | Batch 3 59–62 | Complete | Large-font/compact audit |
| Seller promotions/profile/hours/closures/minimum | Seller | `debug/seller/management/...` | Batch 3 63–68 | Complete | Regression |
| Seller employees/permissions/settings | Seller | `debug/seller/management/...` | Batch 3 69–71 | Complete | Owner→manager→staff regression |
| Seller analytics/trends/products/customers/promotions | Seller | `debug/seller/insights/{page}/{id}` | Batch 4 72–76 | Complete | Accessibility chart summary |
| Seller forecasting | Seller | same route | Batch 4 77 | Complete | Retain insufficient-data state |
| Seller report builder/history | Seller | same route | Batch 4 78–79 | Complete | Real XLSX deferred to backend stage |
| Seller support/notifications/payment status | Seller | same route | Batch 4 80–82 | Complete | Distinguish local vs live |
| Admin dashboard/seller/user oversight | Admin | `debug/admin/{page}/{id}` | Batch 4 83–87 | Complete | Access regression |
| Admin analytics and seller drilldown | Admin | same route | Batch 4 88–89 | Complete | Synthetic only |
| Admin support/moderation/settings/audit | Admin | same route | Batch 4 90–94 | Complete | Access/regression |
| Offline/stale presentation | Customer | `debug/customer/account/offline/none` | Batch 5 special offline | Complete | Explicit presentation only; cache/sync deferred |
| Debug Testing Hub/role selection/reset | Development | `debug/developer-panel` | N/A | Complete | Add new direct links; verify release exclusion |

Back-end-dependent services (OTP, verified profiles/payments, synchronization, real notification delivery, XLSX reports and trained forecasting) are intentionally deferred even where their UI is complete. The individual customer cart/review is a debug-local demo, not a production checkout or seller fulfillment policy. Third-party artwork rights remain unresolved.

## Audit outcome

The matrix now has 43 planned areas/states: **41 UI-complete, 0 missing, 2 intentionally deferred** (production individual checkout/fulfillment and its seller dashboard). “UI-complete” is limited to the approved, currently definable presentation; debug-only local actions are not production services. The unified Orders view composes the existing bulk-request history and a separate individual demo store; it does not convert either into a verified order.
