# Remaining work after the independent demo

1. Replace demo-only sign-in provisioning with the institution's authorized identity lifecycle and production-grade signup controls; add request throttling, abuse monitoring, operational alerts, backups, and verified database TLS configuration.
2. On the verification branch, customer favorites, preferences, notification list, bulk templates and support are connected to existing backend routes in cloud-debug mode, but require owner-approved live-account phone acceptance. Seller analytics/reports/support and platform-admin screens still need Android API wiring; their synthetic insights are hidden in cloud mode. The public operating-policy read route also requires an approved backend deployment before it can be verified on Render.
3. Complete unsupported operations: seller employee/promotion management, catalog/category creation and uploads, verified payment provider, QR redemption, kitchen fulfillment, and XLSX exports.
4. Define privacy, retention, accessibility, observability, incident response, migration rollback, and capacity policies.
5. Produce a signed release artifact with protected signing keys, distribution/update process, and full production security review. The cloud demo APK is debug-only.
6. Revisit hosting plans, database backups/availability, custom domains, and cost only after separately approved requirements and budget.
