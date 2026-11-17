# Implementation Plan - SplitUp v1.4: "Intelligence & Collaboration"

This implementation plan outlines the development steps for **SplitUp v1.4**, focusing on Smart Budgets & Cash Flow Forecasting, Group Activity Feeds & Reminders, Itemized Receipt Tax/Tip Splitting, and Automated Backups & Travel Mode.

## User Review Required

> [!IMPORTANT]
> - New Room database tables/entities will be added for Category Budgets and Group Activity Audit Logs, requiring Room migration handling.
> - Background automated backups will utilize `WorkManager` with configurable weekly/monthly frequency.

## Proposed Changes

### Database & Models
#### [MODIFY] [Entities.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/db/Entities.kt)
- Add `BudgetEntity` for category-specific monthly spending limits.
- Add `GroupActivityEntity` for group audit trails and activity logs.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/db/AppDatabase.kt)
- Register new DAOs and entities (`BudgetDao`, `GroupActivityDao`).
- Increment database version and define migration strategy.

### Smart Budgets & Cash Flow
#### [NEW] [BudgetScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/BudgetScreen.kt)
- UI for setting monthly category budgets, viewing progress bars, and warning alerts when limits are approached.
#### [MODIFY] [FinanceViewModel.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/viewmodel/FinanceViewModel.kt)
- Add budget state flow, CRUD operations, and cash flow forecasting logic.

### Group Activity Feed & Reminders
#### [MODIFY] [GroupDetailScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/GroupDetailScreen.kt)
- Add an "Activity Log" tab showing who added/edited expenses or settled balances.
- Add settlement reminder notification trigger button.

### Automated Backups & Travel Mode
#### [MODIFY] [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt)
- Implement scheduled background backup worker using `WorkManager` for automated JSON exports.
#### [MODIFY] [AddExpenseScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/AddExpenseScreen.kt)
- Add Travel Currency mode toggle for automatic foreign currency rate detection.

## Verification Plan

### Automated Tests
- Unit tests for budget calculation and cash flow forecast logic.
- WorkManager background backup worker unit tests.

### Manual Verification
- Test setting category budgets and verifying warning progress bars on Dashboard/Insights.
- Verify group activity logs update in real-time when expenses are added or edited.
- Test automated backup worker scheduling via WorkManager inspector.
