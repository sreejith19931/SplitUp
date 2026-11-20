# SplitUp App: Comprehensive Testing Checklist

This document provides a comprehensive, end-to-end QA testing checklist covering every screen, navigation flow, function, button, and edge case in the **SplitUp** Android application.

---

## 1. Authentication & Onboarding
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **AuthScreen** | Sign In Tab | Switches to email/password sign-in view. | Form fields validate email format & password length. |
| **AuthScreen** | Sign In Button | Authenticates user against backend/local DB. | Successful login navigates to Dashboard; error toast on invalid credentials. |
| **AuthScreen** | Register Tab | Switches to account creation view. | Requires valid email, strong password, and matching confirmation. |
| **AuthScreen** | Biometric Unlock | Prompts fingerprint/face unlock if enabled. | Unlocks app securely; falls back to passcode on failure. |
| **AuthScreen** | Guest / Skip Mode | Allows bypassing authentication. | Enters app in local-only offline guest mode with warning banner. |
| **OnboardingSetupScreen** | Currency Selector | Selects default base currency (USD, EUR, INR, GBP, etc.). | Updates app-wide currency formatting immediately. |
| **OnboardingSetupScreen** | Profile & UPI Inputs | Accepts user display name and UPI / payment handle. | Persists profile data in encrypted preferences. |
| **OnboardingSetupScreen** | "Get Started" Button | Completes onboarding flow. | Saves setup state and navigates to [DashboardScreen](class://com.finance.splitup.ui.screens.DashboardScreen). |

---

## 2. Dashboard & Navigation
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **DashboardScreen** | Net Balance Card | Displays total income minus expenses or net group balance. | Correctly formats currency and color-codes positive/negative balances. |
| **DashboardScreen** | Quick Action: Add Expense | Tapping navigates to [AddExpenseScreen](class://com.finance.splitup.ui.screens.AddExpenseScreen). | Smooth transition with pre-filled defaults. |
| **DashboardScreen** | Quick Action: New Group | Tapping navigates to [CreateGroupScreen](class://com.finance.splitup.ui.screens.CreateGroupScreen). | Opens group creation wizard. |
| **DashboardScreen** | Quick Action: Scan Receipt | Tapping navigates to [ReceiptScannerScreen](class://com.finance.splitup.ui.screens.ReceiptScannerScreen). | Launches camera/gallery picker. |
| **DashboardScreen** | Quick Action: Insights | Tapping navigates to [InsightsScreen](class://com.finance.splitup.ui.screens.InsightsScreen). | Opens spending analytics view. |
| **DashboardScreen** | Recent Transactions List | Displays list of recent personal and group expenses. | Tapping an item opens its details/edit view; infinite scroll / pagination works smoothly. |
| **DashboardScreen** | Top Bar: Search Icon | Tapping opens [GlobalSearchScreen](class://com.finance.splitup.ui.screens.GlobalSearchScreen). | Instant focus on search input. |
| **DashboardScreen** | Top Bar: Notification Icon | Tapping opens [NotificationsScreen](class://com.finance.splitup.ui.screens.NotificationsScreen). | Shows unread badge count correctly. |
| **DashboardScreen** | Top Bar: Profile / Settings | Tapping opens [SettingsScreen](class://com.finance.splitup.ui.screens.SettingsScreen). | Navigates to settings hub. |
| **DashboardScreen** | Pull-to-Refresh | Swiping down triggers data refresh and cloud sync. | Shows loading indicator and updates local data. |

---

## 3. Personal Expenses & Management
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **PersonalExpensesScreen** | Search Bar | Filters transactions by title, merchant, or category in real-time. | Instant UI filtering without noticeable lag. |
| **PersonalExpensesScreen** | Filter Chips | Filters list by All, Income, Expense, or specific Category. | Only matching items are displayed. |
| **PersonalExpensesScreen** | Sort Toggle | Sorts by Date (Newest/Oldest) or Amount (High/Low). | Reorders list correctly. |
| **PersonalExpensesScreen** | Floating Action Button (`+`) | Opens [AddExpenseScreen](class://com.finance.splitup.ui.screens.AddExpenseScreen). | Opens fresh expense entry form. |
| **PersonalExpensesScreen** | Swipe / Tap Item | Tapping opens edit; swiping enables delete/archive. | Confirmation dialog appears before deletion. |
| **AddExpenseScreen** | Amount Input | Numeric keypad, decimal support, currency symbol. | Prevents invalid non-numeric inputs. |
| **AddExpenseScreen** | Category Selector | Opens category picker with auto-detection preview ([CategoryDetector](class://com.finance.splitup.data.CategoryDetector)). | Correctly auto-assigns category based on expense title. |
| **AddExpenseScreen** | Date & Income/Expense Toggle | Switches between expense and income, sets custom date. | Updates database transaction type and totals. |
| **AddExpenseScreen** | Save Button | Validates form and writes to [AppDatabase](class://com.finance.splitup.data.db.AppDatabase). | Success toast, closes screen, updates dashboard. |

---

## 4. Groups & Group Expense Sharing
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **GroupsScreen** | Group Cards List | Displays all expense-sharing groups with member count and net balance. | Tapping any card opens [GroupDetailScreen](class://com.finance.splitup.ui.screens.GroupDetailScreen). |
| **CreateGroupScreen** | Group Name & Currency | Inputs group name and base currency. | Validation prevents empty group names. |
| **CreateGroupScreen** | Member Management | Adds members via email, UPI, or contacts list. | Members successfully added to group list. |
| **GroupDetailScreen** | Expenses Tab | Lists all expenses incurred within the group. | Shows paid-by and split-between details for each item. |
| **GroupDetailScreen** | Balances Tab | Displays individual member balances (who owes whom). | Correct financial aggregation. |
| **GroupDetailScreen** | Settlements Tab | Calculates simplified debts using [CalculateGroupSettlementsUseCase](class://com.finance.splitup.domain.CalculateGroupSettlementsUseCase). | Shows "Settle Up" action buttons to minimize transactions. |
| **GroupDetailScreen** | Export PDF Button | Generates and shares PDF group statement via [PdfGenerator](class://com.finance.splitup.data.PdfGenerator). | Successfully exports formatted PDF file. |
| **AddGroupExpenseScreen** | Split Methods | Supports Equal, Percentage (adds to 100%), Exact amounts, and Shares. | Mathematical validation prevents saving if splits don't match total. |

---

## 5. Analytics, Insights & Budgets
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **InsightsScreen** | [SpendingChartComponent](class://com.finance.splitup.ui.screens.SpendingChartComponent) | Renders interactive pie/bar chart of category spending. | Clear visual representation with accurate percentages. |
| **InsightsScreen** | Time Range Selector | Switches between Weekly, Monthly, and Yearly analytics. | Dynamically reloads chart data. |
| **BudgetScreen** | Budget List & Progress Bars | Displays category budgets with spent vs limit progress indicators. | Turns warning orange/red when approaching or exceeding limits. |
| **BudgetScreen** | Set / Edit Budget Button | Allows setting monthly spending limits per category. | Persists budget limits and triggers alerts when exceeded. |

---

## 6. Subscriptions & Recurring Expenses
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **SubscriptionsScreen** | Active Subscriptions List | Lists recurring payments (Netflix, Rent, Utilities) with next billing date. | Calculates total monthly recurring expenditure. |
| **SubscriptionsScreen** | Add Subscription Button | Form to add new recurring expense rules. | Schedules background checks via [RecurringExpenseWorker](class://com.finance.splitup.data.RecurringExpenseWorker). |
| **SubscriptionsScreen** | Toggle Active/Inactive | Enables or disables recurring payment tracking. | Pauses or resumes background reminder generation. |

---

## 7. Receipt Scanner & OCR Pipeline
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **ReceiptScannerScreen** | Camera Viewfinder & Gallery | Captures paper receipt photo or selects image file. | Smooth camera integration and image cropping. |
| **ReceiptExtractionScreen** | OCR Processing (`ReceiptProcessor`) | Extracts merchant name, date, tax, tip, and total amount. | Shimmer loading state during OCR processing; high accuracy on clear receipts. |
| **ReceiptAllocationScreen** | Line Item Splitter | Assigns extracted line items to specific group members. | Pro-rata tax/tip distribution calculation is accurate. |

---

## 8. Search & Social
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **GlobalSearchScreen** | Global Search Input | Searches across personal expenses, group expenses, groups, and contacts simultaneously. | Real-time results grouped by category; tapping navigates directly to target item. |
| **FindFriendsScreen** | User Lookup & QR Scanner | Searches friends by username/email or scans QR/UPI code. | Successfully adds friend to contact/group directory. |

---

## 9. Notifications & Reminders
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **NotificationsScreen** | Notification Feed | Displays unread and read alerts (payment reminders, group invites, budget warnings). | Tapping notification opens relevant screen; swipe to dismiss works. |
| **NotificationsScreen** | "Mark All as Read" Button | Clears unread notification badges. | Updates UI state instantly. |

---

## 10. Settings, Security & Profile
| Screen / Component | Element / Function | Expected Behavior | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- |
| **SettingsScreen** | Currency & Theme Toggles | Switches base currency and app theme (Light/Dark/System). | Applies changes globally across the app. |
| **SecuritySettingsScreen** | Biometric / Passcode Lock | Enables fingerprint/face lock or PIN code requirement on app launch. | Forces authentication prompt when relaunching app. |
| **EditProfileScreen** | Profile & UPI Editing | Updates user name, email, phone, and payment UPI handle. | Saves changes securely to encrypted preferences. |
| **DataManagementScreen** | CSV Export / Import | Exports all transactions to CSV or imports from backup file via [CsvExportImporter](class://com.finance.splitup.data.CsvExportImporter). | Successfully generates and parses CSV files. |
| **DataManagementScreen** | Cloud Sync Control | Manual trigger and status monitor for [CloudSyncManager](class://com.finance.splitup.data.CloudSyncManager). | Syncs local SQLite database with cloud backend. |
| **AboutScreen** | Version & Update Checker | Displays app version and checks for updates via [UpdateChecker](class://com.finance.splitup.data.UpdateChecker). | Reports latest version status correctly. |

---

## 11. Cross-Cutting System & Edge Case Testing
1. **Edge-to-Edge & System Insets**: Verify that navigation bars and status bars do not obscure buttons (especially FABs and bottom sheets) using window insets.
2. **Offline Resilience**: Perform CRUD operations while network is disabled; verify local SQLite database integrity and subsequent background sync when connectivity returns.
3. **Memory & Performance**: Stress test scrolling through 1,000+ transactions to ensure zero UI jank or memory leaks.
4. **Localization & Currency**: Verify correct formatting across multiple regional currency symbols and number separators.
