# Data Loss Analysis Report: SplitUp App

This report evaluates potential vectors for **local** and **cloud** data loss across the SplitUp application based on a thorough code inspection of Room database entities, repositories, cloud sync mechanisms (`CloudSyncManager`), authentication (`AuthViewModel`), and account management flows.

---

## Executive Summary

SplitUp implements an **offline-first** architecture with cloud synchronization via Firebase Firestore and local persistence using Jetpack Room (`AppDatabase`). While the architecture includes robust components such as delta sync (`isDirty` flags, `updatedAt` timestamps) and tombstoning for deleted groups, several edge cases and destructive flows pose potential risks of data loss.

---

## 1. Local Data Loss Vectors

### A. User-Initiated Data Wiping & Sign Out
* **Mechanism:** In [SettingsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/SettingsScreen.kt) and [AuthViewModel.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/viewmodel/AuthViewModel.kt), user actions like "Clear All App Data", account deletion, and sign out invoke `repository.clearAllLocalData()`.
* **Risk:** Although `AuthViewModel.signOut()` attempts a final sync via `CloudSyncManager.performCloudSync()`, if the network is offline or times out during sign out, unsynced local changes (`isDirty = true`) are wiped permanently when `clearAllLocalData()` executes.

### B. Multi-Account Switch / UID Mismatch Purge
* **Mechanism:** In [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt#L450-L460):
  ```kotlin
  val lastUid = prefs.getString("last_sync_uid", null)
  if (lastUid != null && lastUid != uid) {
      repository.clearAllLocalData()
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
  }
  ```
* **Risk:** If User A logs out and User B logs in on the same device, the app immediately wipes all local database tables. If User A had offline-created expenses or groups that were never pushed to cloud storage, those changes are permanently lost.

### C. App Uninstall / Storage Clearance
* **Mechanism:** Standard Android OS behavior for app uninstalls or "Clear Storage" in system settings.
* **Risk:** Any data created while offline that has not successfully synced to Firestore will be permanently lost upon uninstall or data clearance.

---

## 2. Cloud Data Loss Vectors

### A. Permanent Account Deletion
* **Mechanism:** In [AuthViewModel.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/viewmodel/AuthViewModel.kt#L271-L300) and [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt#L647-L670), calling `deleteAccount()` invokes `deleteUserCloudData(uid)`, which iterates through all user subcollections (`expenses`, `incomes`, `groups`, `budgets`, `recurring_expenses`) and deletes every document, followed by the user document itself.
* **Risk:** This action is **100% irreversible**. While expected for account deletion, there is no soft delete or grace period (trash bin).

### B. Shared Group Tombstoning & Deletion Propagation
* **Mechanism:** In [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt#L590-L630), when a user deletes a group, a tombstone is written to `deleted_groups`, and the global group document is deleted. When other members sync, they detect the tombstone and execute `repository.deleteGroup(groupId)`.
* **Risk:** If a shared group is mistakenly deleted by one member (or due to sync race conditions), all member devices propagate the deletion and remove the group and associated group data locally.

### C. Strict Sync Timeouts & Network Failures
* **Mechanism:** [CloudSyncManager.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/data/CloudSyncManager.kt) enforces a strict 15-second timeout (`SYNC_TIMEOUT_MS = 15_000L`) on cloud operations.
* **Risk:** On poor network conditions, large batch pushes or full fetches can time out (`TimeoutCancellationException`), leading to `SyncStatus.ERROR`. If users assume their data is synced when an error occurred, subsequent local wipes or uninstalls could result in data loss.

### D. Last-Write-Wins Conflict Overwrites
* **Mechanism:** Delta sync relies on `updatedAt` timestamps and batch `.set()` operations.
* **Risk:** If two users edit the same shared record offline or concurrent writes occur without CRDT/operational transformation merging, the newer timestamp silently overwrites the older one, potentially discarding concurrent user edits.

---

## Recommendations to Mitigate Data Loss

1. **Pre-Logout / Pre-Switch Sync Guarantee:**
   - Block sign-out, account switching, and clear data actions if there are pending dirty records (`isDirty = true`) and the device is offline, prompting the user to connect to the internet first or export a backup.
2. **Soft Deletes / Trash Bin for Groups and Expenses:**
   - Introduce a soft-delete mechanism or confirmation grace period before permanently purging records or shared groups from Firestore and Room.
3. **Automatic JSON Backup & Restore Option:**
   - Provide automated local periodic backups (e.g., JSON file export to app-specific external storage or SAF) so users can recover data if sync fails or account actions go awry.
4. **Robust Conflict Resolution:**
   - Implement field-level merging or conflict prompts for shared groups rather than silent last-write-wins overwrites.
