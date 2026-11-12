# Cloud Sync & Multi-User Collaboration Implementation Guide

To transform **SplitUp** into a fully cloud-backed app where data persists across logouts and reinstalls, and where friends can collaborate in shared groups in real time, we need to implement four core architectural enhancements.

---

## 1. Cloud Persistence & Restoration (Reinstall / Re-login)

To ensure users never lose data across app uninstalls, reinstalls, or account logouts:

### A. Lifecycle-Triggered Cloud Sync & Restoration
- **On Sign-In / App Startup:** When `AuthViewModel` transitions to `Authenticated`, trigger `CloudSyncManager.fetchAllUserDataFromCloud(repository)` immediately.
- **On Data Mutation (Insert/Update/Delete):** Wire up `FinanceRepository` (or ViewModel actions) to automatically invoke `CloudSyncManager.syncExpenseToCloud()`, `syncIncomeToCloud()`, etc., in the background whenever local Room data changes.

### B. Account-Scoped Local Database Clean-up
- When a user logs out (`AuthViewModel.signOut()`), explicitly clear the local Room database cache (or close and delete the Room database file) so that the next user logging in starts with a clean slate before pulling their own data from Firestore.

---

## 2. UID-Based Multi-User Shared Groups

Currently, groups store members as plain text strings (`"Alice"`, `"Bob"`). To let friends see each other's updates across different devices:

### A. Update `Group` and `GroupEntity` Models
Modify `Group` and `GroupEntity` to track user UIDs and emails:
```kotlin
@Serializable
data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val members: List<String>, // Display names
    val memberUids: List<String> = emptyList(), // Firebase UIDs
    val memberEmails: List<String> = emptyList(), // Emails for inviting
    val expenses: List<GroupExpense> = emptyList()
)
```

### B. Group Invitation & Joining Flow
1. **Invite by Email:** When creating or editing a group, allow entering a friend's registered email address.
2. **Cloud Firestore Sync:** Save groups to a root collection `/groups/{groupId}` in Firestore with `memberUids`.
3. **Joining:** When User B logs in, query Firestore for any groups where User B's UID is present in `memberUids`, and sync them into User B's local Room database.

---

## 3. Real-Time Collaboration via Firestore Snapshots

To ensure friends see updates instantly:
- In `CloudSyncManager`, maintain active real-time listeners (`addSnapshotListener`) for all groups the user belongs to:
```kotlin
fun listenForUserGroups(uid: String, onGroupsUpdated: (List<Group>) -> Unit) {
    FirebaseFirestore.getInstance().collection("groups")
        .whereArrayContains("memberUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val groups = snapshot.documents.mapNotNull { it.toObject(Group::class.java) }
            onGroupsUpdated(groups)
        }
}
```

---

## 4. Firestore Security Rules (`firestore.rules`)

To secure user financial data and shared groups against unauthorized access:

```javascript
rules_version = '2';
service cloud.firestore {
  database /databases/{database}/documents {

    // User personal data (only accessible by the owner)
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Shared groups (accessible only by authenticated members of the group)
    match /groups/{groupId} {
      allow read, create: if request.auth != null;
      allow update, delete: if request.auth != null &&
        (request.auth.uid in resource.data.memberUids || request.auth.uid in request.resource.data.memberUids);
    }
  }
}
```
