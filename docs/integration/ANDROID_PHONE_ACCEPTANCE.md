# Android phone acceptance (owner-run after approved deployment)

The Moto G60 was not visible to ADB in this verification session. These are acceptance checks, not claims of execution. Use the cloud-configured debug APK built from the same reviewed commit; never enter private passwords into Codex output.

1. On Wi-Fi, cold-start the app, sign in with an authorized account and reopen after force-stop. Verify the same account, server profile, four sellers and 72-item catalog load. Repeat on mobile data; cold starts may take longer.
2. Open a seller and switch categories/search. Verify only that seller's products, prices and availability appear; detail and cart reflect the same items.
3. With an authorized customer, save a favorite and preference, close/reopen, then remove them. Confirm server state, not a local-only “saved” toast; offline changes should show an error.
4. Create a disposable support ticket, reply and reopen it, and save/edit/remove a bulk template. Confirm data survives app restart and belongs only to that account.
5. For a disposable order approved by the owner, review one-seller bulk minimum and pickup policy. If the deployed policy endpoint returns 404, expect “hours unverified” and stop policy acceptance. If deployed, check displayed hours/closure against server data. Verify a rejected/stale submission is not shown as successful.
6. With separate authorized seller credentials (if the owner provisions them), verify inbox visibility and counteroffer/approval without cross-seller access. No account should gain seller/admin access by choosing a role in the UI.
7. Confirm checkout says payment is unavailable; no payment-success, QR pickup token or collected order can be created in cloud mode.

Do not test live write flows without the owner's explicit authorization and disposable test identities. Capture only redacted defects and steps, not tokens or passwords.
