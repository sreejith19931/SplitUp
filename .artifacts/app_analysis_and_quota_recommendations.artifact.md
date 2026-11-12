# SplitUp App Analysis, Improvements, and Firebase Quota Optimization Report

This report provides a comprehensive analysis of the **SplitUp** Android application, covering architectural review, code quality improvements, bug fixes, and targeted strategies to drastically reduce Firebase and Google Cloud quota consumption (Firestore reads/writes/bandwidth).

---

## 1. Executive Summary & Architecture Overview

SplitUp is an offline-first finance and expense-sharing app built with:
- **UI:** Jetpack Compose (Material 3)
- **Local Storage:** Room Database (`AppDatabase`, `FinanceDao`)
- **Cloud & Auth:** Firebase Auth & Cloud Firestore (`CloudSyncManager`, `CloudSyncWorker`)
- **Utilities:** ML Kit (Text recognition & barcode scanning), Retrofit (Currency API), WorkManager, Biometrics, PDF Generator.

While the local-first architecture (Room as source of truth) is well-suited for offline performance, the current cloud synchronization implementation in `CloudSyncManager.kt` performs **full collection queries** (`.get().await()`) and **individual document writes** (`.set().await()` inside loops) on every sync cycle. This leads to high Firestore read/write counts, rapid quota depletion, and potential performance bottlenecks.

---

## 2. Firebase & Google Cloud Quota Optimization Strategies

To minimize Firestore reads, writes, and network bandwidth, implement the following quota-saving mechanisms:

### A. Implement Incremental / Delta Sync (Timestamp-Based Querying)
- **Current Issue:** `fetchAllUserDataFromCloud` fetches all documents across `expenses`, `incomes`, `recurring_expenses`, `groups`, and `budgets` every time the app syncs.
- **Optimization:**
  - Add a `lastSyncedAt` timestamp property in local preferences (`SharedPreferences` or DataStore).
  - Query Firestore documents where `updatedAt > lastSyncedAt` using Firestore queries (`whereGreaterThan("updatedAt", lastSyncTimestamp)`).
  - Only download newly created or updated documents, reducing read operations by up to 90%.

### B. Batch Firestore Writes
- **Current Issue:** `pushAllLocalDataToCloud` loops through all local records and executes individual `set().await()` calls for each item.
- **Optimization:**
  - Use Firestore **WriteBatches** (`db.batch()`) or Transactions to group up to 500 writes into a single network round-trip.
  - This reduces write request overhead, lowers billing costs, and significantly speeds up sync times.

### C. Dirty Flag / Change Tracking in Room
- **Current Issue:** Every local record is pushed to Firestore during `pushAllLocalDataToCloud`, regardless of whether it was modified.
- **Optimization:**
  - Add a `isDirty` (Boolean) or `syncStatus` column to Room entities (`ExpenseEntity`, `IncomeEntity`, etc.).
  - Only push records where `isDirty == true`. Once successfully acknowledged by Firestore, reset `isDirty = false`.

### D. Optimize Real-Time Snapshot Listeners
- **Current Issue:** `listenForRealtimeGroupUpdates` attaches snapshot listeners to groups. If multiple groups or screens are active, persistent listeners consume continuous reads and battery.
- **Optimization:**
  - Scope snapshot listeners strictly to the currently viewed screen (attach in `onStart`/`onResume` and remove in `onStop`/`onPause`).
  - Use targeted polling or pull-to-refresh for dashboard aggregates instead of open-ended real-time listeners where instant collaboration is not critical.

### E. Smart Caching & Metadata Queries
- **Current Issue:** Offline cache is enabled, but queries default to server or unoptimized reads.
- **Optimization:**
  - Configure Firestore cache settings to prioritize local cache (`Source.CACHE`) when offline or during quick initial screen loads, falling back to server updates only when necessary.

---

## 3. Code Quality, Fixes, & Architectural Improvements

### A. Robust Error Handling & Silent Catch Removal
- **Current Issue:** `CloudSyncManager` uses blank catch blocks (`catch (_: Exception) {}`), masking network failures, serialization exceptions, or permission errors.
- **Recommendation:** Replace empty catch blocks with structured logging (`Log.e(TAG, "...", e)`) and emit error states to `SyncStatus` so the UI can gracefully inform the user or trigger retry mechanisms.

### B. Dependency Injection (Hilt / Koin)
- **Current Issue:** Repositories, Database instances, and Managers (`CloudSyncManager`) are instantiated statically or manually.
- **Recommendation:** Integrate **Hilt** for dependency injection. This simplifies testing, decouples components, and manages lifecycle scopes properly.

### C. Modularization of Monolithic Screens
- **Current Issue:** Several files are extremely large (`GroupDetailScreen.kt` ~1441 lines, `FinanceViewModel.kt` ~1125 lines, `DashboardScreen.kt` ~909 lines).
- **Recommendation:** Break down large Composable screens into smaller, reusable UI components and delegate specific business logic into separate ViewModel features or use cases.

### D. Security & Firestore Security Rules Audit
- **Current Issue:** Cross-user data isolation relies heavily on client-side logic.
- **Recommendation:** Ensure robust Firestore Security Rules enforce that users can only read/write documents under their own `users/{uid}` path, and shared groups where `request.auth.uid in resource.data.memberUids` or `request.auth.token.email in resource.data.memberEmails`.

### E. Comprehensive Testing
- **Current Issue:** Test coverage is minimal (`ExampleInstrumentedTest.kt`, `SplitUpUiTest.kt`).
- **Recommendation:** Add unit tests for `FinanceViewModel`, repository mapping logic, and mock Firestore/Room interactions using `kotlinx-coroutines-test`.

---

## 4. Implementation Priority Roadmap

| Priority | Feature / Optimization | Impact | Effort |
| :--- | :--- | :--- | :--- |
| **High** | Delta/Incremental Sync (`updatedAt` filters) | Massive Firestore read reduction | Medium |
| **High** | Firestore Write Batches (`db.batch()`) | Massive Firestore write reduction | Low |
| **Medium** | Dirty Flag Tracking in Room | Eliminates redundant uploads | Medium |
| **Medium** | Remove/Lifecycle-scope Snapshot Listeners | Saves quota & battery | Low |
| **Low** | Hilt DI Integration & Screen Modularization | Code maintainability & testability | High |
