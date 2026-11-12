# SplitUp App: Comprehensive Analysis, Fixes & Feature Implementation Roadmap

This report provides an exhaustive, professional engineering analysis of the **SplitUp** Android application, evaluating its architecture, UI/UX, data persistence, security, and offering concrete fixes, performance optimizations, and next-generation feature implementations.

---

## 1. Architectural & Code Quality Strengths

SplitUp is built upon a modern, robust, and scalable Android tech stack:
- **UI Toolkit**: Jetpack Compose with Material 3 design system, dynamic theming (`Color.kt`, `Theme.kt`, `Type.kt`), and edge-to-edge support.
- **Navigation**: Modern Jetpack Navigation 3 (`androidx.navigation3`) with type-safe routing (`AppScreens.kt`).
- **State Management**: Reactive unidirectional data flow using Kotlin Coroutines and `StateFlow` in `FinanceViewModel` and `AuthViewModel`.
- **Local Persistence**: Room Database (`AppDatabase`, `FinanceDao`) with reactive Flow queries.
- **Advanced Features**:
  - **Firebase Auth & Cloud Sync** (`CloudSyncManager.kt`)
  - **Receipt OCR & Item Extraction** (`ReceiptProcessor.kt`, ML Kit text recognition)
  - **PDF & CSV Export** (`PdfGenerator.kt`)
  - **Currency Conversion API** (`CurrencyApiService.kt`)
  - **Recurring Expenses** (`RecurringExpenseWorker.kt` via WorkManager)

---

## 2. Identified Potential Bugs, Edge Cases & Technical Debt

| Component / File | Issue / Risk | Recommended Fix / Refinement |
| :--- | :--- | :--- |
| **Biometric Lock** (`SettingsScreen.kt`, `MainActivity.kt`) | Biometric toggle currently saves state in preferences without triggering `BiometricPrompt` verification on app resume. | Wire up androidx.biometric `BiometricPrompt` to enforce authentication upon app unlock. |
| **Expense Split Sum Validation** (`AddGroupExpenseScreen.kt`) | In exact amount or percentage splitting modes, users can save expenses where split totals do not match the grand total. | Add real-time mathematical validation checking that `sum(splits) == totalAmount`, disabling the Save button on discrepancy. |
| **Currency Conversion Fallback** (`CurrencyApiService.kt`) | Offline mode or API rate limits can cause currency conversion calculations to fail or return 0. | Implement cached fallback exchange rates in Room / DataStore with last-updated timestamps. |
| **Input Sanitization & Masking** (`AddExpenseScreen.kt`) | Amount field allows multiple decimal points and negative numbers. | Add a custom `VisualTransformation` or input filter to restrict currency formatting (`#.##`). |
| **OCR Confidence Scoring** (`ReceiptScannerScreen.kt`) | Extracted OCR fields lack confidence indicators, requiring manual checks without guidance. | Add color-coded confidence badges (Green > 85%, Yellow 50-85%, Red < 50%) to direct user attention to uncertain fields. |

---

## 3. Performance & Resource Management Improvements

1. **Pull-to-Refresh on Dashboard & Groups**:
   - Add `PullRefreshIndicator` or `pullToRefresh` modifier to `DashboardScreen` and `GroupsScreen` to trigger manual Firestore sync.
2. **Database Query Optimization**:
   - Ensure all DAO queries involving complex aggregations (e.g., net balances across groups) leverage proper SQLite indices and run off the main thread (`Dispatchers.IO`).
3. **Image Compression for Receipts**:
   - Compress captured receipt bitmaps before passing them to ML Kit OCR and storing them in local cache to prevent `OutOfMemoryError` on high-resolution camera sensors.

---

## 4. Security & Data Privacy Enhancements

1. **Credential Manager & Google Sign-In**:
   - Upgrade authentication flows to support Android Credential Manager API for seamless One-Tap Google sign-in and passkeys.
2. **Firestore Security Rules**:
   - Ensure Firestore rules enforce strict document ownership (`request.auth.uid == resource.data.userId`) for all personal and group expense collections.
3. **Encrypted SharedPreferences / DataStore**:
   - Use `EncryptedDataStore` or `EncryptedSharedPreferences` for storing auth tokens, session state, and sensitive user preferences.

---

## 5. Next-Level Feature Implementation Roadmap

### A. UPI & Payment App Deep Integration ("Settle Up")
- **Concept**: When tapping "Settle Up" in a group or individual balance view, generate instant payment deep links (`upi://pay?pa=...&pn=...&am=...`) or QR codes for Google Pay, PhonePe, Paytm, Venmo, and PayPal.
- **Impact**: Reduces friction in settling debts between friends.

### B. Smart Category Auto-Detection
- **Concept**: As the user types an expense title (e.g., "Starbucks", "Shell Gas", "Netflix"), use local keyword matching or lightweight heuristics to automatically select the matching category (Food & Drink, Transport, Entertainment) and icon.

### C. Interactive Spending Analytics & Chart Filtering
- **Concept**: Upgrade `SpendingChartComponent.kt` so that tapping a pie chart slice or category bar instantly filters the transaction list below it and shows period-over-period comparison (e.g., "+12% vs last month").

### D. Split Templates
- **Concept**: Allow users to save frequently used split ratios (e.g., "Weekend Trip: Me (50%), Roommate 1 (25%), Roommate 2 (25%)") as reusable templates when creating group expenses.

### E. Real-Time Collaboration & Cloud Presence
- **Concept**: Show active member presence indicators in group expense details when multiple users are viewing or adding expenses simultaneously via Firestore real-time listeners.
