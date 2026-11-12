# SplitUp: Comprehensive App Testing Checklist

This detailed testing checklist covers all the core functionalities of the **SplitUp** expense sharing and personal finance application, incorporating all recent architectural additions, bug fixes, and feature expansions (such as One-Click Instant Payment Integration, Smart AI Receipt Parser, Subscriptions Hub & Recurring Bill Automation, Group Activity Feed, Multi-Format CSV Export/Import, Biometric Lock with PIN Fallback, and Multi-Channel Settlement Deep Links).

---

## 1. Authentication & Security Flow (`AuthScreen.kt`, `MainActivity.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 1.1 | **Email/Password Registration** | Enter valid email, secure password, and display name; tap Register. | Creates account in Firebase Auth, sends verification email, and routes to Email Verification screen. | `[ ]` |
| 1.2 | **Email Verification Gate** | Tap "Resend Verification" or check status after verifying email. | Unlocks app entry once email is verified. | `[ ]` |
| 1.3 | **Email/Password Sign-In** | Enter registered credentials and tap Sign In. | Successfully authenticates and opens `DashboardScreen`. | `[ ]` |
| 1.4 | **Demo / Guest Mode** | Tap "Continue as Demo User". | Bypasses Firebase Auth and provisions an offline local guest account with sample data. | `[ ]` |
| 1.5 | **Biometric Lock Activation** | Go to `SettingsScreen`, toggle Biometric Security Lock ON. | Triggers system biometric prompt; upon success, immediately prompts for 4-digit PIN setup. | `[ ]` |
| 1.6 | **App Resume Biometric Overlay** | Enable biometric lock, background app, and reopen after timeout. | Displays `BiometricLockOverlay` requiring biometric auth. | `[ ]` |
| 1.7 | **PIN Fallback & Reset** | On Biometric Lock overlay, tap Cancel or fail biometrics. | Switches to 4-digit PIN verification screen; allows successful unlock with correct PIN. | `[ ]` |

---

## 2. Dashboard Screen (`DashboardScreen.kt`, `SpendingChartComponent.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 2.1 | **Summary Financial Cards** | View top summary card on Dashboard. | Accurately aggregates total spent, total owed to you, and total you owe across all active groups. | `[ ]` |
| 2.2 | **Monthly Income & Net Cash Flow** | Record income entries and view dashboard summary widget. | Displays calculated sum of monthly income and real-time net cash flow (`Income - Spent`). | `[ ]` |
| **2.3** | **Interactive Category Chart** | Tap a category slice or legend card in Spending Analytics chart. | Filters recent personal expenses instantly by the selected category and displays a clearable filter chip. | `[ ]` |
| **2.4** | **Quick Action Navigation** | Tap quick action chips (`Add Expense`, `Scan Receipt`, `Groups`, `Subscriptions`). | Smoothly navigates to respective feature screens without jank or delay. | `[ ]` |
| **2.5** | **Recent Activity Feed** | Review transaction list on Dashboard. | Shows personal and group expenses/incomes with correct date headers, category icons, and color-coded amounts (`+$X.XX` for income). | `[ ]` |
| **2.6** | **Pull-to-Refresh** | Perform downward swipe gesture on Dashboard. | Triggers ViewModel state refresh and re-fetches latest database records cleanly. | `[ ]` |

---

## 3. Personal Expenses & Income (`PersonalExpensesScreen.kt`, `AddExpenseScreen.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 3.1 | **Entry Type Toggle** | In `AddExpenseScreen`, toggle between "Expense" and "Income". | Switches transaction type. Saving as "Income" tags record with `ExpenseCategories.INCOME`, updating monthly cash flow without inflating total expenses. | `[ ]` |
| 3.2 | **Category Auto-Detection** | Type "Uber Ride" or "Netflix Subscription" in title field. | Automatically selects matching category icon and color based on `CategoryDetector` heuristics. | `[ ]` |
| 3.3 | **Numeric & Decimal Validation** | Input amounts with decimal points; test negative or empty values. | Validates input correctly, preventing arithmetic exceptions and showing inline error hints if invalid. | `[ ]` |
| 3.4 | **Save & Persist Personal Expense** | Fill title, amount, category, date, and notes; tap Save. | Persists transaction to Room database and updates personal expense feed instantly. | `[ ]` |
| 3.5 | **Search & Filter Transactions** | Use global search bar and category/date filter chips on `PersonalExpensesScreen`. | Instantly filters transaction list matching query criteria. | `[ ]` |

---

## 4. Groups & Split Management (`GroupsScreen.kt`, `CreateGroupScreen.kt`, `GroupDetailScreen.kt`, `AddGroupExpenseScreen.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 4.1 | **Group Creation** | Enter group name, description, currency, and members; tap Save. | Saves group to Room database, initializes member balances, and opens `GroupDetailScreen`. | `[ ]` |
| 4.2 | **WhatsApp Invite Integration** | Add a custom member not on SplitUp or tap invite icon in member list. | Prompts WhatsApp Invite Dialog with pre-formatted invite message and clickable WhatsApp share intent. | `[ ]` |
| 4.3 | **Group Expense Creation & Splits** | Add group expense with Equal, Exact, Percentage, or Shares split method. | Validates split totals sum up to expense amount; calculates exact share breakdown per member. | `[ ]` |
| 4.4 | **Group Income / Refund Entry** | Add group entry tagged as "Income / Refund" with negative amount. | Credits the group, reduces member debt balances accurately. | `[ ]` |
| 4.5 | **Debt Simplification** | View "Balances" and "Simplified Debts" tab in group details. | Computes optimized multi-person debt settlement chains to minimize transaction count. | `[ ]` |
| 4.6 | **Multi-Channel Settlement Deep Links** | Tap "Settle Up" and select PayPal, Venmo, Revolut, or UPI payment option. | Launches 1-tap payment deep link (`paypal.me`, `venmo.com`, `revolut.me`, `upi://pay`) with pre-filled amount and recipient handle. | `[ ]` |
| 4.7 | **Group Activity Feed & Comments** | Post a comment or view activity stream inside group details. | Appends comment/activity item instantly with timestamp and author attribution. | `[ ]` |
| 4.8 | **PDF Report Export** | Tap Export PDF in Group Details. | Compiles group financial summary and balance sheet into a clean PDF document (`PdfGenerator`) and triggers share intent. | `[ ]` |

---

## 5. Smart AI Receipt Scanner & Cost Allocation (`ReceiptScannerScreen.kt`, `ReceiptAllocationScreen.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 5.1 | **Camera Capture / Gallery Pick** | Capture receipt photo or select image from gallery in `ReceiptScannerScreen`. | Requests camera/storage permissions gracefully and loads image preview. | `[ ]` |
| 5.2 | **OCR / Receipt Extraction** | Process receipt image via `ReceiptProcessor`. | Extracts merchant name, date, tax, tip, total, and itemized line items using multi-pass regex parser. | `[ ]` |
| 5.3 | **Manual Line Item Addition** | Tap "Add Item" on Extracted Receipt screen. | Allows manual addition of missed or unlisted line items. | `[ ]` |
| 5.4 | **IME Keyboard & Scrolling Insets** | Type in tax/fee inputs or scroll through line items with software keyboard open. | Screen scrolls smoothly with `.imePadding()`; inputs remain fully visible above keyboard without dismissing keyboard. | `[ ]` |
| 5.5 | **Item-by-Item Cost Allocation** | Assign specific extracted receipt items to individual group members. | Calculates subtotals + proportional tax/tip split accurately per member and converts to group expense. | `[ ]` |

---

## 6. Subscriptions Hub & Recurring Bill Automation (`SubscriptionsScreen.kt`, `RecurringExpenseWorker.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 6.1 | **Subscriptions Hub Navigation** | Navigate to Subscriptions screen from Dashboard or Settings. | Displays list of active recurring subscriptions (Netflix, Spotify, Rent, Wi-Fi) with monthly commitment total. | `[ ]` |
| 6.2 | **Add / Edit Recurring Subscription** | Add new subscription with name, monthly amount, category, and billing day. | Saves subscription and updates recurring commitments list. | `[ ]` |
| 6.3 | **One-Tap Expense Logging** | Tap "Pay" or "Log Expense" on an upcoming subscription card. | Instantly logs an expense transaction matching the subscription details and updates due date. | `[ ]` |
| 6.4 | **Background Automation Worker** | Test `RecurringExpenseWorker` execution via WorkManager diagnostic tools. | Automatically checks and logs due recurring expenses in background without crashing. | `[ ]` |

---

## 7. Data Backup, Import/Export & Cloud Sync (`SettingsScreen.kt`, `CsvExportImporter.kt`, `CloudSyncManager.kt`)

| # | Test Case / Scenario | Action Steps | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 7.1 | **CSV Export & Import** | Go to Settings, tap "Export CSV" and then "Import CSV". | Generates RFC 4180 compliant CSV file for expenses and successfully imports records back into database. | `[ ]` |
| 7.2 | **JSON Database Backup & Restore** | Tap "Backup to JSON" and "Restore from JSON". | Exports complete database state to file storage and restores successfully without data corruption. | `[ ]` |
| 7.3 | **Live Currency Exchange Rates Sync** | Tap refresh rate button in Settings or view multi-currency conversion. | Queries Frankfurter API (`api.frankfurter.app`) for latest EUR/USD/GBP/INR rates and updates active rate cache. | `[ ]` |
| 7.4 | **Cloud Firestore Multi-User Sync** | Log in with Firebase account, perform expense edits, verify Firestore sync. | Synchronizes local Room database changes with Firestore cloud backend (`CloudSyncManager`). | `[ ]` |

---

## 8. Non-Functional & System Testing

| # | Test Category | Test Action / Scenario | Expected Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 8.1 | **Edge-to-Edge & System Bars** | Inspect app padding on devices with gesture navigation and status bars. | Content is not obscured by system bars or cutouts; system bars have correct translucent/adaptive styling. | `[ ]` |
| 8.2 | **Screen Rotation & Config Changes** | Rotate device between Portrait and Landscape across all screens. | ViewModels retain state; UI adapts responsively without crashing or losing form inputs. | `[ ]` |
| 8.3 | **Process Death & Persistence** | Put app in background, kill process via ADB (`adb shell am kill`), reopen app. | Room database persistence and ViewModel state restoration ensure user work in progress is preserved or safely reloaded. | `[ ]` |
| 8.4 | **Accessibility (TalkBack)** | Navigate app using Android TalkBack screen reader. | All buttons, icons, and input fields have clear content descriptions and semantic labels. | `[ ]` |
