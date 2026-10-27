# Frontend flow matrix

This branch starts at Android commit `89123939c3ad9370b486f3f7251efb26ae778780`. All routes below are native Compose. The local role accounts and operational simulator are available only in debug builds; release has no mock authentication or operational routes.

| Persona | Normal entry | Primary destinations | Details and exit |
| --- | --- | --- | --- |
| Customer (Student/Faculty) | Welcome → Log In → Customer Home | Home, Cart, Orders, Saved, Profile | Choose seller → that seller's categorized menu → item → individual/bulk Cart. Profile → account → Log out → Welcome. |
| Seller owner/manager | Same shared login → Seller Dashboard | Dashboard, Orders, Menu, Insights (when granted), Account | Orders opens individual demo requests, bulk request inbox and code verification; Menu opens business/catalog tools; Account has settings/support/logout. |
| Seller staff | Same shared login → Seller Dashboard | Dashboard, Orders, Menu, Account | Seller-scoped read views; response, payment/pickup confirmation and Insights are absent unless capability grants them. |
| Platform administrator | Same shared login → Admin dashboard | Admin's existing dashboard pages | Separate admin navigation and account; no customer/seller tabs or implicit ownership. |

Welcome has one shared prominent **Log In** and a secondary **Create Customer Account**. It has no seller self-registration fork. Customer signup cannot select a privileged role. The Debug Testing Hub remains separate from the normal journey. A denied route displays an access-unavailable state whose exit returns to the current authorized root or Welcome, never to the Hub.

## Demo journeys

```text
Main Canteen prepared item → Individual Cart → Review → Submit local demo request
→ Main Canteen individual queue → Owner/Manager accepts or rejects
→ Customer Orders → Simulated Checkout (success/failure/pending)
→ Success: demo confirmation + QR/text code → same seller verifies and confirms collection once
→ Customer Orders: Collected (demo)

One seller's eligible items → Bulk Cart → Pickup → Review → Submit local request
→ Seller Bulk Requests → exact-version approval/counteroffer + special-pickup decision
→ Customer accepts latest agreed revision → Simulated Checkout (success/failure/pending)
→ Success: demo confirmation + QR/text code → seller-scoped one-use collection
```

Payment failure and pending do not create a pickup code. Quote revision, seller, customer and state are rechecked before simulated payment and pickup. A simulated outcome is never financial proof. The debug simulator stays in memory across account switches on the same running app process; it is not a backend or persistent production order store.
