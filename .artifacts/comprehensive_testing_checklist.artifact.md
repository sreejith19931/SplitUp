# SplitUp: Full-Fledged QA & Testing Checklist

Use this comprehensive verification checklist to ensure every feature, flow, and edge case works perfectly across the SplitUp Android application.

---

## 1. Authentication & Onboarding
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **1.1** | **Email / Password Sign-In** | Successfully authenticates valid credentials and loads user state. | `[ ]` |
| **1.2** | **Guest / Local Demo Mode** | Bypasses Firebase auth and loads offline sample data correctly. | `[ ]` |
| **1.3** | **Biometric Lock** | Enforces biometric prompt on app resume when enabled in security settings. | `[ ]` |
| **1.4** | **Onboarding Wizard** | First-time launch presents welcome steps, default currency selection, and category budget setup. | `[ ]` |

---

## 2. Dashboard & Summary Analytics
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **2.1** | **Financial Summary Cards** | Accurately aggregates total personal spent, group spent, and net group balance in real time. | `[ ]` |
| **2.2** | **Privacy Toggle** | Tapping the eye icon masks/unmasks financial balance figures across summary cards. | `[ ]` |
| **2.3** | **Budget Exceeded Banner** | Clicking the `"⚠️ X budget exceeded"` banner on the Dashboard navigates directly to the **Monthly Category Budgets** screen (`BudgetScreen`). | `[ ]` |
| **2.4** | **Pull-to-Refresh** | Pulling down on Dashboard refreshes local cache and triggers background cloud sync. | `[ ]` |

---

## 3. Personal Expenses & Income
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **3.1** | **Add Personal Expense** | Logs expense with title, amount, category, currency, and date; updates charts and totals. | `[ ]` |
| **3.2** | **Log Income** | Adds income entry; correctly updates net cash flow calculations. | `[ ]` |
| **3.3** | **Category Filtering** | Tapping a category slice in the donut chart filters the personal transaction list instantly. | `[ ]` |
| **3.4** | **Receipt OCR Scanner** | Launches camera/gallery picker, extracts line items and amounts via ML Kit, and pre-fills expense form. | `[ ]` |

---

## 4. Shared Groups & Split Engine
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **4.1** | **Create Group** | Creates shared group with custom emoji, type, currency, and members. | `[ ]` |
| **4.2** | **Latest Activity Group Sorting** | Groups in the group list sort by default according to their **latest activity** (newest expenses or updates automatically float to the top). | `[ ]` |
| **4.3** | **Add Group Expense** | Splits expense equally or custom percentages among members; updates group balances and triggers activity sorting timestamp. | `[ ]` |
| **4.4** | **Record Settlement** | Records payment between members and generates approval request for confirmation. | `[ ]` |

---

## 5. Smart Budgets & Cash Flow Forecasting
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **5.1** | **Category Budget Limits** | Setting monthly spending limits per category updates progress bars and color-coded warning rings. | `[ ]` |
| **5.2** | **Pacing Alerts** | Displays mid-month spending velocity forecast warnings when projected to exceed limits. | `[ ]` |
| **5.3** | **Budget Editing** | Tapping `"Edit Limit"` on any category updates the limit and recalculates status instantly. | `[ ]` |

---

## 6. Notifications & Approvals Hub
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **6.1** | **Vertical Bottom-to-Top Transition** | Tapping the notification bell opens the full-screen **Notifications Hub** with a smooth **bottom-to-top vertical slide transition** (`slideInVertically`). | `[ ]` |
| **6.2** | **Edge-to-Edge Layout** | Notifications page covers 100% of the screen height with zero dead gaps above the bottom navigation bar. | `[ ]` |
| **6.3** | **Interactive Alert Navigation** | Tapping budget exceeded alerts or group balance alerts navigates directly to `BudgetScreen` or group detail pages without redundant text links. | `[ ]` |
| **6.4** | **Decline / Dismiss Requests** | Declining an expense edit request or clearing notifications instantly removes them from active queues. | `[ ]` |

---

## 7. Cloud Sync & Data Management
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| **7.1** | **Cloud Firestore Multi-User Sync** | Changes sync securely across authenticated clients in real time. | `[ ]` |
| **7.2** | **JSON Backup & Restore** | Exporting database to JSON file and restoring from backup successfully recovers all records. | `[ ]` |
| **7.3** | **Clear All App Data** | Wipes local tables and cache safely after explicit confirmation. | `[ ]` |

---

> [!TIP]
> Run `./gradlew testDebugUnitTest` to verify all 53 automated unit tests, and `./gradlew assembleDebug` to verify compilation before release testing.
