# Changelog

All notable changes to the SplitUp app will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.spec/v1.0.0.html).

## [1.4] - 2026-11-15

### Added
- **Dedicated Full-Screen Notifications Hub & Vertical Transitions:**
  - Replaced notification bottom sheet with a full-screen `NotificationsScreen` opening with a smooth vertical bottom-to-top slide-in transition.
  - Wired active notification alerts (group balances, budget warnings/exceeded, subscriptions) to navigate directly to their relevant pages (`BudgetScreen`, group detail, subscriptions).
  - Fixed approval request rejection bug so declining an expense edit request instantly removes it from the pending approvals queue.
  - Removed redundant action text links ("Manage Budget", "View Group") from notification cards.
- **Default Group Activity Sorting:**
  - Configured group list streams (`groups` StateFlow) to sort groups by default according to latest activity (`updatedAt` and expense timestamps).
  - Automatically updated group activity timestamps on expense additions, edits, deletions, and settlements.
- **Dashboard Budget Exceeded Banner Navigation:**
  - Updated the `"⚠️ X budget exceeded"` suggestion chip on the Dashboard to navigate directly to the **Monthly Category Budgets** screen (`BudgetScreen`).
- **Unowned Group Changes Approval System & Creator Settings:**
  - Added pending approval request workflow (`GroupApprovalRequestEntity`) for unowned group expense edits, deletions, and member removals.
  - Added creator-only `requireApproval` group setting toggleable in `CreateGroupScreen` and `GroupDetailScreen`.
  - Added approval notifications and inline review cards with **Approve** and **Decline** options.
- **Universal Activity Log & Multi-Level Undo Engine:**
  - Extended activity logs with serialized JSON payload state for personal expenses, personal incomes, group expenses, settlements, and member removals.
  - Integrated one-tap **Undo** actions across activity feed items and 10-second action popups.
- **Member Identity, Contact Resolution & Name Sync:**
  - Added `autoMergeGuestMembersOnJoin` and email-anchor matching (`group.memberEmails`) to link pre-joining guest expenses and custom phone nicknames (*"Ali Bro"*) to registered profiles (*"Alice Johnson"*).
  - Synchronized Edit Profile display name updates across all shared groups without breaking member identity or pending debt calculations.
- **Group Details UI/UX & Settlement Refinements:**
  - Consolidated TopAppBar action buttons into a three-dot overflow menu with icons and titles.
  - Simplified member balance status display and added smooth scroll with 3-times blinking animation on **"Who Owes Whom?"** when tapping member balance rows.
  - Integrated Android system chooser sheet for sharing group invite links and codes.
  - Added partial settlement payment recording with remaining debt tracking.
  - Added real-time foreign currency conversion display (e.g., `≈ €92.50`) using `CurrencyApiService`.
- **CSV Export & Smart Budget Forecasting:**
  - Added **Export to CSV** for personal and group expenses via `CsvExportImporter`.
  - Added mid-month daily spending rate forecasting (*"Pacing Alert: On track to reach $X by month end"*) in `BudgetScreen`.
- **Security Lock Timeout & Offline Sync Badge:**
  - Added configurable **Biometric Lock Timeout Delay** (*Immediately, 1m, 5m, 15m*) in Security Settings.
  - Added real-time **Offline Sync Badge** on Dashboard using `NetworkConnectivityObserver`.
- **Smart Budgets & OCR Enhancements:**
  - Added ML Kit OCR detection for receipt discounts and coupons.
  - Added category spending limits and real-time budget tracking (`BudgetScreen`).
- **Recurring Income & Subscription Flow:**
  - Integrated recurring income options into Add Income dialog and unified recurring bills into the Subscriptions Hub.
- **UI & UX Polish:**
  - Replaced notification bottom sheet with a full-screen compact sheet with instant "Clear All" dismissal and fly-out animations.
  - Redirected dashboard budget & cash flow banners directly to relevant pages (Personal Expenses & Insights).
  - Standardized app-wide typography scale (`15.sp` titles, `20.sp` headers) and compact layout padding with single-line tab font scale safeguards.
- **Group Activity Feed & Cloud Sync:**
  - Added group audit logs and scheduled background cloud backups via WorkManager.

### Fixed
- Fixed auto-distribute exact split residual distribution bug.
- Standardized group and personal expense card font scales for visual balance.
- Fixed notification clear persistence bug by adding `dismissedNotifications` to `remember` keys.
- Fixed group "Settled up" status calculation to verify pending member transfers.
- Fixed archived group tab selection lock in `GroupsScreen`.

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
