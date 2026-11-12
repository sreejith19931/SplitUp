# SplitUp App: Comprehensive Page-by-Page, Function-to-Function & Field-to-Field Analysis & Testing Report

This report provides an exhaustive, granular analysis of every screen, component, button, field, function, and state management flow within the **SplitUp** Android application, along with targeted fixes, code improvements, and future roadmap suggestions.

---

## 1. Executive Summary & Architecture Overview

SplitUp is a modern, feature-rich personal finance and expense-sharing Android application engineered using **Jetpack Compose**, **Jetpack Navigation 3**, **Room Database**, **Coroutines & StateFlow**, **Firebase Authentication**, **Google Cloud Sync / Firestore integration**, **PDF/CSV Export**, and **ML/Heuristic Receipt OCR Scanner**.

### Core Architecture Components:
- **UI Layer**: Jetpack Compose (Material 3), Navigation 3 (`NavDisplay`, `NavKey`), edge-to-edge layout, responsive components.
- **ViewModel & State Management**: `FinanceViewModel` (reactive `StateFlow` streams for expenses, groups, budgets, analytics), `AuthViewModel` (Firebase Auth & Demo mode management).
- **Data & Persistence Layer**: `AppDatabase` (Room), `FinanceDao`, `FinanceRepository`, `CloudSyncManager` (Firestore sync), `ReceiptProcessor` (OCR & regex item extraction), `CurrencyApiService` (live exchange rates), `PdfGenerator` (iText/Canvas PDF generation).
- **Testing Suite**: JUnit unit tests (`FinanceViewModelTest`, `ReceiptProcessorTest`), UI instrumentation tests (`SplitUpUiTest`).

---

## 2. Page-by-Page, Function-to-Function, Button-to-Button & Field-to-Field Analysis

### A. Authentication & Security Flow (`AuthScreen`, `EmailVerificationScreen`, Biometric Lock)
- **Fields**: Email input field, Password input field, Confirm Password (on Register mode), Display Name (on Register mode).
- **Buttons / Actions**:
  - `Sign In` / `Register` toggle tab.
  - `Submit` button (triggers Firebase Auth / validation).
  - `Continue as Demo User` button (local offline guest mode).
  - `Sign Out` & `Resend Verification` (on email verification pending screen).
  - Biometric Prompt Overlay (`BiometricLockOverlay`) triggered on app resume when biometric lock is enabled in settings.
- **Analysis & Findings**:
  - Clean separation between Firebase Auth and local guest mode.
  - Email verification check works robustly.
  - Biometric lock correctly hooks into Activity Lifecycle `ON_STOP`.
- **Potential Improvements**:
  - Add Google Sign-In / Credential Manager integration for frictionless authentication.
  - Add password visibility toggle (`VisualTransformation`) on password fields.

---

### B. Dashboard Screen (`DashboardScreen`, `SpendingChartComponent`)
- **Fields & Displays**:
  - User greeting, Net balance summary card (Total Spent, Total Owed to You, Total You Owe).
  - Monthly budget progress bar and alert banner.
  - Interactive spending chart (Pie/Bar breakdown by category).
  - Recent expenses list with category icons and amounts.
- **Buttons / Actions**:
  - Quick action chips (`Add Expense`, `Scan Receipt`, `Groups`, `Search`).
  - Expense item click -> Opens expense detail/edit (`AddExpenseRoute`).
  - View All Expenses -> Navigates to `PersonalExpensesRoute`.
- **Analysis & Findings**:
  - Highly responsive layout with Material 3 cards.
  - Budget warning system correctly triggers snackbars via `FinanceViewModel`.
- **Potential Improvements**:
  - Add pull-to-refresh functionality for syncing data with cloud Firestore.
  - Make chart interactive (tap category slice to filter expenses).

---

### C. Personal Expenses & Add/Edit Expense (`PersonalExpensesScreen`, `AddExpenseScreen`)
- **Personal Expenses Screen**:
  - **Fields**: Global search bar, category filter chips, date range filter.
  - **Buttons / Actions**: Floating Action Button (FAB) to add expense (`AddExpenseRoute`), swipe-to-delete or click-to-edit expense items, export transactions.
- **Add/Edit Expense Screen**:
  - **Fields**: Title/Description, Amount, Currency dropdown, Category selector, Date picker, Split type (Equal, Percentage, Exact, Shares), Receipt attachment indicator.
  - **Buttons / Actions**: `Save Expense`, `Attach Receipt` (launches camera/gallery scanner), category selector dialog.
- **Analysis & Findings**:
  - Robust expense splitting model (`ExpenseModel`, `SplitDetail`).
  - Supports multiple split types (`EQUALLY`, `EXACT`, `PERCENTAGE`, `SHARES`).
- **Potential Improvements**:
  - Add currency symbol formatting dynamically based on selected currency.
  - Add input validation error messages directly below amount and title fields.

---

### D. Groups & Group Details (`GroupsScreen`, `CreateGroupScreen`, `GroupDetailScreen`, `AddGroupExpenseScreen`)
- **Groups Screen**:
  - List of active expense groups with member avatars and net balances.
  - **Buttons**: `Create Group` button (`CreateGroupRoute`), group card click (`GroupDetailRoute`).
- **Create Group Screen**:
  - **Fields**: Group Name, Group Description, Currency, Member add/remove dynamic fields.
  - **Buttons**: `Add Member`, `Save Group`.
- **Group Detail Screen**:
  - **Tabs/Sections**: Expenses list, Member Balances summary, Simplified Debts (`Settle Up` calculation).
  - **Buttons / Actions**: `Add Group Expense`, `Settle Up` button (records payment between members), `Export Group Report` (generates PDF/CSV), Share deep link (`splitup://group?id=...`).
- **Add Group Expense Screen**:
  - **Fields**: Description, Amount, Paid By (dropdown of group members), Split With (checkboxes for members), Split method breakdown.
  - **Buttons**: `Save Expense`.
- **Analysis & Findings**:
  - Outstanding implementation of debt simplification algorithms and multi-member expense splitting.
  - Deep linking support (`splitup://group?id=...`) allows sharing group links directly.
- **Potential Improvements**:
  - Add push notifications or real-time Firestore sync status indicator in group detail header.
  - Add direct UPI / PayPal / Venmo payment intents when clicking "Settle Up".

---

### E. Receipt OCR & Smart Scanner (`ReceiptScannerScreen`, `ReceiptExtractionScreen`, `ReceiptAllocationScreen`, `ReceiptProcessor`)
- **Receipt Scanner Screen**:
  - Camera preview / Gallery picker for receipt image.
  - **Buttons**: `Capture Receipt`, `Pick from Gallery`, `Process Receipt`.
- **Receipt Extraction Screen**:
  - Displays extracted merchant name, date, subtotal, tax, tip, and line-item table parsed via `ReceiptProcessor` heuristic regex engine.
- **Receipt Allocation Screen**:
  - Allows assigning specific receipt line items to different group members for precise bill splitting.
- **Analysis & Findings**:
  - Sophisticated regex and text parsing logic (`ReceiptProcessor`) handles tax, tip, and line items without requiring paid external OCR APIs.
- **Potential Improvements**:
  - Integrate ML Kit Text Recognition for more robust on-device OCR accuracy across varied receipt formats.

---

### F. Global Search (`GlobalSearchScreen`)
- **Fields**: Real-time search query input field.
- **Displays**: Combined search results across personal expenses, group expenses, and groups.
- **Buttons**: Click item to navigate directly to expense edit or group detail.

---

### G. Settings & Preferences (`SettingsScreen`)
- **Fields & Toggles**:
  - Currency selection dropdown (USD, EUR, GBP, INR, etc.).
  - Monthly budget limit input field.
  - Dark/Light/System theme selector.
  - Biometric lock toggle.
  - Cloud Sync status & account info.
  - Data backup & restore buttons.
  - CSV/PDF Export options.
- **Buttons**: `Sync Now`, `Backup to JSON`, `Restore from JSON`, `Clear All Data`, `Sign Out`.

---

## 3. Code Quality, Testing & Reliability Analysis

### Unit & UI Testing
- `FinanceViewModelTest`: Tests expense insertion, budget calculations, and balance logic.
- `ReceiptProcessorTest`: Validates regex parsing of merchant names, line items, tax, and totals.
- `SplitUpUiTest`: Instrumentation test verifying Compose UI rendering.

### Room Database & Repository
- `AppDatabase` is correctly configured with migrations and type converters.
- `FinanceRepository` abstracts DAO operations and cleanly exposes flows to `FinanceViewModel`.

---

## 4. Identified Issues, Bugs & Proposed Fixes

1. **Edge Case in Receipt OCR (`ReceiptProcessor`)**:
   - *Issue*: Some receipts with non-standard date formats or missing currency symbols may fail line-item parsing.
   - *Fix*: Enhance regex fallback patterns and add ML Kit text recognition integration.
2. **Input Validation on Add Expense**:
   - *Issue*: Entering empty strings or negative amounts in `AddExpenseScreen` can cause arithmetic exceptions in split calculations.
   - *Fix*: Add strict input sanitization and inline validation error states (`isError = true`).
3. **Cloud Sync Conflict Resolution**:
   - *Issue*: Offline edits made concurrently with cloud updates lack last-write-wins timestamp resolution.
   - *Fix*: Add `updatedAt` timestamp fields to all Room entities and reconcile conflicts in `CloudSyncManager`.

---

## 5. Future Possible Implementations & Roadmap

1. **Recurring Expenses & Subscriptions**:
   - Automated generation of monthly rent, utilities, and subscription expenses using `WorkManager` (partially scaffolded via `RecurringExpenseWorker`).
2. **UPI / Payment Gateway Integration**:
   - One-tap "Pay via UPI / Google Pay / Venmo" integration when settling debts with friends.
3. **Multi-Currency Auto-Conversion**:
   - Fully integrate live exchange rates from `CurrencyApiService` when adding expenses in foreign currencies within a home currency group.
4. **AI Financial Advisor Agent**:
   - On-device Gemini Nano integration via ML Kit Prompt API to give personalized spending advice and anomaly detection.
5. **Widget & Wear OS Companion App**:
   - Home screen widget for quick expense entry and Wear OS tile for balance tracking.
