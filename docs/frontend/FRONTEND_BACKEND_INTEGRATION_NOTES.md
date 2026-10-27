# Frontend/backend integration notes

No backend files or frozen API contracts are changed by this branch. The normal debug login uses `DevelopmentDemoAccounts` and `DevelopmentPreviewAccess`; release mock login is inert. When server authentication is integrated, the app must map a server-issued identity/role/seller membership to `AuthorizedIdentity` without letting the user choose seller/admin privileges. `PresentationAccess` is presentation-only; every operation needs server-side authorization.

## Replaceable seams

- `DemoCheckoutRepository` is the client-only prototype contract; `DevelopmentDemoCheckout` in `app/src/debug` implements in-memory individual submission, seller response, payment-outcome simulation, token creation and pickup redemption. The release counterpart denies all operations. It must **not** be reused as a production payment or fulfillment repository.
- `BulkOperationsRepository` and `DevelopmentSellerOperations` own local versioned bulk negotiation. A future backend adapter must preserve seller scoping, current revision numbers, both approvals on the same revision, special-pickup approval, expiry and one-seller minimum.
- `IndividualCartSession` and `BulkDraftSession` are UI draft state, not submitted orders. Revalidate eligibility, stock, hours, totals and seller minimum on the server at submission.
- `DevelopmentDemoAccounts` and `DevelopmentSellerBusiness` are fixture identities and capabilities. Server role grants and seller membership must replace them before real operations.

## Trust boundary

The checkout screen offers user-selected `SUCCESS`, `FAILURE` and `PENDING` **simulated** results. These never contact a payment provider. `DEMO_CONFIRMED`, the random QR/text code and `DEMO_COLLECTED` live solely in debug memory. They are not receipts, verified revenue, real pickup credentials or kitchen tickets. The QR payload is only `RIT-DEMO:<opaque random code>`; the seller uses manual code entry. No camera permission or app scanner is present.

The local redemption record stores a demo actor ID and time for traceability within this process; it is not a trusted audit log. Production requires a server-owned order/payment state machine: payment-provider webhook/verification, idempotent order creation, revocable seller-scoped pickup token and atomic single-use redemption with audit trail. A client-chosen outcome or local token must never update real payment, seller order or pickup records. Real OTP/email, live availability, kitchen acceptance/readiness and real analytics remain outside this branch.
