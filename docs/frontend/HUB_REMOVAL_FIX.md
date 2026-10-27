# Developer Testing Hub removal

The debug APK previously exported a second `MAIN`/`LAUNCHER` activity, `DebugTestingActivity`, labeled **RIT Testing Hub**. It entered a dedicated `DeveloperPanel` route. Login/logout navigation popped to the graph without removing that start route, so system Back could reveal the Hub beneath an authenticated screen.

This hotfix deletes the debug activity/manifest entry, Hub screen and route, `startInDeveloperHub`, and the unused OTP/profile/mode preview selector routes. The optional customer Stories composables were moved to `RitStoriesScreens.kt` and remain intact. The debug and release app now share the single normal `MainActivity` launcher and **RIT Canteen** label. Mock accounts, role-scoped local repositories, checkout simulator and sample QR/code are retained behind regular debug Login; release mock access remains inert.

Role-root sign-in and logout now use an inclusive pop to the navigation graph root before navigating to the authorized home or Welcome. Protected-route denials return to the current authorized home or Welcome. Normal-launcher instrumentation tests exercise role login, logout and Back behavior; package-manager and merged-manifest checks assert one launcher. A home-screen shortcut manually pinned to the old component may require the owner to remove that stale shortcut after in-place upgrade; it does not represent an exported activity in the new APK.

Exact build, device and manifest results are recorded in `FRONTEND_QA_REPORT.md` after verification. No backend code was modified.
