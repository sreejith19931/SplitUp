package com.finance.splitup.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Expense
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import com.finance.splitup.data.GroupExpense
import com.finance.splitup.data.Settlement
import com.finance.splitup.data.db.AppDatabase
import com.finance.splitup.data.repository.FinanceRepository
import com.finance.splitup.data.repository.OfflineFirstFinanceRepository
import com.finance.splitup.domain.CalculateGroupSettlementsUseCase
import com.finance.splitup.domain.DetectCategoryUseCase
import com.finance.splitup.domain.CalculateGroupMetricsUseCase
import com.finance.splitup.ui.state.DashboardUiState
import com.finance.splitup.ui.state.GroupDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class FinanceViewModel(application: Application? = null) : AndroidViewModel(application ?: DummyApplication()) {

    private class DummyApplication : Application()

    private val repository: FinanceRepository? = application?.let {
        OfflineFirstFinanceRepository(AppDatabase.getDatabase(it).financeDao())
    }

    val calculateGroupSettlementsUseCase = CalculateGroupSettlementsUseCase()
    val detectCategoryUseCase = DetectCategoryUseCase()
    val calculateGroupMetricsUseCase = CalculateGroupMetricsUseCase()

    private val prefs = application?.let { com.finance.splitup.data.EncryptedPrefs.getPreferences(it, "splitup_prefs") }

    private val _dismissedNotifications = MutableStateFlow<Set<String>>(
        prefs?.getStringSet("dismissed_notifications", emptySet()) ?: emptySet()
    )
    val dismissedNotifications: StateFlow<Set<String>> = _dismissedNotifications.asStateFlow()

    fun dismissNotifications(keys: Collection<String>) {
        _dismissedNotifications.update { current ->
            val updated = current + keys
            prefs?.edit()?.putStringSet("dismissed_notifications", updated)?.apply()
            updated
        }
    }

    fun clearDismissedNotifications() {
        _dismissedNotifications.value = emptySet()
        prefs?.edit()?.remove("dismissed_notifications")?.apply()
    }

    var currentExtractedReceipt: com.finance.splitup.data.ExtractedReceipt? = null
    var targetGroupIdForReceipt: String? = null
    var budgetWarningMessage by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(
        try {
            val saved = prefs?.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
            com.finance.splitup.ui.theme.ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            com.finance.splitup.ui.theme.ThemeMode.SYSTEM
        }
    )
    var isBiometricLockEnabled by mutableStateOf(prefs?.getBoolean("biometrics_enabled", false) ?: false)
    var defaultCurrency by mutableStateOf(prefs?.getString("default_currency", Currencies.EUR) ?: Currencies.EUR)

    private val _registeredUserEmails = MutableStateFlow<Set<String>>(emptySet())
    val registeredUserEmails: StateFlow<Set<String>> = _registeredUserEmails.asStateFlow()

    private val _registeredUsers = MutableStateFlow<List<com.finance.splitup.data.AppContact>>(emptyList())
    val registeredUsers: StateFlow<List<com.finance.splitup.data.AppContact>> = _registeredUsers.asStateFlow()

    private val _recentFriends = MutableStateFlow<List<com.finance.splitup.data.AppContact>>(emptyList())
    val recentFriends: StateFlow<List<com.finance.splitup.data.AppContact>> = _recentFriends.asStateFlow()

    init {
        fetchRegisteredUsers()
        loadRecentFriends()
        try {
            com.finance.splitup.data.CategoryDetector.init(getApplication())
        } catch (_: Exception) {}
    }

    private fun loadRecentFriends() {
        val saved = prefs?.getStringSet("recent_friends", emptySet()) ?: emptySet()
        val list = saved.mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 2) {
                com.finance.splitup.data.AppContact(name = parts[0], email = parts[1], isSplitUpMember = true)
            } else if (parts.size == 1) {
                com.finance.splitup.data.AppContact(name = parts[0], email = parts[0], isSplitUpMember = true)
            } else null
        }
        _recentFriends.value = list
    }

    fun addRecentFriend(name: String, email: String) {
        if (name.isBlank() || email.isBlank()) return
        viewModelScope.launch {
            val current = _recentFriends.value.toMutableList()
            current.removeAll { it.email.equals(email, ignoreCase = true) }
            current.add(0, com.finance.splitup.data.AppContact(name = name, email = email, isSplitUpMember = true))
            if (current.size > 10) current.removeAt(current.size - 1)
            _recentFriends.value = current
            val serialized = current.map { "${it.name}|${it.email}" }.toSet()
            prefs?.edit()?.putStringSet("recent_friends", serialized)?.apply()
        }
    }

    suspend fun searchCloudUserByEmail(emailQuery: String): com.finance.splitup.data.AppContact? {
        return try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val snapshot = db.collection("users").get().await()
            for (doc in snapshot.documents) {
                val email = doc.getString("email") ?: continue
                if (email.equals(emailQuery.trim(), ignoreCase = true)) {
                    val rawName = doc.getString("name") ?: doc.getString("displayName")
                    val name = if (!rawName.isNullOrBlank() && rawName != email) {
                        rawName
                    } else {
                        val prefix = email.substringBefore("@")
                        prefix.replace(".", " ").replace("_", " ").split(" ").joinToString(" ") {
                            if (it.isNotEmpty()) it.replaceFirstChar { c -> c.uppercase() } else ""
                        }
                    }
                    return com.finance.splitup.data.AppContact(name = name, email = email, isSplitUpMember = true)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun fetchRegisteredUsers() {
        viewModelScope.launch {
            try {
                val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { null } ?: return@launch
                val snapshot = db.collection("users").get().await()
                val users = snapshot.documents.mapNotNull { doc ->
                    val email = doc.getString("email") ?: return@mapNotNull null
                    val rawName = doc.getString("name") ?: doc.getString("displayName")
                    val name = if (!rawName.isNullOrBlank() && rawName != email) {
                        rawName
                    } else {
                        val prefix = email.substringBefore("@")
                        prefix.replace(".", " ").replace("_", " ").split(" ").joinToString(" ") {
                            if (it.isNotEmpty()) it.replaceFirstChar { c -> c.uppercase() } else ""
                        }
                    }
                    val upiVpa = doc.getString("upiVpa") ?: ""
                    val paypalHandle = doc.getString("paypalHandle") ?: ""
                    val venmoHandle = doc.getString("venmoHandle") ?: ""
                    val revolutHandle = doc.getString("revolutHandle") ?: ""
                    com.finance.splitup.data.AppContact(
                        name = name,
                        email = email,
                        isSplitUpMember = true,
                        upiVpa = upiVpa,
                        paypalHandle = paypalHandle,
                        venmoHandle = venmoHandle,
                        revolutHandle = revolutHandle
                    )
                }
                _registeredUsers.value = users
                _registeredUserEmails.value = users.map { it.email.lowercase() }.toSet()
            } catch (_: Exception) {}
        }
    }

    val isDarkTheme: Boolean
        get() = themeMode == com.finance.splitup.ui.theme.ThemeMode.DARK

    fun updateThemeMode(mode: com.finance.splitup.ui.theme.ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString("theme_mode", mode.name)?.apply()
    }

    fun toggleBiometricLock(enabled: Boolean) {
        isBiometricLockEnabled = enabled
        prefs?.edit()?.putBoolean("biometrics_enabled", enabled)?.apply()
    }

    fun updateDefaultCurrency(currency: String) {
        updateDefaultCurrencyAndConvert(currency)
    }

    fun updateDefaultCurrencyAndConvert(newCurrency: String) {
        val oldCurrency = defaultCurrency
        defaultCurrency = newCurrency
        prefs?.edit()?.putString("default_currency", newCurrency)?.apply()
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser != null) {
            viewModelScope.launch {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("users").document(currentUser.uid)
                        .set(mapOf("defaultCurrency" to newCurrency), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun hasUnsyncedChanges(): Boolean {
        return repository?.hasUnsyncedChanges() ?: false
    }

    fun clearLocalDataOnSignOut() {
        realtimeGroupListenerRegistration?.remove()
        realtimeGroupListenerRegistration = null
        realtimeEmailGroupListenerRegistration?.remove()
        realtimeEmailGroupListenerRegistration = null
        notifiedGroupIds.clear()
        notifiedExpenseIds.clear()
        _expensesFallback.value = emptyList()
        _groupsFallback.value = emptyList()
        _budgetsFallback.value = emptyMap()
        _incomesFallback.value = emptyList()
        _recurringExpensesFallback.value = emptyList()
        _userProfileFallback.value = com.finance.splitup.data.db.UserProfileEntity()
        budgetWarningMessage = null
        currentExtractedReceipt = null
        targetGroupIdForReceipt = null
        clearDismissedNotifications()
        try {
            val app = getApplication<Application>()
            app.getSharedPreferences("splitup_sync_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        if (repository != null) {
            viewModelScope.launch {
                try {
                    repository.clearAllLocalData()
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Failed to clear local data on sign out", e)
                }
            }
        }
    }

    fun clearAllData() {
        clearLocalDataOnSignOut()
        prefs?.edit()?.clear()?.apply()
    }

    private fun triggerImmediateSync() {
        try {
            val context = getApplication<Application>()
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.finance.splitup.data.CloudSyncWorker>()
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        } catch (_: Exception) {}
    }

    // Fallback in-memory states
    private val _expensesFallback = MutableStateFlow<List<Expense>>(emptyList())
    private val _budgetsFallback = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _groupsFallback = MutableStateFlow<List<Group>>(emptyList())
    private val _userProfileFallback = MutableStateFlow(com.finance.splitup.data.db.UserProfileEntity())

    val userProfile: StateFlow<com.finance.splitup.data.db.UserProfileEntity> = if (repository != null) {
        repository.getUserProfile()
            .map { it ?: com.finance.splitup.data.db.UserProfileEntity() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.finance.splitup.data.db.UserProfileEntity())
    } else {
        _userProfileFallback.asStateFlow()
    }

    fun updateUserProfile(upiVpa: String, paypalHandle: String, venmoHandle: String, revolutHandle: String) {
        val cleanUpi = upiVpa.trim()
        val cleanPaypal = paypalHandle.trim().removePrefix("@")
        val cleanVenmo = venmoHandle.trim().removePrefix("@")
        val cleanRevolut = revolutHandle.trim().removePrefix("@")
        val updated = com.finance.splitup.data.db.UserProfileEntity(
            id = "current_user",
            upiVpa = cleanUpi,
            paypalHandle = cleanPaypal,
            venmoHandle = cleanVenmo,
            revolutHandle = cleanRevolut
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.upsertUserProfile(updated)
            }
        } else {
            _userProfileFallback.value = updated
        }

        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser != null) {
            viewModelScope.launch {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val profileData = mapOf(
                        "upiVpa" to cleanUpi,
                        "paypalHandle" to cleanPaypal,
                        "venmoHandle" to cleanVenmo,
                        "revolutHandle" to cleanRevolut
                    )
                    db.collection("users").document(currentUser.uid)
                        .set(profileData, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                } catch (_: Exception) {}
            }
        }
    }

    fun logActivity(groupId: String, actorName: String, actionType: String, description: String, referenceId: String = "") {
        val log = com.finance.splitup.data.db.ActivityLogEntity(
            groupId = groupId,
            actorName = actorName,
            actionType = actionType,
            description = description,
            referenceId = referenceId
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.insertActivityLog(log)
            }
        }
    }

    fun getActivityLogs(groupId: String): kotlinx.coroutines.flow.Flow<List<com.finance.splitup.data.db.ActivityLogEntity>> {
        return repository?.getActivityLogsForGroup(groupId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    private val _incomesFallback = MutableStateFlow<List<com.finance.splitup.data.db.IncomeEntity>>(emptyList())
    private val _recurringExpensesFallback = MutableStateFlow<List<com.finance.splitup.data.db.RecurringExpenseEntity>>(emptyList())

    val incomes: StateFlow<List<com.finance.splitup.data.db.IncomeEntity>> = if (repository != null) {
        repository.getAllIncomes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        _incomesFallback.asStateFlow()
    }

    val recurringExpenses: StateFlow<List<com.finance.splitup.data.db.RecurringExpenseEntity>> = if (repository != null) {
        repository.getAllRecurringExpenses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        _recurringExpensesFallback.asStateFlow()
    }

    val totalIncome: Double
        get() {
            val fromIncomes = incomes.value.sumOf { Currencies.convertToEUR(it.amount, it.currency) }
            val fromExpenseIncomes = expenses.value
                .filter { it.category == ExpenseCategories.INCOME }
                .sumOf { Currencies.convertToEUR(it.amount, it.currency) }
            return fromIncomes + fromExpenseIncomes
        }

    val netCashFlow: Double
        get() = totalIncome - totalSpent

    fun addIncome(title: String, amount: Double, source: String, currency: String = defaultCurrency, date: String = "Today") {
        val income = com.finance.splitup.data.db.IncomeEntity(
            title = title.ifBlank { "Income" },
            amount = amount,
            source = source.ifBlank { "Salary" },
            date = com.finance.splitup.data.DateUtils.normalizeExpenseDate(date),
            currency = currency
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.insertIncome(income)
                com.finance.splitup.data.CloudSyncManager.syncIncomeToCloud(income)
                triggerImmediateSync()
            }
        } else {
            _incomesFallback.update { current -> listOf(income) + current }
        }
    }

    fun deleteIncome(id: String) {
        if (repository != null) {
            viewModelScope.launch {
                repository.deleteIncome(id)
                com.finance.splitup.data.CloudSyncManager.deleteIncomeFromCloud(id)
                triggerImmediateSync()
            }
        } else {
            _incomesFallback.update { current -> current.filter { it.id != id } }
        }
    }

    fun upsertRecurringExpense(
        id: String? = null,
        title: String,
        amount: Double,
        category: String,
        frequency: String,
        nextDueDate: String,
        currency: String = defaultCurrency
    ) {
        val recurring = com.finance.splitup.data.db.RecurringExpenseEntity(
            id = id ?: java.util.UUID.randomUUID().toString(),
            title = title.ifBlank { "Subscription" },
            amount = amount,
            category = category,
            frequency = frequency,
            nextDueDate = nextDueDate.ifBlank { "Next Month" },
            currency = currency
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.upsertRecurringExpense(recurring)
                com.finance.splitup.data.CloudSyncManager.syncRecurringExpenseToCloud(recurring)
                triggerImmediateSync()
            }
        } else {
            _recurringExpensesFallback.update { current -> listOf(recurring) + current.filter { it.id != recurring.id } }
        }
    }

    fun addRecurringExpense(title: String, amount: Double, category: String, frequency: String, nextDueDate: String, currency: String = defaultCurrency) {
        upsertRecurringExpense(null, title, amount, category, frequency, nextDueDate, currency)
    }

    fun deleteRecurringExpense(id: String) {
        if (repository != null) {
            viewModelScope.launch {
                repository.deleteRecurringExpense(id)
                com.finance.splitup.data.CloudSyncManager.deleteRecurringExpenseFromCloud(id)
                triggerImmediateSync()
            }
        } else {
            _recurringExpensesFallback.update { current -> current.filter { it.id != id } }
        }
    }


    init {
        viewModelScope.launch {
            try {
                com.finance.splitup.data.CurrencyApiService.updateLiveRates()
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Failed to update live currency rates", e)
            }
            if (repository != null) {
                try {
                    com.finance.splitup.data.CloudSyncManager.performCloudSync(repository, getApplication())
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Failed to perform initial cloud sync", e)
                }
            }
        }
        startRealtimeGroupListeners()
        checkContactJoinedNotifications()

        try {
            val connectivityObserver = com.finance.splitup.data.NetworkConnectivityObserver(getApplication())
            viewModelScope.launch {
                var initial = true
                connectivityObserver.isOnline.collect { online ->
                    if (initial) {
                        initial = false
                        return@collect
                    }
                    if (online) {
                        triggerImmediateSync()
                        if (repository != null) {
                            com.finance.splitup.data.CloudSyncManager.performCloudSync(repository, getApplication())
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private var realtimeGroupListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var realtimeEmailGroupListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private val notifiedGroupIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val notifiedExpenseIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private var lastSnapshot1: com.google.firebase.firestore.QuerySnapshot? = null
    private var lastSnapshot2: com.google.firebase.firestore.QuerySnapshot? = null

    private fun startRealtimeGroupListeners() {
        try {
            realtimeGroupListenerRegistration?.remove()
            realtimeGroupListenerRegistration = null
            realtimeEmailGroupListenerRegistration?.remove()
            realtimeEmailGroupListenerRegistration = null

            val auth = try { com.google.firebase.auth.FirebaseAuth.getInstance() } catch (_: Exception) { null }
            val user = auth?.currentUser ?: return
            val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { null } ?: return

            val handleSnapshotUpdates = {
                viewModelScope.launch {
                    try {
                        val snap1 = lastSnapshot1
                        val snap2 = lastSnapshot2
                        val context = getApplication<Application>()

                        val deletedGroupIds = try {
                            db.collection("deleted_groups").get().await().documents.map { it.id }.toSet()
                        } catch (_: Exception) { emptySet() }

                        val activeIds = mutableSetOf<String>()
                        val allDocs = (snap1?.documents.orEmpty() + snap2?.documents.orEmpty())

                        for (doc in allDocs) {
                            val groupId = doc.id
                            if (deletedGroupIds.contains(groupId) || com.finance.splitup.data.CloudSyncManager.isGroupLocallyTombstoned(context, groupId)) {
                                com.finance.splitup.data.CloudSyncManager.markGroupLocallyTombstoned(context, groupId)
                                try { doc.reference.delete() } catch (_: Exception) {}
                                try { db.collection("users").document(user.uid).collection("groups").document(groupId).delete() } catch (_: Exception) {}
                                val localGrp = repository?.getGroupById(groupId) ?: _groupsFallback.value.find { it.id == groupId }
                                if (localGrp != null && notifiedGroupIds.add("deleted_$groupId")) {
                                    postSystemNotification(
                                        "🗑️ Group Deleted",
                                        "The group '${localGrp.name}' has been deleted by its owner.",
                                        CHANNEL_GROUP_ACTIVITY,
                                        groupId = groupId
                                    )
                                }
                                repository?.deleteGroup(groupId)
                                continue
                            }
                            activeIds.add(groupId)
                            try {
                                val group = doc.toObject(Group::class.java)
                                if (group != null) {
                                    val existingLocalGroup = repository?.getGroupById(group.id)
                                        ?: _groupsFallback.value.find { it.id == group.id }

                                    val selfName = currentUserName
                                    val userEmail = user.email?.lowercase() ?: ""

                                    if (existingLocalGroup == null) {
                                        val isCreatedBySelf = group.createdBy.equals(selfName, ignoreCase = true) || group.createdBy.equals("You", ignoreCase = true)
                                        val addedBy = group.members.firstOrNull {
                                            !it.equals(selfName, ignoreCase = true) && !it.equals("You", ignoreCase = true)
                                        }
                                        if (!isCreatedBySelf && addedBy != null && notifiedGroupIds.add("new_group_${group.id}")) {
                                            postSystemNotification(
                                                "🎉 Added to New Group",
                                                "You were added to '${group.name}' by $addedBy!",
                                                CHANNEL_GROUP_ACTIVITY,
                                                groupId = group.id
                                            )
                                        }
                                    } else {
                                        val existingExpenseIds = existingLocalGroup.expenses.map { it.id }.toSet()
                                        val newExpenses = group.expenses.filter { exp ->
                                            !existingExpenseIds.contains(exp.id) &&
                                                    !exp.paidBy.equals(selfName, ignoreCase = true) &&
                                                    !exp.paidBy.equals("You", ignoreCase = true)
                                        }
                                        for (newExp in newExpenses) {
                                            if (notifiedExpenseIds.add(newExp.id)) {
                                                val symbol = Currencies.getSymbol(newExp.currency)
                                                val amountStr = String.format(java.util.Locale.US, "%.2f", newExp.amount)
                                                postSystemNotification(
                                                    "💰 New Group Expense Logged",
                                                    "${newExp.paidBy} added '${newExp.title}' ($symbol$amountStr) in '${group.name}'",
                                                    CHANNEL_GROUP_ACTIVITY,
                                                    groupId = group.id
                                                )
                                            }
                                        }

                                        val existingMembersLower = existingLocalGroup.members.map { it.lowercase() }.toSet()
                                        val newMembers = group.members.filter { m ->
                                            !existingMembersLower.contains(m.lowercase()) &&
                                                    !m.equals(selfName, ignoreCase = true) &&
                                                    !m.equals("You", ignoreCase = true)
                                        }
                                        for (newMember in newMembers) {
                                            if (notifiedGroupIds.add("member_${group.id}_$newMember")) {
                                                postSystemNotification(
                                                    "👥 Group Member Joined",
                                                    "$newMember has joined ${group.name}!",
                                                    CHANNEL_GROUP_ACTIVITY,
                                                    groupId = group.id
                                                )
                                            }
                                        }
                                    }

                                    val needsUidUpdate = !group.memberUids.contains(user.uid)
                                    val needsEmailUpdate = userEmail.isNotBlank() && !group.memberEmails.map { it.lowercase() }.contains(userEmail)
                                    val updatedGroup = if (needsUidUpdate || needsEmailUpdate) {
                                        val uids = if (needsUidUpdate) group.memberUids + user.uid else group.memberUids
                                        val emails = if (needsEmailUpdate) group.memberEmails + userEmail else group.memberEmails
                                        group.copy(memberUids = uids.distinct(), memberEmails = emails.map { it.lowercase() }.distinct())
                                    } else group

                                    if (needsUidUpdate || needsEmailUpdate) {
                                        com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup)
                                    }

                                    if (repository != null) {
                                        repository.insertGroup(updatedGroup)
                                    } else {
                                        _groupsFallback.update { current ->
                                            current.map { if (it.id == updatedGroup.id) updatedGroup else it } +
                                                    if (current.none { it.id == updatedGroup.id }) listOf(updatedGroup) else emptyList()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FinanceViewModel", "Error processing realtime group doc", e)
                            }
                        }

                        // Check deleted groups & automatically delete any local groups present in tombstones
                        if (repository != null) {
                            val localGroups = repository.getAllGroups().firstOrNull() ?: emptyList()
                            for (localGroup in localGroups) {
                                val isExplicitlyDeleted = deletedGroupIds.contains(localGroup.id) || com.finance.splitup.data.CloudSyncManager.isGroupLocallyTombstoned(context, localGroup.id)
                                if (isExplicitlyDeleted) {
                                    com.finance.splitup.data.CloudSyncManager.markGroupLocallyTombstoned(context, localGroup.id)
                                    if (notifiedGroupIds.add("deleted_${localGroup.id}")) {
                                        postSystemNotification(
                                            "🗑️ Group Deleted",
                                            "The group '${localGroup.name}' has been deleted by its owner.",
                                            CHANNEL_GROUP_ACTIVITY,
                                            groupId = localGroup.id
                                        )
                                    }
                                    repository.deleteGroup(localGroup.id)
                                    try {
                                        db.collection("users").document(user.uid).collection("groups").document(localGroup.id).delete()
                                    } catch (_: Exception) {}
                                }
                            }
                        } else {
                            _groupsFallback.update { current ->
                                current.filter { !deletedGroupIds.contains(it.id) && !com.finance.splitup.data.CloudSyncManager.isGroupLocallyTombstoned(context, it.id) }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FinanceViewModel", "Error in handleSnapshotUpdates", e)
                    }
                }
            }

            // Listener 1: Filter by memberUids
            realtimeGroupListenerRegistration = db.collection("groups")
                .whereArrayContains("memberUids", user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    lastSnapshot1 = snapshot
                    handleSnapshotUpdates()
                }

            // Listener 2: Filter by memberEmails
            val userEmail = user.email?.lowercase()
            if (!userEmail.isNullOrBlank()) {
                realtimeEmailGroupListenerRegistration = db.collection("groups")
                    .whereArrayContains("memberEmails", userEmail)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        lastSnapshot2 = snapshot
                        handleSnapshotUpdates()
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("FinanceViewModel", "Failed startRealtimeGroupListeners", e)
        }
    }

    fun triggerCloudSync() {
        if (repository != null) {
            viewModelScope.launch {
                com.finance.splitup.data.CloudSyncManager.performCloudSync(repository, getApplication())
            }
        }
        startRealtimeGroupListeners()
        checkContactJoinedNotifications()
    }

    private fun checkContactJoinedNotifications() {
        val app = getApplication<Application>()
        if (app is DummyApplication) return
        viewModelScope.launch {
            try {
                val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { null } ?: return@launch
                val auth = try { com.google.firebase.auth.FirebaseAuth.getInstance() } catch (_: Exception) { null }

                val contacts = com.finance.splitup.data.SampleContacts.loadDeviceContacts(app)
                if (contacts.isEmpty()) return@launch

                val snapshot = db.collection("users").get().await()
                val registeredEmails = snapshot.documents.mapNotNull { it.getString("email")?.lowercase() }.toSet()

                val currentUserEmail = auth?.currentUser?.email?.lowercase()

                for (contact in contacts) {
                    val email = contact.email.lowercase()
                    if (email.isNotBlank() && registeredEmails.contains(email) && email != currentUserEmail) {
                        val prefsKey = "notified_joined_$email"
                        val alreadyNotified = prefs?.getBoolean(prefsKey, false) ?: false
                        if (!alreadyNotified) {
                            postSystemNotification(
                                "Friend Joined SplitUp! 🎉",
                                "Your contact ${contact.name} ($email) is now on SplitUp. Start splitting expenses with them!"
                            )
                            prefs?.edit()?.putBoolean(prefsKey, true)?.apply()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val expenses: StateFlow<List<Expense>> = if (repository != null) {
        repository.getAllExpenses()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    } else {
        _expensesFallback.asStateFlow()
    }

    val budgets: StateFlow<Map<String, Double>> = if (repository != null) {
        repository.getAllBudgets()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    } else {
        _budgetsFallback.asStateFlow()
    }

    val groups: StateFlow<List<Group>> = if (repository != null) {
        repository.getAllGroups()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    } else {
        _groupsFallback.asStateFlow()
    }

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        expenses,
        groups,
        dismissedNotifications
    ) { expensesList, groupsList, _ ->
        val totalPersonalSpent = expensesList
            .filter { it.category != ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
        val totalGroupSpent = groupsList.sumOf { g ->
            calculateGroupMetricsUseCase.calculateTotalGroupSpent(g, defaultCurrency)
        }
        val totalNetOwed = groupsList.sumOf { g ->
            calculateGroupMetricsUseCase.calculateUserNetBalance(g, currentUserName, defaultCurrency)
        }
        DashboardUiState.Success(
            expenses = expensesList,
            groups = groupsList,
            totalPersonalSpent = totalPersonalSpent,
            totalGroupSpent = totalGroupSpent,
            totalNetOwed = totalNetOwed,
            defaultCurrency = defaultCurrency,
            notificationCount = 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    val totalSpent: Double
        get() = expenses.value
            .filter { it.category != ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }

    fun toggleDarkTheme(enabled: Boolean) {
        themeMode = if (enabled) com.finance.splitup.ui.theme.ThemeMode.DARK else com.finance.splitup.ui.theme.ThemeMode.LIGHT
    }

    fun getSpendingForCategory(category: String): Double {
        return expenses.value.asSequence()
            .filter { it.category == category }
            .sumOf { Currencies.convertToEUR(it.amount, it.currency) }
    }

    fun getBudgetForCategory(category: String): Double {
        return budgets.value[category] ?: 200.0
    }

    fun saveBitmapToInternalStorage(bitmap: android.graphics.Bitmap, filename: String = "${java.util.UUID.randomUUID()}.jpg"): String? {
        val app = getApplication<Application>()
        return try {
            val dir = java.io.File(app.filesDir, "receipts")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, filename)
            val stream = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            stream.flush()
            stream.close()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun learnCategory(title: String, category: String) {
        com.finance.splitup.data.CategoryDetector.learnCategory(title, category, getApplication())
    }

    fun addExpense(
        title: String,
        amount: Double,
        category: String,
        date: String,
        description: String,
        paidBy: String,
        currency: String = Currencies.EUR,
        paymentMethod: String = "Cash",
        receiptImagePath: String? = null
    ) {
        val currentSpending = getSpendingForCategory(category)
        val limit = getBudgetForCategory(category)
        val projected = currentSpending + amount
        if (projected > limit) {
            val msg = "Warning: Adding '$title' (${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)}) exceeds your monthly $category budget limit (${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.0f", limit)})!"
            budgetWarningMessage = msg
            postSystemNotification("SplitUp Budget Exceeded", msg, CHANNEL_FINANCIAL_ALERTS)
        } else if (projected >= limit * 0.8) {
            val msg = "Alert: $category spending is at ${(projected / limit * 100).toInt()}% of monthly limit (${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.0f", limit)})."
            budgetWarningMessage = msg
            postSystemNotification("SplitUp Budget Alert", msg, CHANNEL_FINANCIAL_ALERTS)
        }

        val eurAmount = Currencies.convertToEUR(amount, currency)
        if (eurAmount >= 200.0) {
            postSystemNotification(
                "High-Value Expense Logged 💸",
                "You logged '$title' of ${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)} under '$category'.",
                CHANNEL_FINANCIAL_ALERTS
            )
        }

        val normalizedDate = com.finance.splitup.data.DateUtils.normalizeExpenseDate(date)
        val newExpense = Expense(
            title = title,
            amount = amount,
            category = category,
            date = normalizedDate,
            description = description,
            paidBy = paidBy.ifBlank { "You" },
            currency = currency,
            paymentMethod = paymentMethod,
            receiptImagePath = receiptImagePath
        )
        com.finance.splitup.data.CategoryDetector.learnCategory(title, category, getApplication())
        if (repository != null) {
            viewModelScope.launch {
                repository.insertExpense(newExpense)
                com.finance.splitup.data.CloudSyncManager.syncExpenseToCloud(newExpense, getApplication())
                triggerImmediateSync()
            }
        } else {
            _expensesFallback.update { current -> listOf(newExpense) + current }
        }
    }

    fun clearBudgetWarning() {
        budgetWarningMessage = null
    }

    fun deleteExpense(id: String) {
        if (repository != null) {
            viewModelScope.launch {
                repository.deleteExpense(id)
                com.finance.splitup.data.CloudSyncManager.deleteExpenseFromCloud(id)
                triggerImmediateSync()
            }
        } else {
            _expensesFallback.update { current -> current.filter { it.id != id } }
        }
    }

    fun updateExpense(expense: Expense) {
        val normalizedExpense = expense.copy(
            date = com.finance.splitup.data.DateUtils.normalizeExpenseDate(expense.date)
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.insertExpense(normalizedExpense)
                com.finance.splitup.data.CloudSyncManager.syncExpenseToCloud(normalizedExpense, getApplication())
                triggerImmediateSync()
            }
        } else {
            _expensesFallback.update { current ->
                current.map { if (it.id == normalizedExpense.id) normalizedExpense else it }
            }
        }
    }

    fun refreshData() {
        budgetWarningMessage = null
        triggerCloudSync()
    }

    fun updateBudget(category: String, newLimit: Double) {
        if (repository != null) {
            viewModelScope.launch {
                repository.upsertBudget(category, newLimit)
                com.finance.splitup.data.CloudSyncManager.syncBudgetToCloud(category, newLimit)
                triggerImmediateSync()
            }
        } else {
            _budgetsFallback.update { current ->
                current.toMutableMap().apply {
                    this[category] = newLimit
                }
            }
        }
    }

    val currentUserName: String
        get() {
            val userAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val name = userAuth?.displayName?.ifBlank { null }
            val emailName = userAuth?.email?.substringBefore("@")?.ifBlank { null }
            return name ?: emailName ?: "You"
        }

    val currentUserEmail: String
        get() {
            val userAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            return userAuth?.email ?: ""
        }

    // Group Management
    fun addGroup(
        name: String,
        type: String,
        members: List<String>,
        memberEmails: List<String> = emptyList(),
        emoji: String = "✈️",
        colorHex: String = "#3B82F6",
        pendingMembers: List<String> = emptyList()
    ): String {
        val selfName = currentUserName
        val cleanMembers = if (members.isEmpty()) listOf(selfName) else members
        val mappedMembers = cleanMembers.map { if (it == "You") selfName else it }
        val finalMembers = if (mappedMembers.contains(selfName)) mappedMembers else listOf(selfName) + mappedMembers

        val newGroup = Group(
            name = name.ifBlank { "New Group" },
            type = type.ifBlank { "Trip" },
            emoji = emoji,
            colorHex = colorHex,
            members = finalMembers.distinct(),
            pendingMembers = pendingMembers.filter { !it.equals(selfName, ignoreCase = true) && !it.equals("You", ignoreCase = true) }.distinct(),
            memberEmails = memberEmails,
            expenses = emptyList(),
            createdBy = selfName
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.insertGroup(newGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(newGroup, repository, getApplication())
                triggerImmediateSync()
            }
        } else {
            _groupsFallback.update { current -> listOf(newGroup) + current }
        }
        return newGroup.id
    }

    fun acceptGroupInvitation(groupId: String) {
        viewModelScope.launch {
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val selfName = currentUserName
            val userAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val currentUid = userAuth?.uid ?: ""

            val updatedMembers = if (!group.members.contains(selfName)) group.members + selfName else group.members
            val updatedPending = group.pendingMembers.filter { !it.equals(selfName, ignoreCase = true) && !it.equals("You", ignoreCase = true) }
            val updatedUids = if (currentUid.isNotBlank() && !group.memberUids.contains(currentUid)) group.memberUids + currentUid else group.memberUids

            val updatedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberUids = updatedUids)
            logActivity(groupId, selfName, "MEMBER_JOINED", "Accepted group invitation")
            postSystemNotification("Invitation Accepted! 🎉", "You joined the group '${group.name}'.", CHANNEL_GROUP_ACTIVITY)

            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun declineGroupInvitation(groupId: String) {
        viewModelScope.launch {
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val selfName = currentUserName
            val selfEmail = currentUserEmail.lowercase()
            val updatedMembers = group.members.filter { !it.equals(selfName, ignoreCase = true) && !it.equals("You", ignoreCase = true) }
            val updatedPending = group.pendingMembers.filter { !it.equals(selfName, ignoreCase = true) && !it.equals("You", ignoreCase = true) }
            val updatedEmails = group.memberEmails.filter { it.lowercase() != selfEmail }
            val updatedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberEmails = updatedEmails)

            logActivity(groupId, selfName, "MEMBER_DECLINED", "$selfName declined the invitation to join ${group.name}")
            postSystemNotification("Invitation Declined", "$selfName declined the invitation to join ${group.name}.", CHANNEL_GROUP_ACTIVITY)

            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        val group = groups.value.find { it.id == groupId }
        val groupName = group?.name ?: "Group"
        postSystemNotification(
            "🗑️ Group Deleted",
            "You deleted the group '$groupName'.",
            CHANNEL_GROUP_ACTIVITY,
            groupId = groupId
        )
        if (repository != null) {
            viewModelScope.launch {
                com.finance.splitup.data.CloudSyncManager.markGroupLocallyTombstoned(getApplication(), groupId)
                repository.deleteGroup(groupId)
                com.finance.splitup.data.CloudSyncManager.deleteGroupFromCloud(groupId, repository, getApplication())
                triggerImmediateSync()
            }
        } else {
            _groupsFallback.update { current -> current.filter { it.id != groupId } }
        }
    }

    fun archiveGroup(groupId: String) {
        val group = groups.value.find { it.id == groupId } ?: return
        val updated = group.copy(isArchived = true, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            if (repository != null) {
                repository.insertGroup(updated)
                triggerImmediateSync()
            } else {
                _groupsFallback.update { current -> current.map { if (it.id == groupId) updated else it } }
            }
        }
        postSystemNotification(
            "📦 Group Archived",
            "You archived the group '${group.name}'.",
            CHANNEL_GROUP_ACTIVITY,
            groupId = groupId
        )
    }

    fun unarchiveGroup(groupId: String) {
        val group = groups.value.find { it.id == groupId } ?: return
        val updated = group.copy(isArchived = false, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            if (repository != null) {
                repository.insertGroup(updated)
                triggerImmediateSync()
            } else {
                _groupsFallback.update { current -> current.map { if (it.id == groupId) updated else it } }
            }
        }
    }

    private suspend fun verifyGroupActiveOrPurge(groupId: String): Boolean {
        val context = getApplication<Application>()
        if (com.finance.splitup.data.CloudSyncManager.isGroupLocallyTombstoned(context, groupId)) {
            purgeDeletedGroupLocally(groupId)
            return false
        }
        val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { return true }
        try {
            val deletedDoc = db.collection("deleted_groups").document(groupId).get().await()
            if (deletedDoc.exists()) {
                com.finance.splitup.data.CloudSyncManager.markGroupLocallyTombstoned(context, groupId)
                purgeDeletedGroupLocally(groupId)
                return false
            }
        } catch (_: Exception) {
            // Offline fallback
        }
        return true
    }

    private fun purgeDeletedGroupLocally(groupId: String) {
        val groupName = groups.value.find { it.id == groupId }?.name ?: "Group"
        postSystemNotification(
            "⚠️ Action Blocked",
            "The group '$groupName' has been deleted by its owner and can no longer be modified.",
            CHANNEL_GROUP_ACTIVITY
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.deleteGroup(groupId)
                val user = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { null }
                if (db != null && user != null) {
                    try {
                        db.collection("users").document(user.uid).collection("groups").document(groupId).delete().await()
                    } catch (_: Exception) {}
                }
            }
        } else {
            _groupsFallback.update { current -> current.filter { it.id != groupId } }
        }
    }

    fun addGroupMember(groupId: String, memberName: String, memberEmail: String = "", isPending: Boolean = false) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            if (memberName.isBlank()) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val selfName = currentUserName
            val selfEmail = currentUserEmail.lowercase()
            val resolvedName = if (memberName == "You") selfName else memberName

            // Prevent adding oneself or duplicate existing members
            val isSelf = resolvedName.equals(selfName, ignoreCase = true) ||
                    resolvedName.equals("You", ignoreCase = true) ||
                    (memberEmail.isNotBlank() && memberEmail.lowercase() == selfEmail)

            val isAlreadyMember = group.members.any { it.equals(resolvedName, ignoreCase = true) || (it == "You" && isSelf) } ||
                    (memberEmail.isNotBlank() && group.memberEmails.map { it.lowercase() }.contains(memberEmail.lowercase()))

            if (isSelf || isAlreadyMember) {
                return@launch
            }

            val updatedMembers: List<String>
            val updatedPending: List<String>

            if (isPending) {
                updatedMembers = group.members
                updatedPending = if (!group.pendingMembers.contains(resolvedName) && resolvedName != selfName && !group.members.contains(resolvedName)) {
                    group.pendingMembers + resolvedName
                } else group.pendingMembers
            } else {
                updatedMembers = if (!group.members.contains(resolvedName)) group.members + resolvedName else group.members
                updatedPending = group.pendingMembers.filter { !it.equals(resolvedName, ignoreCase = true) }
            }

            val updatedEmails = if (memberEmail.isNotBlank() && !group.memberEmails.contains(memberEmail)) group.memberEmails + memberEmail else group.memberEmails

            if (updatedMembers != group.members || updatedPending != group.pendingMembers || updatedEmails != group.memberEmails) {
                val updatedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberEmails = updatedEmails)
                logActivity(groupId, selfName, "MEMBER_ADDED", "Added member '$resolvedName'")
                postSystemNotification("🎉 Group Member Joined", "$resolvedName has joined ${group.name}!", CHANNEL_GROUP_ACTIVITY)
                // Notify the new member about joining the group
                postSystemNotification("👋 New Group Member", "Welcome to ${group.name}! Join the group as $resolvedName.", CHANNEL_GROUP_ACTIVITY)
                if (repository != null) {
                    repository.insertGroup(updatedGroup)
                    com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                } else {
                    _groupsFallback.update { current ->
                        current.map { if (it.id == groupId) updatedGroup else it }
                    }
                }
            }
        }
    }

    fun removeGroupMember(groupId: String, memberName: String, memberEmail: String = "") {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val selfName = currentUserName
            if (memberName == "You" || memberName.equals(selfName, ignoreCase = true)) return@launch
            val group = groups.value.find { it.id == groupId } ?: return@launch
            
            val targetNameLower = memberName.lowercase()
            val targetEmailLower = memberEmail.lowercase()

            val updatedMembers = group.members.filter { !it.equals(memberName, ignoreCase = true) && it.lowercase() != targetEmailLower }
            val updatedPending = group.pendingMembers.filter { !it.equals(memberName, ignoreCase = true) && it.lowercase() != targetEmailLower }
            val updatedEmails = group.memberEmails.filter { email ->
                val emailLower = email.lowercase()
                val emailPrefix = emailLower.substringBefore("@")
                emailLower != targetEmailLower && emailLower != targetNameLower && emailPrefix != targetNameLower
            }
            val updatedGroup = group.copy(
                members = updatedMembers,
                pendingMembers = updatedPending,
                memberEmails = updatedEmails
            )
            // Notify remaining members about the departure
            if (updatedMembers.isNotEmpty()) {
                postSystemNotification("👥 Group Member Left", "$memberName has left ${group.name} group.", CHANNEL_GROUP_ACTIVITY)
            }
            logActivity(groupId, selfName, "MEMBER_REMOVED", "Removed member '$memberName'")
            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun joinGroupViaInvite(groupId: String, onJoined: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!verifyGroupActiveOrPurge(groupId)) {
                    onError("This group has been deleted by its owner.")
                    return@launch
                }
                val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Exception) {
                    onError(e.localizedMessage ?: "Firebase not configured")
                    return@launch
                }
                val userAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }

                val snapshot = db.collection("groups").document(groupId).get().await()
                if (snapshot.exists()) {
                    val group = snapshot.toObject(Group::class.java)
                    if (group != null) {
                        val currentUserName = userAuth?.displayName ?: userAuth?.email?.substringBefore("@") ?: "Friend"
                        val currentUid = userAuth?.uid ?: ""

                        val updatedMembers = if (group.members.contains(currentUserName)) group.members else group.members + currentUserName
                        val updatedPending = group.pendingMembers.filter { !it.equals(currentUserName, ignoreCase = true) }
                        val updatedUids = if (currentUid.isNotBlank() && !group.memberUids.contains(currentUid)) group.memberUids + currentUid else group.memberUids

                        val joinedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberUids = updatedUids)

                        if (repository != null) {
                            repository.insertGroup(joinedGroup)
                        }
                        com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(joinedGroup, repository, getApplication())
                        postSystemNotification(
                            "Member Joined Group! 🎉",
                            "$currentUserName has accepted the invite and joined '${group.name}'."
                        )
                        onJoined()
                    } else {
                        onError("Group not found")
                    }
                } else {
                    onError("Invite link expired or group does not exist")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to join group")
            }
        }
    }

    fun addGroupExpense(
        groupId: String,
        title: String,
        amount: Double,
        paidBy: String,
        splitAmong: List<String>,
        date: String,
        currency: String = Currencies.EUR,
        splitMode: String = "EQUAL",
        memberSplits: Map<String, Double> = emptyMap(),
        category: String = ExpenseCategories.OTHER,
        receiptImagePath: String? = null
    ) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val actualPayer = paidBy.ifBlank { "You" }
            val actualTitle = title.ifBlank { "Shared Expense" }
            val normalizedDate = com.finance.splitup.data.DateUtils.normalizeExpenseDate(date)
            val expense = GroupExpense(
                title = actualTitle,
                amount = amount,
                paidBy = actualPayer,
                date = normalizedDate,
                splitAmong = if (splitAmong.isEmpty()) listOf("You") else splitAmong,
                currency = currency,
                splitMode = splitMode,
                memberSplits = memberSplits,
                category = category,
                receiptImagePath = receiptImagePath
            )
            com.finance.splitup.data.CategoryDetector.learnCategory(actualTitle, category, getApplication())
            val updatedGroup = group.copy(expenses = listOf(expense) + group.expenses)
            val symbol = Currencies.getSymbol(currency)
            logActivity(groupId, actualPayer, "EXPENSE_ADDED", "Added '$actualTitle' ($symbol${String.format(java.util.Locale.US, "%.2f", amount)})", referenceId = expense.id)

            val selfName = currentUserName
            val isSelfPayer = actualPayer.equals(selfName, ignoreCase = true) || actualPayer.equals("You", ignoreCase = true)
            if (!isSelfPayer) {
                if (category == ExpenseCategories.SETTLEMENT || actualTitle.contains("Settlement", ignoreCase = true)) {
                    postSystemNotification(
                        "Settlement Payment Recorded 🎉",
                        "$actualPayer recorded settlement of $symbol${String.format(java.util.Locale.US, "%.2f", amount)} in '${group.name}'",
                        CHANNEL_GROUP_ACTIVITY
                    )
                } else {
                    postSystemNotification(
                        "New Group Expense Logged 💰",
                        "$actualPayer added '$actualTitle' ($symbol${String.format(java.util.Locale.US, "%.2f", amount)}) in '${group.name}'",
                        CHANNEL_GROUP_ACTIVITY
                    )
                }
            }

            val currentSpending = getSpendingForCategory(category)
            val limit = getBudgetForCategory(category)
            val projected = currentSpending + amount
            if (projected > limit) {
                val msg = "Warning: Adding '$actualTitle' ($symbol${String.format(java.util.Locale.US, "%.2f", amount)}) exceeds $category budget limit ($symbol${String.format(java.util.Locale.US, "%.0f", limit)})!"
                budgetWarningMessage = msg
                postSystemNotification("SplitUp Budget Exceeded", msg, CHANNEL_FINANCIAL_ALERTS)
            } else if (projected >= limit * 0.8) {
                val msg = "Alert: $category spending reached ${(projected / limit * 100).toInt()}% of monthly limit ($symbol${String.format(java.util.Locale.US, "%.0f", limit)})."
                budgetWarningMessage = msg
                postSystemNotification("SplitUp Budget Alert", msg, CHANNEL_FINANCIAL_ALERTS)
            }
            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                val notifTitle = if (category == ExpenseCategories.SETTLEMENT || actualTitle.contains("Settlement", ignoreCase = true)) {
                    "Settlement Payment Recorded 🎉"
                } else {
                    "New Group Expense 💰"
                }
                val notifBody = "$actualPayer added '$actualTitle' ($symbol${String.format(java.util.Locale.US, "%.2f", amount)}) in '${group.name}'"
                com.finance.splitup.data.CloudSyncManager.sendGroupActivityNotification(updatedGroup, notifTitle, notifBody)
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun deleteGroupExpense(groupId: String, expenseId: String) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val deletedExpense = group.expenses.find { it.id == expenseId }
            val updatedGroup = group.copy(expenses = group.expenses.filter { it.id != expenseId })
            if (deletedExpense != null) {
                logActivity(groupId, "You", "EXPENSE_DELETED", "Deleted expense '${deletedExpense.title}'")
            }
            com.finance.splitup.data.CloudSyncManager.deleteGroupExpenseFromCloud(groupId, expenseId)
            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun updateGroupExpense(groupId: String, expense: GroupExpense) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val normalizedExpense = expense.copy(
                date = com.finance.splitup.data.DateUtils.normalizeExpenseDate(expense.date)
            )
            val updatedExpenses = group.expenses.map { if (it.id == normalizedExpense.id) normalizedExpense else it }
            val updatedGroup = group.copy(expenses = updatedExpenses)
            logActivity(groupId, normalizedExpense.paidBy, "EXPENSE_UPDATED", "Updated expense '${normalizedExpense.title}'")
            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun updateGroup(groupId: String, name: String, type: String, members: List<String>, emoji: String = "✈️", colorHex: String = "#3B82F6") {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val updatedGroup = group.copy(
                name = name.ifBlank { group.name },
                type = type,
                emoji = emoji.ifBlank { group.emoji },
                colorHex = colorHex.ifBlank { group.colorHex },
                members = members.ifEmpty { group.members }
            )
            if (repository != null) {
                repository.insertGroup(updatedGroup)
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
            } else {
                _groupsFallback.update { current ->
                    current.map { if (it.id == groupId) updatedGroup else it }
                }
            }
        }
    }

    fun getUserNetBalance(group: Group, targetCurrency: String = group.expenses.firstOrNull()?.currency ?: defaultCurrency): Double {
        return calculateGroupMetricsUseCase.calculateUserNetBalance(group, currentUserName, targetCurrency)
    }

    fun calculateNetBalances(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: defaultCurrency
    ): Map<String, Double> {
        return calculateGroupSettlementsUseCase.calculateNetBalances(group, targetCurrency, currentUserName)
    }

    fun calculateSettlements(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: defaultCurrency
    ): List<Settlement> {
        return calculateGroupSettlementsUseCase.calculateSettlements(group, targetCurrency, currentUserName)
    }

    fun exportBackupJson(): String {
        val payload = BackupPayload(
            expenses = expenses.value,
            budgets = budgets.value,
            groups = groups.value,
            incomes = incomes.value,
            recurringExpenses = recurringExpenses.value
        )
        return Json.encodeToString(payload)
    }

    fun importBackupJson(jsonStr: String) {
        try {
            val payload = Json.decodeFromString<BackupPayload>(jsonStr)
            viewModelScope.launch {
                if (repository != null) {
                    for (exp in payload.expenses) {
                        repository.insertExpense(exp)
                    }
                    for ((cat, limit) in payload.budgets.entries) {
                        repository.upsertBudget(cat, limit)
                    }
                    for (grp in payload.groups) {
                        repository.insertGroup(grp)
                    }
                    for (inc in payload.incomes) {
                        repository.insertIncome(inc)
                    }
                    for (rec in payload.recurringExpenses) {
                        repository.upsertRecurringExpense(rec)
                    }
                } else {
                    _expensesFallback.value = payload.expenses
                    _budgetsFallback.value = payload.budgets
                    _groupsFallback.value = payload.groups
                    _incomesFallback.value = payload.incomes
                    _recurringExpensesFallback.value = payload.recurringExpenses
                }
            }
        } catch (_: Exception) {
            // Handle import gracefully
        }
    }

    companion object {
        const val CHANNEL_GROUP_ACTIVITY = "splitup_group_activity"
        const val CHANNEL_FINANCIAL_ALERTS = "splitup_financial_alerts"
        const val CHANNEL_RECURRING_BILLS = "splitup_recurring_bills"
        const val CHANNEL_PAYMENT_DETECTION = "splitup_payment_detection"
        const val CHANNEL_EXPORTS_INSIGHTS = "splitup_exports_insights"
        const val CHANNEL_SECURITY_ALERTS = "splitup_security_alerts"
    }

    private fun createAllNotificationChannels(manager: android.app.NotificationManager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channels = listOf(
                android.app.NotificationChannel(
                    CHANNEL_GROUP_ACTIVITY,
                    "Group Activity & Shared Expenses",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifications for new group expenses, settlements, and member updates" },
                android.app.NotificationChannel(
                    CHANNEL_FINANCIAL_ALERTS,
                    "Budget & Spending Alerts",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Alerts when budget thresholds are reached or high-value expenses are logged" },
                android.app.NotificationChannel(
                    CHANNEL_RECURRING_BILLS,
                    "Subscriptions & Recurring Bills",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifications for auto-logged subscriptions and bill reminders" },
                android.app.NotificationChannel(
                    CHANNEL_PAYMENT_DETECTION,
                    "Payment Auto-Detector",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Prompts when bank/wallet transaction notifications are detected" },
                android.app.NotificationChannel(
                    CHANNEL_EXPORTS_INSIGHTS,
                    "Reports & AI Insights",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Notifications when PDF reports are generated or AI advice is ready" },
                android.app.NotificationChannel(
                    CHANNEL_SECURITY_ALERTS,
                    "Account & Security Alerts",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Alerts for password updates and account security changes" }
            )
            for (ch in channels) {
                manager.createNotificationChannel(ch)
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun postSystemNotification(
        title: String,
        message: String,
        channelId: String = CHANNEL_FINANCIAL_ALERTS,
        targetScreen: String? = null,
        groupId: String? = null,
        category: String? = null,
        pendingIntent: android.app.PendingIntent? = null
    ) {
        val app = getApplication<Application>()
        if (app is DummyApplication) return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        app,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            val notificationManager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (notificationManager != null) {
                createAllNotificationChannels(notificationManager)
            }

            val finalTargetScreen = targetScreen ?: when {
                !groupId.isNullOrBlank() -> "group_detail"
                !category.isNullOrBlank() -> "personal_expenses"
                channelId == CHANNEL_GROUP_ACTIVITY -> "groups"
                channelId == CHANNEL_SECURITY_ALERTS -> "settings"
                channelId == CHANNEL_FINANCIAL_ALERTS -> "personal_expenses"
                else -> "dashboard"
            }

            val intent = android.content.Intent(app, com.finance.splitup.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_target_screen", finalTargetScreen)
                if (!groupId.isNullOrBlank()) putExtra("extra_group_id", groupId)
                if (!category.isNullOrBlank()) putExtra("extra_category", category)
            }

            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE

            val effectivePendingIntent = pendingIntent ?: android.app.PendingIntent.getActivity(
                app,
                System.currentTimeMillis().toInt() + (0..1000).random(),
                intent,
                flags
            )

            val builder = androidx.core.app.NotificationCompat.Builder(app, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(effectivePendingIntent)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

            val notificationId = System.currentTimeMillis().toInt() + (0..1000).random()
            androidx.core.app.NotificationManagerCompat.from(app).notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        realtimeGroupListenerRegistration?.remove()
        realtimeGroupListenerRegistration = null
        realtimeEmailGroupListenerRegistration?.remove()
        realtimeEmailGroupListenerRegistration = null
    }
}

@Serializable
data class BackupPayload(
    val expenses: List<Expense> = emptyList(),
    val budgets: Map<String, Double> = emptyMap(),
    val groups: List<Group> = emptyList(),
    val incomes: List<com.finance.splitup.data.db.IncomeEntity> = emptyList(),
    val recurringExpenses: List<com.finance.splitup.data.db.RecurringExpenseEntity> = emptyList()
)
