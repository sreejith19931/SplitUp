# SplitUp Comprehensive App Testing Checklist

This comprehensive, page-by-page, feature-by-feature, and function-by-function testing checklist is designed for the **SplitUp** expense sharing and personal finance application. It covers UI validation, business logic, date transitions, subscription calendar pickers, adaptive layouts, biometric security, and cloud sync.

---

## 1. Dashboard Screen & Analytics (`DashboardScreen.kt`, `SpendingChartComponent.kt`)

| Feature / Component | Function / Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **Summary Cards** | Total Balance / Owe / Owed calculation | Accurately aggregates all active group balances and personal net worth. | `[ ]` |
| **Interactive Category Donut Chart** | Tap category slice or legend card | Displays exact amount and percentage tooltip popup; filters expense list by tapped category. | `[ ]` |
| **Empty States & Shimmer Loaders** | View empty lists or loading states | Displays clean `EmptyStateComponent` with actionable buttons ("Add Expense", "Scan Receipt") or shimmer skeleton loaders. | `[ ]` |
| **Pull to Refresh** | Swipe down gesture | Refreshes view model state and re-fetches latest database records without crashing. | `[ ]` |

---

## 2. Date Transitions & Formatting (`ExpenseModel.kt`, `DateUtils`)

| Feature / Component | Function / Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **Today Date Button** | Add expense choosing "Today" | Displays `"Today, h:mm a"` with current time. | `[ ]` |
| **Yesterday Date Transition** | View expense added yesterday on the next calendar day | Automatically transitions display label to `"Yesterday"` without showing time beside it. | `[ ]` |
| **Older Date Transition** | View expense after 2+ days have passed | Displays concrete date (e.g., `"Nov 11, 2025"`) without time. | `[ ]` |

---

## 3. Subscriptions & Calendar Picker (`SubscriptionsScreen.kt`)

| Feature / Component | Function / Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **Frequency Buttons Order** | Open Add Subscription dialog | Frequency chips appear in exact order: **Weekly**, **Monthly**, **Yearly**. | `[ ]` |
| **Calendar Due Date Picker** | Tap Next Due Date field | Opens Material 3 `DatePickerDialog` calendar picker; selected date formats correctly as `"MMM dd, yyyy"`. | `[ ]` |

---

## 4. Groups Management & Details (`GroupsScreen.kt`, `GroupDetailScreen.kt`)

| Feature / Component | Function / Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **Adaptive Multi-Pane Layout** | Open Groups on tablet/foldable or wide screen | Renders side-by-side List-Detail view (`ListDetailPaneScaffold`) responsively. | `[ ]` |
| **Group Expense Deletion Sync** | Delete a group expense | Deletes subcollection document `/groups/{groupId}/expenses/{expenseId}` from Firestore immediately and does not resurrect on sign-out/in. | `[ ]` |
| **Group Detail & Balances** | Member balance breakdown and debt simplification | Calculates net standings and multi-person debt settlement vectors accurately. | `[ ]` |

---

## 5. Security, Biometrics & Cloud Sync (`SettingsScreen.kt`, `CloudSyncManager.kt`)

| Feature / Component | Function / Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **Sign Out & Sign In Sync** | Sign out, clear local cache, and sign back in | Clears `splitup_sync_prefs` (`KEY_LAST_SYNCED_AT`), restoring all cloud groups and expenses cleanly without data loss or overwrite loops (`isDirty = false`). | `[ ]` |
| **Biometric Lock & PIN Fallback** | Background app and re-open | Prompts biometric lock overlay; falls back gracefully to 4-digit PIN authentication if biometric fails or is unavailable. | `[ ]` |
