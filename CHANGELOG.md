# Changelog

All notable changes to the SplitUp app will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v1.0.0.html).

## [1.2.0] - 2026-11-13

### Added
- **Authentication & Security:**
  - Added Guest Mode for instant offline-first exploration without signing up.
  - Added password strength requirement badges and automated email verification polling.
  - Integrated biometric lock and 4-digit PIN protection.
- **Dashboard & Personal Expenses:**
  - Added pull-to-refresh trigger for immediate offline-first cloud synchronization with Firestore.
  - Added interactive donut chart slice-to-expense list filtering.
  - Added "Log Income" Speed Dial FAB on Dashboard.
  - Added sticky date headers with daily spending totals and Net Cash Flow Summary card.
  - Added live budget threshold warning banners when expenses approach or exceed category budget limits.
- **Group Expenses & Invitations:**
  - Added custom split allocation progress bars and quick "Settle Up" shortcut chips.
  - Implemented Group Member Invitation Request & Accept/Decline flow.
  - Enforced contact privacy protection hiding global user directory by default.
- **Search, Subscriptions & OCR:**
  - Added recent search history persistence (saved in `EncryptedPrefs`) with quick query chips on `GlobalSearchScreen`.
  - Added category selectors and edit modal support for recurring bills and subscriptions on `SubscriptionsScreen`.
  - Added Sample Receipt quick-loader button on `ReceiptScannerScreen` for instant OCR testing.
- **Settings & Profile:**
  - Added multi-word user initials extraction (e.g. `"John Doe"` -> `"JD"`).
  - Added dynamic app version footer badge (`v1.2 • Build 5`).
  - Added one-tap payment handle copy action buttons for UPI VPA and PayPal handles.
  - Added Cloud Sync Status card with manual sync triggers on `DataManagementScreen`.
- **UI Polish & Animations:**
  - Added custom pulsing loading animation component (`SplitUpLoadingAnimation`).
  - Added celebratory success dialog (`🎉 Settlement Recorded!`) on debt settlements.
  - Configured smooth Navigation 3 screen entrance transition animations (`slideInHorizontally` + `fadeIn`).
- **Universal App Links & Vercel Domain:**
  - Upgraded all QR codes and WhatsApp share links to use **`splitupfin.vercel.app`**.
  - Configured Android App Links intent filters (`autoVerify="true"`) for `/invite`, `/group`, and `/user` routes.
  - Created `index.html` web fallback landing page for users without the app installed.

### Fixed
- **Archived Groups Persistence:** Fixed bug where archived groups were not persisting locally or in cloud sync by adding `isArchived` to `GroupEntity` and Room Database migration `16 -> 17`.
- **Sign-Out Safety:** Added pre-sign-out unsynced offline changes check with a "Sync Now" warning dialog to prevent data loss.
