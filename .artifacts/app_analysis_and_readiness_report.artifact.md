# SplitUp App: Detailed Codebase & Architecture Analysis

An exhaustive page-to-page, function-to-function, and feature-to-feature analysis of the **SplitUp** Android application has been performed to evaluate its readiness for implementing user authentication (Google / Email sign-up) and multi-user cross-device expense collaboration.

---

## 1. Executive Summary

| Category | Readiness Status | Summary |
| :--- | :--- | :--- |
| **Local UI & Architecture** | 🟢 **Ready** | Clean Jetpack Compose UI, Navigation 3, Material 3 theming, Room Database, and offline-first repository. |
| **Expense & Group Engine** | 🟢 **Ready** | Full local support for personal expenses, categorized budgeting, group creation, equal/custom splits, and settlement calculations. |
| **Receipt OCR & PDF Export** | 🟢 **Ready** | ML Kit text recognition for receipt scanning, line-item extraction, PDF reports, and CSV/JSON backups. |
| **User Authentication** | 🔴 **Not Ready** | App launches directly into Dashboard. Settings profile ("John Doe") is static mock data. No Auth SDKs or sign-up screens exist. |
| **Multi-User Sync & Cloud Collaboration** | 🔴 **Not Ready** | `CloudSyncManager` is a mock stub (`delay(1000)`). Data is stored strictly locally in Room. Group members are simple string arrays (`["Alice", "Bob"]`), not cloud user accounts. |

---

## 2. Page-by-Page & Feature-by-Feature Audit

### A. Dashboard Screen ([DashboardScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/DashboardScreen.kt))
- **UI / Layout**: Total spent summary, budget progress indicators per category, recent transactions list, and active groups summary.
- **Buttons / Actions**:
  - **"Add Expense"**: Navigates to [AddExpenseScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/AddExpenseScreen.kt).
  - **"Scan Receipt"**: Navigates to camera OCR flow ([ReceiptScannerScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/ReceiptScannerScreen.kt)).
  - **Transaction Deletion / Edit**: Supported locally via Room database DAO.
- **Collaboration Status**: Displays local transactions and local groups only. Does not reflect remote changes from other users or devices.

### B. Personal Expenses Screen ([PersonalExpensesScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/PersonalExpensesScreen.kt))
- **UI / Layout**: Complete chronological list of personal transactions with category badges, amounts, currency conversion, and payment methods.
- **Buttons / Actions**: Filter categories, add expense, delete expense.
- **Collaboration Status**: Local-only storage (`expenses` table in Room).

### C. Groups & Group Detail Screens ([GroupsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/GroupsScreen.kt), [GroupDetailScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/GroupDetailScreen.kt))
- **UI / Layout**: Group cards showing member count and total spend. Detail view shows member balances, who owes whom (settling up), and list of group expenses.
- **Buttons / Actions**: Create group, add group expense, delete group.
- **Collaboration Status**: Members are stored as `List<String>` of names (e.g., `"Alice"`, `"Bob"`). There is no concept of user accounts, device UUIDs, cloud invitation links, or sync tokens.

### D. Receipt Scanner & OCR Pipeline ([ReceiptScannerScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/ReceiptScannerScreen.kt), [ReceiptProcessor.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/ReceiptProcessor.kt))
- **UI / Layout**: CameraX preview viewfinder, extraction screen, and line-item allocation screen.
- **Functionality**: Uses Google ML Kit Text Recognition to parse merchant name, date, total amount, and line items from physical receipts.
- **Collaboration Status**: Operates entirely locally on device.

### E. Settings & Profile Screen ([SettingsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/SettingsScreen.kt))
- **UI / Layout**:
  - **User Profile Card**: Hardcoded static mock profile (`John Doe`, `john.doe@splitup.finance`, `Pro Member`, avatar `JD`).
  - **Data Exports**: PDF report generation via [PdfGenerator.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/PdfGenerator.kt), CSV export, JSON backup & restore.
  - **App Preferences**: Theme mode toggle (Light/Dark/System), Biometric Lock toggle, Default Currency switcher with automatic currency conversion across all local expenses.
  - **Cloud Sync Section**: Displays mock sync status managed by [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt).
- **Collaboration Status**: Currently lacks real authentication state, sign-in/sign-out controls, and account linking.

---

## 3. Technical Architecture Audit

- **Persistence Layer**: Built with Jetpack Room ([AppDatabase.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/db/AppDatabase.kt), [Entities.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/db/Entities.kt)) and [OfflineFirstFinanceRepository](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/repository/FinanceRepository.kt). Fully offline-capable.
- **State Management**: AndroidViewModel ([FinanceViewModel.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/viewmodel/FinanceViewModel.kt)) exposing StateFlows backed by Room Flow queries.
- **Navigation**: Jetpack Navigation 3 ([AppScreens.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/navigation/AppScreens.kt)).

---

## 4. What is Missing for Google/Email Sign-Up & Multi-User Collaboration?

To enable multi-user cross-device synchronization and account-based expense management, the following components must be implemented:

### Phase 1: Authentication Infrastructure (Sign Up / Login)
1. **Auth SDK Integration**: Add Firebase Auth (or Supabase/Backend REST auth) dependencies in `libs.versions.toml` and `app/build.gradle.kts`.
2. **Google Sign-In & Email/Password Flow**:
   - Implement Credential Manager API or Google Sign-In SDK.
   - Create an onboarding / Auth Screen (`AuthScreen.kt`) with Google Sign-In button and Email/Password sign-up/login form.
3. **Session & Profile Management**:
   - Replace the static mock profile in [SettingsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/SettingsScreen.kt) with real authenticated user data (`displayName`, `email`, `photoUrl`, `uid`).
   - Gate app entry behind authentication state checking (`isAuthenticated` state flow).

### Phase 2: Cloud Backend & Real-Time Sync
1. **Remote Database / Cloud Service**:
   - Integrate a backend cloud database (e.g., Firebase Firestore / Realtime Database or a backend REST API).
2. **Replace Mock `CloudSyncManager`**:
   - Implement actual background synchronization in [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt) to push local Room transactions/groups to the cloud and listen for remote updates.
   - Implement conflict resolution strategies (e.g., timestamp-based last-write-wins or transactional server authority).

### Phase 3: Multi-User Group Collaboration & Identity
1. **User ID Mapping in Groups**:
   - Transition group members from plain string names (`["Alice", "Bob"]`) to unique user IDs (`Map<String, String>` mapping `userId` to `displayName`/`email`).
2. **Group Sharing & Invitations**:
   - Implement group invite mechanisms (Invite code, shareable link, or direct email search to add members to a shared group).
   - Ensure expenses added by User A on Device A instantly propagate to User B on Device B via the cloud backend.

---

## 5. Conclusion & Recommendations

The app is in an **excellent local state**: its Compose UI, Room persistence, receipt scanning, and navigation are fully polished and robust. However, **it is currently a local-only single-device application**.

To achieve multi-user cross-device collaboration with Google/Email sign-up, the foundation is solid, but you will need to:
1. Implement the **Authentication Flow** (Google Sign-In / Email Auth).
2. Build out **Cloud Sync** (replacing `CloudSyncManager` with a real backend like Firebase Firestore/Supabase).
3. Update **Group & Member Models** to use cloud user IDs instead of static strings.
