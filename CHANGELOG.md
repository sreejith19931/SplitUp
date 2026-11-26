# Changelog

All notable changes to the SplitUp app will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.spec/v1.0.0.html).

## [1.5-alpha] - 2026-11-25

### Added
- **Investments & Assets Tracking:**
  - New `InvestmentEntity` (Room v23) with categories (Stocks, Mutual Funds, Crypto, Real Estate, Provident Fund, Gold, Bonds), broker/platform, and BUY/SELL/DIVIDEND_RETURN transaction types.
  - Full add/edit/delete with cloud sync, delta sync, and LWW conflict guards.
  - Personal Expenses screen: dedicated Investments tab with portfolio total and per-asset cards (tap to edit).
  - Dashboard "Investments & Assets" summary card; Insights "Portfolio Asset Allocation" breakdown; Investments CSV export and full-app-summary PDF section.
- **Replay Interactive Product Tour** button in About screen.

### Fixed
- Pull-path LWW guards for expenses/incomes/recurring/investments: older remote records can no longer clobber newer local edits.
- Pulled incomes/recurring/investments now insert clean (no timestamp bump) — eliminates the re-push echo every sync cycle.
- "Add Investment" button on the Investments tab now opens the Investment form (previously opened the Expense form).
- Centralized large-amount notification thresholds (income €200, expense €200, investment €500 EUR-equivalent).
- Settings row labels wrap to two lines instead of clipping under large system fonts.
- Demo tour scrim now blocks touches on background content and navigation bar; system back is owned by the tour; step badge centered above the highlighted target.
- Live FX rates persist across restarts (no more hardcoded-rate balances before first refresh); migrated to `api.frankfurter.dev`.
- Notification-shade Approve/Decline moved to WorkManager with an owner re-verification guard (no more 10s broadcast-window risk).
- PIN hashing upgraded to salted PBKDF2 (transparent migration from legacy unsalted SHA-256 on next successful unlock).
- CSV export neutralizes spreadsheet formula injection (=, +, -, @ prefixes).
- `user_lookup` collection replaces full `users` collection scans for contact matching and member-uid resolution.

## [1.4] - 2026-11-15

### Added
- **Actionable Push Notifications & Dedicated Channels:**
  - Implemented background `NotificationActionReceiver` for inline **Approve** and **Decline** quick actions directly in the notification shade without opening the app.
  - Configured 4 dedicated Android notification channels with distinct importance levels (Budget alerts, Group approvals, Payments, Subscriptions).
  - Added precise deep-linking routing straight to target screens and groups.
- **Group Governance & Owner Approvals:**
  - Added creator-only `requireApproval` settings and approval workflow (`GroupApprovalRequestEntity`) for unowned group expense edits and deletions.
  - Configured default group sorting by latest activity (`updatedAt` and expense timestamps).
- **Full-Screen Notifications Hub:**
  - Replaced legacy notification sheets with a full-screen `NotificationsScreen` featuring smooth vertical slide-in transitions and direct alert navigation.
- **Smart Budgets & AI OCR Enhancements:**
  - Integrated category spending limits, mid-month pacing rate forecasting, and ML Kit receipt scanning for discounts and coupons.
- **Security & Offline Sync:**
  - Added configurable **Biometric Lock Timeout Delay** (*Immediately, 1m, 5m, 15m*) in Security Settings.
  - Added real-time **Offline Sync Badge** and robust multi-user Cloud Firestore persistence.
- **Interactive Product Demo Tour:**
  - Implemented a window-level guided product walkthrough (`DemoTourOverlay`) that automatically navigates users through Dashboard, Quick Actions, Personal Expenses, Groups, and Settings with full system-bar scrim protection.
- **Security PIN Enhancements:**
  - Upgraded Fallback PIN setup during onboarding and security settings to require double-entry confirmation (`Confirm 4-Digit PIN`).
- **3-Dot Overflow Menus:**
  - Added clean `MoreVert` 3-dot overflow menus to the top app bars for Personal Expenses and Groups screens.
- **JIT Onboarding & Compact UI:**
  - Streamlined onboarding by replacing upfront permission walls with Just-In-Time (JIT) requests and optimized compact group cards.

### Fixed
- Fixed auto-distribute exact split residual distribution bug.
- Standardized group and personal expense card font scales for visual balance.
- Fixed notification clear persistence bug by adding `dismissedNotifications` to `remember` keys.
- Fixed group "Settled up" status calculation to verify pending member transfers.
- Fixed archived group tab selection lock in `GroupsScreen`.
- Fixed fallback PIN recognition bug by aligning custom hash storage with `"security_pin_hash"`.
- Suppressed positive cash flow banner when net cash flow is exactly zero.
- Configured guest fallback email to `user@splitupfin.vercel.app` and deactivated profile editing in guest mode.

## [1.3] - 2026-11-13

### Added
- **Spending Insights & Trends:** Added `InsightsScreen` with monthly overview and category breakdown.
- **App-Wide UI Polish:** Added pulsing loading animation, celebratory settlement dialog, and Navigation 3 transitions.
- **Universal App Links & Vercel Domain:** Upgraded to `splitupfin.vercel.app` with Android App Links verification.

## [1.2] - 2026-11-13

### Added
- **Authentication & Security:** Guest mode, biometric lock, 4-digit PIN, and email verification.
- **Dashboard & Personal Expenses:** Pull-to-refresh sync, interactive donut chart filtering, and cash flow summary.
- **Group Expenses & Invitations:** Split progress bars, "Settle Up" shortcuts, and member invitation workflow.
- **Search & Subscriptions:** Recent search history persistence and recurring bills manager.

### Fixed
- Fixed archived groups persistence and pre-sign-out unsynced offline changes check.
