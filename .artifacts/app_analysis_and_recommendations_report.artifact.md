# SplitUp - Comprehensive Application Analysis & Optimization Report

## Executive Summary
**SplitUp** is an Android application built using Kotlin, Jetpack Compose, Material 3, Navigation 3, Room, Firebase (Auth + Firestore), ML Kit OCR, and Retrofit. It supports personal expense tracking, group bill splitting (equal and custom splits with automated settlement calculation), receipt scanning with OCR, multi-currency live conversions, PDF report generation, and biometric security.

An analysis was conducted across all layers of the project (Architecture, Data, UI/UX, Security, Testing, and Performance). Below is the comprehensive breakdown of findings, current state, and actionable recommendations.

---

## 1. Architectural & Core Stack Overview

| Dimension | Component / Tech Stack | Status & Analysis |
| :--- | :--- | :--- |
| **Architecture** | MVVM + Repository Pattern | Clean separation between View, ViewModel (`FinanceViewModel`, `AuthViewModel`), and Repository (`FinanceRepository`, `OfflineFirstFinanceRepository`). |
| **Database** | Room ORM (`AppDatabase`) | 8 Entities (`ExpenseEntity`, `GroupEntity`, `GroupExpenseEntity`, `BudgetEntity`, `UserProfileEntity`, `ActivityLogEntity`, `RecurringExpenseEntity`, `IncomeEntity`) with Moshi JSON Type Converters. |
| **Navigation** | Jetpack Navigation 3 (`androidx.navigation3`) | Type-safe route keys (`DashboardRoute`, `PersonalExpensesRoute`, `GroupsRoute`, `GroupDetailRoute`, etc.) managed via `rememberNavBackStack` and `NavDisplay`. |
| **Cloud & Network** | Firebase + Retrofit | Firebase Authentication & Firestore real-time sync (`CloudSyncManager`). Retrofit API service (`CurrencyApiService`) fetching live Frankfurter exchange rates. |
| **OCR & AI** | ML Kit Text Recognition | Multi-pass receipt parser (`ReceiptProcessor`) supporting merchant extraction, subtotal/total/tax/tip discrimination, and price reconciliation. |
| **Background Tasks** | WorkManager (`RecurringExpenseWorker`) | Periodic daily background worker processing automated recurring subscriptions. |
| **Reports & Export** | PdfDocument + Canvas | Native multi-page PDF generation (`PdfGenerator`) with styled tables, summary cards, and share intents. |

---

## 2. Key Findings & Suggested Improvements

### A. Security & Permission Hygiene
1. **Android 13+ Notification Permission (`POST_NOTIFICATIONS`)**:
   - **Finding**: In `FinanceViewModel.kt` (`postSystemNotification`), calling `NotificationManagerCompat.from(app).notify(...)` triggers a static analysis alert when targeting Android 13+ (API 33+) if runtime notification permission isn't requested first.
   - **Fix**: Check `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)` prior to building and posting system notifications.

2. **Biometric Overlay Lifecycle**:
   - **Finding**: `MainActivity` correctly uses a `DisposableEffect` observing `ON_STOP` to lock the app when backgrounded, skipping configuration changes (`isChangingConfigurations`).
   - **Improvement**: Store the fallback PIN hashed (e.g. SHA-256) instead of plain text in `SharedPreferences`.

---

### B. Core Financial & Business Logic
1. **Member Removal & Historical Expenses**:
   - **Finding**: `removeGroupMember()` removes a member from `group.members`. However, if that member is listed in historical `GroupExpense.splitAmong` or `paidBy`, `calculateNetBalances()` uses string fallback.
   - **Improvement**: Show a warning modal if a member has unpaid balances before removal, or retain their name in past settlements while marking them inactive.

2. **Unwired Features & ViewModel Cleanup**:
   - **Finding**: `addIncome()`/`deleteIncome()` write to `IncomeEntity` in Room, whereas `AddExpenseScreen` writes income into `expenses` with category `INCOME`.
   - **Improvement**: Unified income management tab in `PersonalExpensesScreen` to display both `IncomeEntity` items and expense-based incomes.
   - **Activity Logs**: `ActivityLogEntity` and `logActivity()` exist in ViewModel. Hook activity logging to group expense creations, edits, and settlements, and add an **Activity Feed** tab in `GroupDetailScreen`.
   - **Recurring Expenses**: Expose a **Subscriptions & Recurring Expenses** management sheet in `PersonalExpensesScreen` or `SettingsScreen` to allow users to add and toggle auto-debit recurring items.

3. **Custom Split Validation**:
   - **Finding**: `AddGroupExpenseScreen` supports custom percentage/exact splits, but requires strict validation ensuring the sum of member shares equals the total amount before saving.

---

### C. UI/UX & Accessibility Enhancements
1. **Accessibility (`Semantics`)**:
   - **Finding**: `SpendingDonutChart` and `SpendingAnalyticsView` include custom `contentDescription` semantics for screen readers (VoiceOver/TalkBack).
   - **Improvement**: Ensure all interactive icons (like delete, edit, QR scanner) have explicit `contentDescription` parameters.

2. **Form Input Ergonomics**:
   - **Finding**: Decimal input fields use `formatInputNumber()` and `parseAmount()` to handle both dot and comma decimal separators gracefully.

3. **Multi-Currency Support**:
   - Live exchange rates update dynamically. When a user changes default currency in Settings, `updateDefaultCurrencyAndConvert()` converts existing local expenses, group expenses, and budgets seamlessly.

---

## 3. Testing & Code Quality Audit

- **Unit Testing**: Ran `:app:testDebugUnitTest` via Gradle build task.
  - **Results**: **12 passed, 0 failed**.
  - **Covered Modules**: OCR parsing, total vs subtotal discrimination, currency detection, group settlement calculations, uneven split rounding, member name case normalization, and budget threshold warnings.
- **UI Instrumentation**: `SplitUpUiTest` verifies Compose rendering and navigation flow.

---

## 4. Actionable Task Recommendations

1. **[Fix]** Add Android 13+ runtime permission check for `POST_NOTIFICATIONS` in `FinanceViewModel.postSystemNotification()`.
2. **[Feature]** Wire up `ActivityLogEntity` by showing a real-time **Group Activity Stream** in `GroupDetailScreen`.
3. **[Feature]** Build a **Recurring Subscriptions & Income Manager** sheet in `PersonalExpensesScreen` leveraging existing `RecurringExpenseEntity` and `IncomeEntity` endpoints.
4. **[UX]** Add strict validation in `AddGroupExpenseScreen` for custom splits to prevent rounding discrepancies.
5. **[Security]** Hash the fallback security PIN in `SharedPreferences` using SHA-256 for enhanced protection.
