# Android ↔ backend screen matrix (local debug demo)

All API paths below are under `/api/v1`. `SERVER_BACKED` means the displayed read or write uses a Ktor response; it does not mean payment or production readiness. Normal debug sign-in never falls back to a local identity. Release contains inert data-layer stubs and no cleartext networking permission.

| Screen / action | State | Route / DTO | Authority and caveat |
| --- | --- | --- | --- |
| Welcome, onboarding | LOCAL_DEMO_ONLY | None | Local presentation, no authorization. |
| Signup | SERVER_BACKED | `POST /auth/register` → user | Customer registration only; explicit login required. |
| Login, session restore, logout | SERVER_BACKED | `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` → bearer/user | Server UUID, membership and capability grants drive navigation. Bearer is encrypted locally; no password storage. |
| Home, seller directory, menu, search, item detail | SERVER_BACKED | `GET /sellers`, `GET /sellers/{id}/menu` → sellers/items | Four sellers, 72 IDs on pristine seed. Images and category labels are bundled by stable IDs. Prices, availability and ownership come from server. |
| Individual cart | LOCAL_DEMO_ONLY | None | Unsaved composition; server revalidates on submission. |
| Individual review, history and detail | SERVER_BACKED | `POST/GET /me/individual-demo-orders` → `DEMO_SAVED`, `demoOnly`, `paymentVerified` | Customer bearer. Never seller acceptance, payment or pickup. |
| Bulk cart, pickup and review | LOCAL_DEMO_ONLY | None | Draft only. Server is final authority for hours, closures, minimum and item validity. |
| Bulk submit, customer request list/detail | SERVER_BACKED | `POST /bulk/requests`, `GET /me/bulk/requests` → request/version/lines | Customer bearer; stable idempotency key for unchanged draft. Data is reloaded from server. |
| Seller inbox/detail, counteroffer, approval, special decision | SERVER_BACKED | `GET /sellers/{id}/bulk/requests`, `POST .../counteroffers`, `.../seller-approval`, `.../special-pickup-decision` | Seller membership and `bulk.view`/`bulk.respond` enforced by server. Expected version is sent. |
| Customer approval and withdrawal | SERVER_BACKED | `POST /bulk/requests/{id}/customer-approval`, `.../withdraw` | Customer bearer and expected version. `AGREED_AWAITING_PAYMENT` is unpaid. |
| Customer profile edit | SERVER_BACKED | `GET/PATCH /me/profile` | Own name, affiliation and department only; email is not edited. |
| Seller existing-item edit, availability, hours, closure, minimum | SERVER_BACKED | `PATCH /sellers/{id}/menu/items/{itemId}`, `/availability`, `PATCH /bulk-policy`, `PUT /hours`, `PUT/DELETE /closures/{date}` | Seller capability enforced by server. Hours do not have a read-back API; displayed editing form is a local draft. |
| Favorites, preference, notification, bulk template, customer support | LOCAL_DEMO_ONLY | Corresponding backend routes exist but Android screens still use fixture stores | Not synchronized with backend. Do not demonstrate as durable. |
| Seller employee/promotion editor, category creation, item creation, uploads | UNAVAILABLE | No backend write route | UI may show old fixture presentation; connected mutations must not be treated as persistent. |
| Seller analytics, reporting, support, admin dashboard/directory/moderation | LOCAL_DEMO_ONLY | Backend routes exist but screens still use synthetic fixtures | Not verified revenue or backend state. Report export is unsupported by backend (501). |
| Payment, QR, pickup/fulfilment | UNAVAILABLE | No trusted server endpoint | Offline simulator is not mounted as connected commerce; no simulated success is mapped to server state. |

Backend contracts and mounted route implementations are in `docs/backend/*` and `backend/src/main/kotlin`. This matrix deliberately calls out remaining P1 integration gaps rather than implying that a synthetic presentation is persistent.
