package com.finance.splitup.data

import android.content.Context
import android.util.Log
import com.finance.splitup.data.db.IncomeEntity
import com.finance.splitup.data.repository.FinanceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncStatus {
    IDLE, SYNCING, SYNCED, ERROR
}

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val SYNC_TIMEOUT_MS = 15_000L
    private const val PREFS_NAME = "splitup_sync_prefs"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"

    init {
        try {
            val db = FirebaseFirestore.getInstance()
            db.firestoreSettings = com.google.firebase.firestore.firestoreSettings {
                setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore cache settings", e)
        }
    }

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow("Just Now")
    val lastSyncTimestamp: StateFlow<String> = _lastSyncTimestamp.asStateFlow()

    private val firestore: FirebaseFirestore?
        get() = try { FirebaseFirestore.getInstance() } catch (e: Exception) { Log.e(TAG, "Failed to get Firestore instance", e); null }

    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) { Log.e(TAG, "Failed to get FirebaseAuth instance", e); null }

    private suspend fun getDeletedGroupIds(db: FirebaseFirestore): Set<String> {
        return try {
            val snapshot = db.collection("deleted_groups").get().await()
            snapshot.documents.map { it.id }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun uploadReceiptImageToStorage(context: Context, localPath: String, storagePath: String): String? {
        return try {
            val fileUri = if (localPath.startsWith("content://") || localPath.startsWith("file://")) {
                android.net.Uri.parse(localPath)
            } else {
                android.net.Uri.fromFile(java.io.File(localPath))
            }
            val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)
            storageRef.putFile(fileUri).await()
            val downloadUrl = storageRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed uploadReceiptImageToStorage: $storagePath", e)
            null
        }
    }

    suspend fun fetchGroupExpensesFromSubcollection(db: FirebaseFirestore, grp: Group): Group {
        return try {
            val subcollDocs = db.collection("groups").document(grp.id).collection("expenses").get().await()
            if (!subcollDocs.isEmpty) {
                val groupExpenses = subcollDocs.documents.mapNotNull { doc ->
                    doc.toObject(GroupExpense::class.java)
                }
                grp.copy(expenses = groupExpenses)
            } else {
                grp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch group expenses subcollection for ${grp.id}", e)
            grp
        }
    }

    suspend fun comparePhoneAndCloudGroups(repository: FinanceRepository) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            val uid = user.uid
            val email = user.email?.lowercase()
            val localGroups = repository.getAllGroups().firstOrNull() ?: emptyList()
            val localGroupMap = localGroups.associateBy { it.id }

            val userCloudSnapshot = db.collection("users").document(uid).collection("groups").get().await()
            val userCloudIds = userCloudSnapshot.documents.map { it.id }.toSet()

            val sharedSnapshot = db.collection("groups").whereArrayContains("memberUids", uid).get().await()
            val sharedCloudIds = sharedSnapshot.documents.map { it.id }.toSet()

            val emailCloudIds = if (!email.isNullOrBlank()) {
                try {
                    db.collection("groups").whereArrayContains("memberEmails", email).get().await().documents.map { it.id }.toSet()
                } catch (_: Exception) {
                    emptySet()
                }
            } else {
                emptySet()
            }

            val allCloudIds = (userCloudIds + sharedCloudIds + emailCloudIds).toSet()
            val deletedGroupIds = getDeletedGroupIds(db)

            Log.d(TAG, "=== CLOUD VS PHONE GROUPS COMPARISON ===")
            Log.d(TAG, "Local phone group IDs: ${localGroupMap.keys}")
            Log.d(TAG, "User cloud group IDs: $userCloudIds")
            Log.d(TAG, "Shared cloud group IDs: $sharedCloudIds")
            Log.d(TAG, "Email-matched cloud group IDs: $emailCloudIds")
            Log.d(TAG, "Deleted tombstones: $deletedGroupIds")

            for ((id, group) in localGroupMap) {
                val isInCloud = allCloudIds.contains(id)
                val isTombstoned = deletedGroupIds.contains(id)
                if (isTombstoned || (!isInCloud && !group.name.isBlank())) {
                    Log.w(TAG, "Mismatch / Tombstoned group found on phone: ID=$id, Name=${group.name}")
                    repository.deleteGroup(id)
                } else {
                    Log.d(TAG, "Group verified matching: ID=$id, Name=${group.name}")
                }
            }
            Log.d(TAG, "=== END COMPARISON ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compare phone and cloud groups", e)
        }
    }

    suspend fun pushAllLocalDataToCloud(repository: FinanceRepository) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            val uid = user.uid
            val batch = db.batch()
            val deletedGroupIds = getDeletedGroupIds(db)

            // Push dirty expenses
            val dirtyExpenses = repository.getDirtyExpenses()
            val expenseIds = dirtyExpenses.map { it.id }
            for (exp in dirtyExpenses) {
                val ref = db.collection("users").document(uid).collection("expenses").document(exp.id)
                batch.set(ref, exp)
            }

            // Push dirty incomes
            val dirtyIncomes = repository.getDirtyIncomes()
            val incomeIds = dirtyIncomes.map { it.id }
            for (inc in dirtyIncomes) {
                val ref = db.collection("users").document(uid).collection("incomes").document(inc.id)
                batch.set(ref, inc)
            }

            // Push dirty groups (filtering out any groups that were deleted)
            val dirtyGroups = repository.getDirtyGroups().filter { !deletedGroupIds.contains(it.id) }
            repository.getDirtyGroups().filter { deletedGroupIds.contains(it.id) }.forEach {
                repository.deleteGroup(it.id)
            }
            val groupIds = dirtyGroups.map { it.id }
            for (grp in dirtyGroups) {
                val userName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "You"
                val uids = (if (grp.memberUids.contains(user.uid)) grp.memberUids else grp.memberUids + user.uid).toMutableSet()
                val emails = (if (!user.email.isNullOrBlank() && !grp.memberEmails.contains(user.email!!.lowercase())) {
                    grp.memberEmails + user.email!!.lowercase()
                } else grp.memberEmails).map { it.lowercase() }.distinct()

                if (emails.isNotEmpty()) {
                    try {
                        val usersSnapshot = db.collection("users").get().await()
                        for (doc in usersSnapshot.documents) {
                            val userEmail = doc.getString("email")?.lowercase()
                            if (userEmail != null && emails.contains(userEmail)) {
                                uids.add(doc.id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve member UIDs from Firestore in batch push", e)
                    }
                }

                val updatedMembers = grp.members.map { if (it == "You") userName else it }.distinct()
                val updatedGroup = grp.copy(
                    memberUids = uids.toList(),
                    memberEmails = emails,
                    members = updatedMembers,
                    updatedAt = System.currentTimeMillis()
                )

                val userGroupRef = db.collection("users").document(uid).collection("groups").document(updatedGroup.id)
                batch.set(userGroupRef, updatedGroup)

                val globalGroupRef = db.collection("groups").document(updatedGroup.id)
                batch.set(globalGroupRef, updatedGroup)
            }

            // Push dirty budgets
            val dirtyBudgets = repository.getDirtyBudgets()
            val budgetCategories = dirtyBudgets.map { it.category }
            for (budget in dirtyBudgets) {
                val ref = db.collection("users").document(uid).collection("budgets").document(budget.category)
                batch.set(ref, mapOf("category" to budget.category, "limit" to budget.limit, "updatedAt" to budget.updatedAt))
            }

            // Push dirty recurring expenses
            val dirtyRecurring = repository.getDirtyRecurringExpenses()
            val recurringIds = dirtyRecurring.map { it.id }
            for (rec in dirtyRecurring) {
                val ref = db.collection("users").document(uid).collection("recurring_expenses").document(rec.id)
                batch.set(ref, rec)
            }

            // Commit batch write
            batch.commit().await()

            // Mark local records clean
            if (expenseIds.isNotEmpty()) repository.markExpensesClean(expenseIds)
            if (incomeIds.isNotEmpty()) repository.markIncomesClean(incomeIds)
            if (groupIds.isNotEmpty()) repository.markGroupsClean(groupIds)
            if (budgetCategories.isNotEmpty()) repository.markBudgetsClean(budgetCategories)
            if (recurringIds.isNotEmpty()) repository.markRecurringExpensesClean(recurringIds)

        } catch (e: Exception) {
            Log.e(TAG, "Error pushing local data to cloud in batch", e)
            throw e
        }
    }

    suspend fun fetchAllUserDataFromCloud(repository: FinanceRepository, context: Context?) {
        val user = auth?.currentUser
        if (user == null) {
            _syncStatus.value = SyncStatus.IDLE
            return
        }
        val db = firestore
        if (db == null) {
            _syncStatus.value = SyncStatus.ERROR
            return
        }
        _syncStatus.value = SyncStatus.SYNCING
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                val uid = user.uid
                val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val localExpenseCount = repository.getExpenseCount()
                // If local DB was wiped (count == 0), ignore lastSyncedAt to ensure full recovery from cloud
                val lastSyncedAt = if (localExpenseCount == 0) 0L else (prefs?.getLong(KEY_LAST_SYNCED_AT, 0L) ?: 0L)

                val userRef = db.collection("users").document(uid)

                // Restore Expenses (Delta sync if lastSyncedAt > 0, otherwise full fetch)
                var expenseDocs = if (lastSyncedAt > 0) {
                    val query = userRef.collection("expenses").whereGreaterThan("updatedAt", lastSyncedAt).get().await()
                    if (query.isEmpty) userRef.collection("expenses").get().await() else query
                } else {
                    userRef.collection("expenses").get().await()
                }

                for (doc in expenseDocs.documents) {
                    try {
                        val exp = doc.toObject(com.finance.splitup.data.db.ExpenseEntity::class.java)
                        if (exp != null) {
                            repository.insertExpense(exp.toDomain())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore expense ${doc.id}", e)
                    }
                }

                // Restore Incomes
                var incomeDocs = if (lastSyncedAt > 0) {
                    val query = userRef.collection("incomes").whereGreaterThan("updatedAt", lastSyncedAt).get().await()
                    if (query.isEmpty) userRef.collection("incomes").get().await() else query
                } else {
                    userRef.collection("incomes").get().await()
                }

                for (doc in incomeDocs.documents) {
                    try {
                        val inc = doc.toObject(IncomeEntity::class.java)
                        if (inc != null) {
                            repository.insertIncome(inc.copy(isDirty = false))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore income ${doc.id}", e)
                    }
                }

                // Restore Recurring Expenses
                var recurringDocs = if (lastSyncedAt > 0) {
                    val query = userRef.collection("recurring_expenses").whereGreaterThan("updatedAt", lastSyncedAt).get().await()
                    if (query.isEmpty) userRef.collection("recurring_expenses").get().await() else query
                } else {
                    userRef.collection("recurring_expenses").get().await()
                }

                for (doc in recurringDocs.documents) {
                    try {
                        val rec = doc.toObject(com.finance.splitup.data.db.RecurringExpenseEntity::class.java)
                        if (rec != null) {
                            repository.upsertRecurringExpense(rec.copy(isDirty = false))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore recurring expense ${doc.id}", e)
                    }
                }

                // Restore Groups
                val deletedGroupIds = getDeletedGroupIds(db)

                var groupDocs = if (lastSyncedAt > 0) {
                    val query = userRef.collection("groups").whereGreaterThan("updatedAt", lastSyncedAt).get().await()
                    if (query.isEmpty) userRef.collection("groups").get().await() else query
                } else {
                    userRef.collection("groups").get().await()
                }

                for (doc in groupDocs.documents) {
                    try {
                        val groupId = doc.id
                        if (deletedGroupIds.contains(groupId)) {
                            doc.reference.delete().await()
                            repository.deleteGroup(groupId)
                            continue
                        }
                        val globalDoc = try {
                            db.collection("groups").document(groupId).get().await()
                        } catch (e: Exception) {
                            null
                        }
                        if (globalDoc == null || !globalDoc.exists()) {
                            try {
                                db.collection("deleted_groups").document(groupId)
                                    .set(mapOf("deletedAt" to System.currentTimeMillis()))
                                    .await()
                            } catch (_: Exception) {}
                            doc.reference.delete().await()
                            repository.deleteGroup(groupId)
                            continue
                        }
                        val rawGrp = doc.toObject(Group::class.java)
                        if (rawGrp != null) {
                            val grp = fetchGroupExpensesFromSubcollection(db, rawGrp)
                            repository.insertGroupClean(grp)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore group ${doc.id}", e)
                    }
                }

                try {
                    val sharedGroupDocs = db.collection("groups").whereArrayContains("memberUids", uid).get().await()
                    for (doc in sharedGroupDocs.documents) {
                        try {
                            if (deletedGroupIds.contains(doc.id)) {
                                repository.deleteGroup(doc.id)
                                continue
                            }
                            val rawGrp = doc.toObject(Group::class.java)
                            if (rawGrp != null) {
                                val grp = fetchGroupExpensesFromSubcollection(db, rawGrp)
                                repository.insertGroupClean(grp)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to restore shared group ${doc.id}", e)
                        }
                    }

                    val email = user.email?.lowercase()
                    if (!email.isNullOrBlank()) {
                        val emailGroupDocs = db.collection("groups").whereArrayContains("memberEmails", email).get().await()
                        for (doc in emailGroupDocs.documents) {
                            try {
                                if (deletedGroupIds.contains(doc.id)) {
                                    repository.deleteGroup(doc.id)
                                    continue
                                }
                                val rawGrp = doc.toObject(Group::class.java)
                                if (rawGrp != null) {
                                    val grpWithSubcoll = fetchGroupExpensesFromSubcollection(db, rawGrp)
                                    val userName = user.displayName?.ifBlank { null } ?: email.substringBefore("@")
                                    val updatedUids = if (grpWithSubcoll.memberUids.contains(uid)) grpWithSubcoll.memberUids else grpWithSubcoll.memberUids + uid
                                    
                                    var membersList = grpWithSubcoll.members
                                    if (membersList.contains("You") && membersList.contains(userName)) {
                                        membersList = membersList.filter { it != "You" }
                                    }
                                    val isPending = grpWithSubcoll.pendingMembers.any { 
                                        it.equals(userName, ignoreCase = true) || 
                                        it.equals(email, ignoreCase = true) || 
                                        (email.isNotBlank() && it.equals(email.substringBefore("@"), ignoreCase = true)) 
                                    }
                                    val updatedMembers = if (isPending) {
                                        membersList.filter { !it.equals(userName, ignoreCase = true) }
                                    } else membersList

                                    val updatedPending = grpWithSubcoll.pendingMembers

                                    if (updatedUids != grpWithSubcoll.memberUids || updatedMembers != grpWithSubcoll.members) {
                                        val updatedGroup = grpWithSubcoll.copy(memberUids = updatedUids, members = updatedMembers, pendingMembers = updatedPending)
                                        db.collection("groups").document(updatedGroup.id).set(updatedGroup).await()
                                        repository.insertGroupClean(updatedGroup)
                                    } else {
                                        repository.insertGroupClean(grpWithSubcoll)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to process email-matched group ${doc.id}", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query shared groups", e)
                }

                try {
                    val localGroups = repository.getAllGroups().firstOrNull() ?: emptyList()
                    for (localGroup in localGroups) {
                        if (deletedGroupIds.contains(localGroup.id)) {
                            repository.deleteGroup(localGroup.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to purge deleted local groups", e)
                }

                // Compare and reconcile phone vs cloud groups
                comparePhoneAndCloudGroups(repository)

                // Restore Budgets
                val budgetDocs = userRef.collection("budgets").get().await()
                for (doc in budgetDocs.documents) {
                    try {
                        val cat = doc.getString("category")
                        val limit = doc.getDouble("limit")
                        if (cat != null && limit != null) {
                            repository.upsertBudget(cat, limit)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore budget ${doc.id}", e)
                    }
                }

                // Update lastSyncedAt preference
                prefs?.edit()?.putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis())?.apply()

                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                _lastSyncTimestamp.value = dateFormat.format(Date())
                _syncStatus.value = SyncStatus.SYNCED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetchAllUserDataFromCloud", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    suspend fun performCloudSync(repository: FinanceRepository? = null, context: Context? = null) {
        val user = auth?.currentUser
        if (user == null) {
            _syncStatus.value = SyncStatus.IDLE
            return
        }
        val db = firestore
        if (db == null) {
            _syncStatus.value = SyncStatus.ERROR
            return
        }
        _syncStatus.value = SyncStatus.SYNCING
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                val uid = user.uid
                val userRef = db.collection("users").document(uid)
                val userMap = mutableMapOf<String, Any>("lastActive" to Date())
                user.email?.let { userMap["email"] = it }
                if (!user.displayName.isNullOrBlank()) {
                    userMap["name"] = user.displayName!!
                }
                userRef.set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
                SplitUpMessagingService.uploadTokenToFirestore()

                if (repository != null) {
                    if (context != null) {
                        val prefs = context.getSharedPreferences("splitup_prefs", Context.MODE_PRIVATE)
                        val lastUid = prefs.getString("last_sync_uid", null)
                        if (lastUid != null && lastUid != uid) {
                            repository.clearAllLocalData()
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                        }
                        prefs.edit().putString("last_sync_uid", uid).apply()
                    }

                    // Push local dirty changes first
                    pushAllLocalDataToCloud(repository)
                    // Then fetch cloud updates (delta sync with fallback)
                    fetchAllUserDataFromCloud(repository, context)
                } else {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    _lastSyncTimestamp.value = dateFormat.format(Date())
                    _syncStatus.value = SyncStatus.SYNCED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed performCloudSync", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    suspend fun syncExpenseToCloud(expense: Expense, context: Context? = null) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                var currentExpense = expense
                if (!currentExpense.receiptImagePath.isNullOrBlank() && currentExpense.receiptUrl.isNullOrBlank() && context != null) {
                    val uploadedUrl = uploadReceiptImageToStorage(context, currentExpense.receiptImagePath!!, "receipts/${user.uid}/${currentExpense.id}.jpg")
                    if (uploadedUrl != null) {
                        currentExpense = currentExpense.copy(receiptUrl = uploadedUrl)
                    }
                }
                db.collection("users")
                    .document(user.uid)
                    .collection("expenses")
                    .document(currentExpense.id)
                    .set(currentExpense)
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncExpenseToCloud ${expense.id}", e)
        }
    }

    suspend fun deleteExpenseFromCloud(expenseId: String) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("users")
                    .document(user.uid)
                    .collection("expenses")
                    .document(expenseId)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleteExpenseFromCloud $expenseId", e)
        }
    }

    suspend fun syncIncomeToCloud(income: IncomeEntity) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("users")
                    .document(user.uid)
                    .collection("incomes")
                    .document(income.id)
                    .set(income)
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncIncomeToCloud ${income.id}", e)
        }
    }

    suspend fun deleteIncomeFromCloud(incomeId: String) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("users")
                    .document(user.uid)
                    .collection("incomes")
                    .document(incomeId)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleteIncomeFromCloud $incomeId", e)
        }
    }

    suspend fun deleteGroupExpenseFromCloud(groupId: String, expenseId: String) {
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("groups")
                    .document(groupId)
                    .collection("expenses")
                    .document(expenseId)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleteGroupExpenseFromCloud $groupId/$expenseId", e)
        }
    }

    suspend fun syncGroupToCloud(group: Group, repository: FinanceRepository? = null, context: Context? = null) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            val deletedGroupIds = getDeletedGroupIds(db)
            if (deletedGroupIds.contains(group.id)) {
                repository?.deleteGroup(group.id)
                return
            }
            withTimeout(SYNC_TIMEOUT_MS) {
                val userName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "You"
                val uids = (if (group.memberUids.contains(user.uid)) group.memberUids else group.memberUids + user.uid).toMutableSet()
                val emails = (if (!user.email.isNullOrBlank() && !group.memberEmails.contains(user.email!!.lowercase())) {
                    group.memberEmails + user.email!!.lowercase()
                } else group.memberEmails).map { it.lowercase() }.distinct()

                val updatedMembers = group.members.map { if (it == "You") userName else it }.distinct()

                if (emails.isNotEmpty()) {
                    try {
                        val usersSnapshot = db.collection("users").get().await()
                        for (doc in usersSnapshot.documents) {
                            val userEmail = doc.getString("email")?.lowercase()
                            if (userEmail != null && emails.contains(userEmail)) {
                                uids.add(doc.id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve member UIDs from Firestore", e)
                    }
                }

                val processedExpenses = group.expenses.map { exp ->
                    var currentExp = exp
                    if (!currentExp.receiptImagePath.isNullOrBlank() && currentExp.receiptUrl.isNullOrBlank() && context != null) {
                        val uploadedUrl = uploadReceiptImageToStorage(context, currentExp.receiptImagePath!!, "group_receipts/${group.id}/${currentExp.id}.jpg")
                        if (uploadedUrl != null) {
                            currentExp = currentExp.copy(receiptUrl = uploadedUrl)
                        }
                    }
                    currentExp
                }

                val updatedGroup = group.copy(
                    memberUids = uids.toList(),
                    memberEmails = emails,
                    members = updatedMembers,
                    expenses = processedExpenses,
                    updatedAt = System.currentTimeMillis()
                )

                db.collection("users")
                    .document(user.uid)
                    .collection("groups")
                    .document(updatedGroup.id)
                    .set(updatedGroup)
                    .await()

                db.collection("groups")
                    .document(updatedGroup.id)
                    .set(updatedGroup)
                    .await()

                // Sync individual expenses to subcollection /groups/{groupId}/expenses/{expenseId}
                val subcollRef = db.collection("groups").document(updatedGroup.id).collection("expenses")
                val activeExpenseIds = processedExpenses.map { it.id }.toSet()
                try {
                    val existingDocs = subcollRef.get().await()
                    for (doc in existingDocs.documents) {
                        if (!activeExpenseIds.contains(doc.id)) {
                            doc.reference.delete().await()
                        }
                    }
                } catch (_: Exception) {}

                for (exp in processedExpenses) {
                    subcollRef.document(exp.id).set(exp).await()
                }

                repository?.insertGroupClean(updatedGroup)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncGroupToCloud ${group.id}", e)
        }
    }

    private const val KEY_DELETED_GROUP_IDS = "deleted_group_ids_set"

    fun isGroupLocallyTombstoned(context: Context, groupId: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_DELETED_GROUP_IDS, emptySet()) ?: emptySet()
            return set.contains(groupId)
        } catch (_: Exception) {
            return false
        }
    }

    fun markGroupLocallyTombstoned(context: Context, groupId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = (prefs.getStringSet(KEY_DELETED_GROUP_IDS, emptySet()) ?: emptySet()).toMutableSet()
            set.add(groupId)
            prefs.edit().putStringSet(KEY_DELETED_GROUP_IDS, set).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark group tombstoned", e)
        }
    }

    suspend fun deleteGroupFromCloud(groupId: String, repository: FinanceRepository? = null, context: Context? = null) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            if (context != null) {
                markGroupLocallyTombstoned(context, groupId)
            } else {
                try {
                    val app = com.google.firebase.FirebaseApp.getInstance().applicationContext
                    markGroupLocallyTombstoned(app, groupId)
                } catch (_: Exception) {}
            }
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("deleted_groups").document(groupId)
                    .set(mapOf("deletedAt" to System.currentTimeMillis(), "deletedBy" to user.uid))
                    .await()

                db.collection("groups").document(groupId).delete().await()

                // Clean up own private copy
                db.collection("users").document(user.uid).collection("groups").document(groupId).delete().await()

                repository?.deleteGroup(groupId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleteGroupFromCloud $groupId", e)
        }
    }

    suspend fun syncBudgetToCloud(category: String, limit: Double) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("users")
                    .document(user.uid)
                    .collection("budgets")
                    .document(category)
                    .set(mapOf("category" to category, "limit" to limit, "updatedAt" to System.currentTimeMillis()))
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncBudgetToCloud $category", e)
        }
    }

    suspend fun syncRecurringExpenseToCloud(recurring: com.finance.splitup.data.db.RecurringExpenseEntity) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("users")
                    .document(user.uid)
                    .collection("recurring_expenses")
                    .document(recurring.id)
                    .set(recurring)
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncRecurringExpenseToCloud ${recurring.id}", e)
        }
    }

    suspend fun deleteRecurringExpenseFromCloud(id: String) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS) {
                db.collection("users")
                    .document(user.uid)
                    .collection("recurring_expenses")
                    .document(id)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleteRecurringExpenseFromCloud $id", e)
        }
    }

    suspend fun deleteUserCloudData(uid: String) {
        val db = firestore ?: return
        try {
            withTimeout(SYNC_TIMEOUT_MS * 2) {
                val userRef = db.collection("users").document(uid)
                val collections = listOf("expenses", "incomes", "groups", "budgets", "recurring_expenses", "incoming_notifications")
                for (colName in collections) {
                    try {
                        val snapshot = userRef.collection(colName).get().await()
                        for (doc in snapshot.documents) {
                            doc.reference.delete().await()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed deleting subcollection $colName for $uid", e)
                    }
                }

                // Remove user from shared groups in root /groups collection
                try {
                    val sharedGroups = db.collection("groups").whereArrayContains("memberUids", uid).get().await()
                    for (groupDoc in sharedGroups.documents) {
                        val group = groupDoc.toObject(Group::class.java)
                        if (group != null) {
                            val updatedUids = group.memberUids.filter { it != uid }
                            if (updatedUids.isEmpty()) {
                                groupDoc.reference.delete().await()
                            } else {
                                groupDoc.reference.update(
                                    mapOf(
                                        "memberUids" to updatedUids,
                                        "updatedAt" to System.currentTimeMillis()
                                    )
                                ).await()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed removing user $uid from shared groups during deletion", e)
                }

                userRef.delete().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleteUserCloudData for $uid", e)
        }
    }

    fun listenForRealtimeGroupUpdates(groupId: String, onGroupUpdated: (Group) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("groups")
                .document(groupId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    try {
                        val group = snapshot.toObject(Group::class.java)
                        if (group != null) {
                            onGroupUpdated(group)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse realtime group update", e)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed listenForRealtimeGroupUpdates", e)
            null
        }
    }

    private var notificationListenerRegistration: ListenerRegistration? = null

    suspend fun sendGroupActivityNotification(
        group: Group,
        title: String,
        body: String
    ) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        val senderUid = user.uid

        val recipientUids = group.memberUids.filter { it != senderUid }.distinct()
        if (recipientUids.isEmpty()) return

        for (targetUid in recipientUids) {
            try {
                val notificationData = mapOf(
                    "title" to title,
                    "body" to body,
                    "senderUid" to senderUid,
                    "senderName" to (user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "A group member"),
                    "groupId" to group.id,
                    "groupName" to group.name,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("users")
                    .document(targetUid)
                    .collection("incoming_notifications")
                    .add(notificationData)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification doc to user $targetUid", e)
            }
        }
    }

    fun stopListeningForIncomingNotifications() {
        notificationListenerRegistration?.remove()
        notificationListenerRegistration = null
    }

    fun startListeningForIncomingNotifications(context: Context) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return

        notificationListenerRegistration?.remove()
        notificationListenerRegistration = db.collection("users")
            .document(user.uid)
            .collection("incoming_notifications")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                for (doc in snapshot.documents) {
                    val title = doc.getString("title") ?: "Group Update 💸"
                    val body = doc.getString("body") ?: "A new update was made in your group."
                    val senderUid = doc.getString("senderUid")

                    if (senderUid != user.uid) {
                        showSystemNotification(context, title, body)
                    }

                    doc.reference.delete()
                }
            }
    }

    private fun showSystemNotification(context: Context, title: String, body: String) {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (notificationManager != null) {
            val channel = android.app.NotificationChannel(
                SplitUpMessagingService.CHANNEL_ID,
                "Group & Cloud Notifications",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Instant notifications for group expenses and updates" }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(context, com.finance.splitup.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, SplitUpMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

        try {
            @android.annotation.SuppressLint("MissingPermission")
            androidx.core.app.NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show system notification", e)
        }
    }
}
