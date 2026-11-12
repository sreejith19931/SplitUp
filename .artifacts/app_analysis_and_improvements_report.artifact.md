# SplitUp App: Page-by-Page Analysis & Improvement Strategy

This report provides a comprehensive, page-by-page audit of the **SplitUp** Android application, analyzing current capabilities, potential edge cases, UX friction points, and proposing actionable improvements and bug fixes across all screens.

---

## 1. Dashboard Screen (`DashboardScreen.kt`)

### Current State
- Displays financial summary card (personal spent, group spent, net owed), budget warnings, recent personal expenses, and group summaries.
- Floating Action Button (FAB) for adding expenses.

### Improvements & Fixes
- **Unified Currency Conversion:** Ensure all aggregate totals correctly convert non-EUR currencies into the user's selected default currency (`Currencies.convertToEUR` / multi-currency support).
- **Interactive Budget Progress Bars:** Add color-coded progress bars (Green < 80%, Orange 80-100%, Red > 100%) showing budget consumption per category directly on the dashboard card.
- **Pending Settlements Widget:** Add a dedicated card highlighting pending debts where the user owes money or is owed money, with a one-tap "Settle Up" shortcut.
- **Empty States:** Provide friendly empty state illustrations when no expenses or groups exist.

---

## 2. Personal Expenses Screen (`PersonalExpensesScreen.kt` & `SpendingChartComponent.kt`)

### Current State
- Lists personal expenses with search bar, category filter chips, and spending chart component.

### Improvements & Fixes
- **Date Range Filters:** Add filter pills for "This Month", "Last 30 Days", "This Year", and custom date ranges.
- **Interactive Chart Selection:** Make the spending chart slices/bars clickable to instantly filter the expense list by that category.
- **Swipe-to-Delete with Undo:** Implement `SwipeToDismiss` on expense items with a Snackbar providing an "Undo" action.
- **Quick Edit Modal:** Allow tapping an expense item to edit its amount, category, or description inline or via bottom sheet.

---

## 3. Add Personal Expense Screen (`AddExpenseScreen.kt`)

### Current State
- Form with fields for title, amount, category dropdown, date, description, currency, and paidBy.

### Improvements & Fixes
- **Strict Input Validation & Masking:** Prevent negative values, multiple decimal points, and empty titles. Disable the "Save" button until inputs are valid.
- **Smart Category Auto-Detection:** Analyze the title string (e.g., "Uber", "Coffee", "Grocery") to automatically suggest the correct category.
- **Receipt Attachment Shortcut:** Add a button in the form to scan or attach a receipt directly while adding an expense.

---

## 4. Groups Screen (`GroupsScreen.kt`)

### Current State
- Lists user groups, total balances owed/owing per group, search bar, and navigation to Group Detail / Create Group.

### Improvements & Fixes
- **Net Balance Color Coding:** Display net balances with clear color indicators (Green for positive balance / owed, Red for negative / you owe).
- **Custom Group Icons / Avatars:** Allow choosing category icons (Trip, Home, Couple, Office, Project) instead of generic icons.
- **Favorite / Pinned Groups:** Allow pinning frequent groups to the top of the list for quick access.

---

## 5. Create Group Screen (`CreateGroupScreen.kt`)

### Current State
- Form for group name, category, and member list.

### Improvements & Fixes
- **Member Contact Integration:** Allow adding members easily with auto-complete or picking from recent contacts/friends.
- **Default Currency Setting:** Allow assigning a default currency (e.g., USD, GBP, JPY) for international travel groups.
- **Validation:** Prevent creating a group with empty name or fewer than 2 members.

---

## 6. Group Detail Screen (`GroupDetailScreen.kt`)

### Current State
- Group expense list, member net balances, simplified debt settlements, "Settle Up" action, and PDF export.

### Improvements & Fixes
- **UPI / Payment App Deep Links:** When tapping "Settle Up", generate payment deep links (Google Pay, PhonePe, Venmo, PayPal) or QR codes to settle debts instantly.
- **Step-by-Step Debt Simplification:** Show clear explanations of simplified debt transfers (e.g., "Alice pays Bob $45.00 via UPI").
- **Expense Filtering & Search:** Add search and member filtering inside group expenses.

---

## 7. Add Group Expense Screen (`AddGroupExpenseScreen.kt`)

### Current State
- Supports splitting equally, by percentages, exact amounts, and itemized receipt allocation.

### Improvements & Fixes
- **Real-Time Split Validation:** In exact amount or percentage splitting modes, validate that the sum equals 100% or the total expense amount in real-time, highlighting discrepancies in red.
- **Split Templates:** Save frequent split templates (e.g., "Me + Roommate 1", "Everyone") for faster expense entry.

---

## 8. Receipt Scanner & OCR Pipeline (`ReceiptScannerScreen.kt`, `ReceiptExtractionScreen.kt`, `ReceiptAllocationScreen.kt`)

### Current State
- Multi-step wizard: Capture/Pick image -> OCR Simulation -> Extraction Review -> Itemized Allocation.

### Improvements & Fixes
- **Real ML Kit Integration:** Ensure Google ML Kit Text Recognition is fully integrated for live camera capture alongside simulation fallback.
- **Confidence Badges:** Display OCR confidence scores on extracted fields (Merchant, Tax, Tip, Items) so users know what needs manual verification.
- **Image Cropping:** Provide basic cropping tools before OCR extraction to improve accuracy.

---

## 9. Settings & Profile Screen (`SettingsScreen.kt`)

### Current State
- Theme switcher, default currency selector, biometric lock toggle, backup restore JSON, and about info.

### Improvements & Fixes
- **Biometric Authentication Prompt:** Implement actual `BiometricPrompt` verification when biometric lock is enabled, rather than just toggling a boolean state.
- **Cloud Backup / Sync Integration:** Add Google Drive or Firebase Firestore backup options.
- **Export Data Options:** Provide one-tap export of all expenses and groups to CSV or JSON backup files.
