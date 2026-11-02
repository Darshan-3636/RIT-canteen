# College pilot implementation progress

Branch `feat/rit-seller-admin-android-cloud-integration`, base HEAD `8c34c10804b6ed677a5112da6987fb8041c4c9db`. Changes are uncommitted and unpushed.

## Implemented in this working tree

- V049 preserves existing seller IDs while updating names; adds seller-level individual policy and per-item `INDIVIDUAL_ONLY`, `BULK_ONLY`, `BOTH` modes. Customer item detail offers Individual and Bulk cart choices only when the seller permits them. Backend cart, checkout, and bulk request paths enforce the same rule.
- Existing seller/admin management remains in V048. Admin can prepare ESB MSRIT Canteen with proposed ID `esb-msrit-canteen`; an active owner and approval are still required. No live seller was created.
- V050 writes scoped, deduplicated in-app notices for order and quote events. Customer and seller cloud inbox screens read the authenticated inbox.
- V051 adds seller promotions and targets, customer offers, quote/checkout discount validation in integer paise, and Android offer management/selection.
- V052 adds seller-scoped inventory categories, materials, units, suppliers, purchase orders, receipts, batches, ledger, wastage, adjustments, reorder rules, expiry/low-stock alerts, recipes, CSV report, and a seller Android screen. Stock updates lock rows and reject negative balances; request IDs serialize concurrent retries.
- V053 adds a friendly pickup reference and customer-held code. A seller must enter that code to mark a demo individual order handed over. The backend records the seller actor and time; payment remains unverified/demo.
- A public `/api/v1/catalog/snapshot` response lets Android fetch the seller directory and menus in one request, with a fallback to existing endpoints for older servers. Admin cloud UI now lists and handles support tickets and exports audited platform CSV reports; seller cloud UI can view, reply and escalate routed customer tickets. Cloud quick reorder uses saved server order history and reopens current items for a fresh Individual/Bulk choice.

## Verification

- Disposable PostgreSQL 18 on `127.0.0.1:55433`: selected management and commerce integration suites passed, **29 tests, 0 failed, 0 skipped**. A separate focused catalog snapshot test passed. The older disposable `rit_canteen_test` has a stale draft V049 checksum; the suite used the newly created `rit_canteen_test_v053` instead. No shared database was touched.
- Android debug/release Kotlin compilation and debug unit tests passed after the seller/admin support, quick-reorder and platform report UI changes: **154 tests, 0 failed, 3 skipped**.
- No startup or latency timing has been measured. Catalog loading reduces its initial network request count from one directory plus one menu per seller to one snapshot request when the new backend is available.

## Still required before phone pilot

1. Finish remaining preview-only screens, report and support edge cases, and production wording where the workflow is actually live. Quick reorder currently reopens current items for customer choice; it does not submit a repeat order automatically.
2. Add automatic recipe consumption only after a real, verified paid fulfillment event and its idempotent integration tests exist. Current individual fulfillment and PhonePe UAT remain explicitly simulated/unverified.
3. Run the full backend and Android verification suites, measure startup/login/navigation on device, and fix any resulting issues.
4. Review V048-V053 against authorized live read-only metadata, then obtain separate approval for Supabase migration and matching Render deployment. Neither was modified or deployed. The current live backend may return 404 for the new routes.
5. Build and install on the phone only after the remaining application work is complete, as requested.
