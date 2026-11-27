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
import kotlinx.coroutines.flow.first
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
    var biometricLockTimeoutMins by androidx.compose.runtime.mutableIntStateOf(prefs?.getInt("biometric_timeout_mins", 0) ?: 0)
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
        loadPendingApprovalRequests()
        try {
            com.finance.splitup.data.CategoryDetector.init(getApplication())
        } catch (_: Exception) {}
    }

    private fun loadPendingApprovalRequests() {
        viewModelScope.launch {
            if (repository != null) {
                try {
                    repository.getPendingApprovalRequestsForOwner().collect { list ->
                        _pendingApprovalRequestsFallback.value = list.filter { req ->
                            req.status == "PENDING" && !isUserMatch(req.requestedBy, currentUserName, currentUserEmail)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
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

            // Fast path: exact email doc in the bounded user_lookup collection.
            val lookupDoc = try {
                db.collection("user_lookup").document(emailQuery.trim().lowercase()).get().await()
            } catch (_: Exception) { null }
            if (lookupDoc != null && lookupDoc.exists()) {
                val email = lookupDoc.getString("email") ?: emailQuery.trim().lowercase()
                return toAppContact(lookupDoc.getString("name"), email, lookupDoc)
            }

            // Fallback: legacy scan over users collection (for members who
            // haven't written a lookup doc yet).
            val snapshot = db.collection("users").get().await()
            for (doc in snapshot.documents) {
                val email = doc.getString("email") ?: continue
                if (email.equals(emailQuery.trim(), ignoreCase = true)) {
                    return toAppContact(doc.getString("name") ?: doc.getString("displayName"), email, doc)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun toAppContact(
        rawName: String?,
        email: String,
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): com.finance.splitup.data.AppContact {
        val name = if (!rawName.isNullOrBlank() && rawName != email) {
            rawName
        } else {
            val prefix = email.substringBefore("@")
            prefix.replace(".", " ").replace("_", " ").split(" ").joinToString(" ") {
                if (it.isNotEmpty()) it.replaceFirstChar { c -> c.uppercase() } else ""
            }
        }
        return com.finance.splitup.data.AppContact(
            name = name,
            email = email,
            isSplitUpMember = true,
            upiVpa = doc.getString("upiVpa") ?: "",
            paypalHandle = doc.getString("paypalHandle") ?: "",
            venmoHandle = doc.getString("venmoHandle") ?: "",
            revolutHandle = doc.getString("revolutHandle") ?: ""
        )
    }

    fun fetchRegisteredUsers() {
        viewModelScope.launch {
            try {
                val db = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { null } ?: return@launch

                // Preferred: bounded user_lookup collection (public fields only).
                val lookupSnapshot = try {
                    db.collection("user_lookup").get().await()
                } catch (_: Exception) { null }

                if (lookupSnapshot != null && !lookupSnapshot.isEmpty) {
                    val users = lookupSnapshot.documents.mapNotNull { doc ->
                        val email = doc.getString("email") ?: return@mapNotNull null
                        toAppContact(doc.getString("name"), email, doc)
                    }
                    _registeredUsers.value = users
                    _registeredUserEmails.value = users.map { it.email.lowercase() }.toSet()
                    return@launch
                }

                // Fallback: legacy scan over the users collection.
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

    fun updateBiometricLockTimeout(mins: Int) {
        biometricLockTimeoutMins = mins
        prefs?.edit()?.putInt("biometric_timeout_mins", mins)?.apply()
    }

    fun syncUserDisplayNameChange(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank() || oldName.equals(newName, ignoreCase = true)) return
        viewModelScope.launch {
            val currentGroups = groups.value
            for (group in currentGroups) {
                val needsMemberUpdate = group.members.any { it.equals(oldName, ignoreCase = true) }
                val needsCreatorUpdate = group.createdBy.equals(oldName, ignoreCase = true)
                val needsExpenseUpdate = group.expenses.any { 
                    it.paidBy.equals(oldName, ignoreCase = true) || 
                    it.splitAmong.any { s -> s.equals(oldName, ignoreCase = true) } ||
                    it.memberSplits.keys.any { k -> k.equals(oldName, ignoreCase = true) }
                }

                if (needsMemberUpdate || needsCreatorUpdate || needsExpenseUpdate) {
                    val updatedMembers = group.members.map { if (it.equals(oldName, ignoreCase = true)) newName else it }
                    val updatedCreator = if (group.createdBy.equals(oldName, ignoreCase = true)) newName else group.createdBy
                    val updatedExpenses = group.expenses.map { exp ->
                        val updatedPaidBy = if (exp.paidBy.equals(oldName, ignoreCase = true)) newName else exp.paidBy
                        val updatedSplitAmong = exp.splitAmong.map { if (it.equals(oldName, ignoreCase = true)) newName else it }
                        val updatedMemberSplits = exp.memberSplits.mapKeys { if (it.key.equals(oldName, ignoreCase = true)) newName else it.key }
                        exp.copy(
                            paidBy = updatedPaidBy,
                            splitAmong = updatedSplitAmong,
                            memberSplits = updatedMemberSplits
                        )
                    }
                    val updatedGroup = group.copy(
                        members = updatedMembers,
                        createdBy = updatedCreator,
                        expenses = updatedExpenses
                    )
                    if (repository != null) {
                        repository.insertGroup(updatedGroup)
                        com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                    } else {
                        _groupsFallback.update { current ->
                            current.map { if (it.id == group.id) updatedGroup else it }
                        }
                    }
                }
            }
        }
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

    suspend fun clearAllData() {
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
            prefs?.edit()?.clear()?.apply()
        } catch (_: Exception) {}

        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser != null) {
            try {
                com.finance.splitup.data.CloudSyncManager.clearCloudDataPreservingSharedGroups(currentUser.uid, currentUser.email)
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Failed clearing cloud data on clearAllData", e)
            }
        }

        if (repository != null) {
            try {
                repository.clearAllLocalData()
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Failed to clear local data on clearAllData", e)
            }
        }
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
                        "revolutHandle" to cleanRevolut,
                        "isOnboardingCompleted" to true,
                        "has_completed_onboarding" to true
                    )
                    db.collection("users").document(currentUser.uid)
                        .set(profileData, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    // Mirror payment handles into the public lookup doc.
                    com.finance.splitup.data.CloudSyncManager.updateUserLookup(
                        name = currentUser.displayName,
                        email = currentUser.email,
                        handles = mapOf(
                            "upiVpa" to cleanUpi,
                            "paypalHandle" to cleanPaypal,
                            "venmoHandle" to cleanVenmo,
                            "revolutHandle" to cleanRevolut
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser != null) {
            viewModelScope.launch {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("users").document(currentUser.uid)
                        .set(
                            mapOf(
                                "isOnboardingCompleted" to completed,
                                "has_completed_onboarding" to completed,
                                "updatedAt" to System.currentTimeMillis()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Failed to set onboarding status in cloud", e)
                }
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

    val allActivityLogs: StateFlow<List<com.finance.splitup.data.db.ActivityLogEntity>> = if (repository != null) {
        repository.getAllActivityLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        MutableStateFlow<List<com.finance.splitup.data.db.ActivityLogEntity>>(emptyList()).asStateFlow()
    }

    private val _pendingApprovalRequestsFallback = MutableStateFlow<List<com.finance.splitup.data.db.GroupApprovalRequestEntity>>(emptyList())
    val pendingApprovalRequests: StateFlow<List<com.finance.splitup.data.db.GroupApprovalRequestEntity>> = if (repository != null) {
        repository.getPendingApprovalRequestsForOwner()
            .map { list ->
                list.filter { req ->
                    req.status == "PENDING" && !isUserMatch(req.requestedBy, currentUserName, currentUserEmail)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _pendingApprovalRequestsFallback.value)
    } else {
        _pendingApprovalRequestsFallback.asStateFlow()
    }

    private val _incomesFallback = MutableStateFlow<List<com.finance.splitup.data.db.IncomeEntity>>(emptyList())
    private val _investmentsFallback = MutableStateFlow<List<com.finance.splitup.data.db.InvestmentEntity>>(emptyList())
    private val _recurringExpensesFallback = MutableStateFlow<List<com.finance.splitup.data.db.RecurringExpenseEntity>>(emptyList())

    val incomes: StateFlow<List<com.finance.splitup.data.db.IncomeEntity>> = if (repository != null) {
        repository.getAllIncomes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        _incomesFallback.asStateFlow()
    }

    val investments: StateFlow<List<com.finance.splitup.data.db.InvestmentEntity>> = if (repository != null) {
        repository.getAllInvestments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        _investmentsFallback.asStateFlow()
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
        val eurAmount = Currencies.convertToEUR(amount, currency)
        if (eurAmount >= INCOME_ALERT_EUR_THRESHOLD) {
            postSystemNotification(
                "High Income Recorded 💰",
                "You logged income '${income.title}' of ${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)}.",
                CHANNEL_FINANCIAL_ALERTS
            )
        }
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

    fun addInvestment(title: String, amount: Double, category: String, brokerOrPlatform: String = "", transactionType: String = "BUY", currency: String = defaultCurrency, date: String = "Today", description: String = "") {
        val investment = com.finance.splitup.data.db.InvestmentEntity(
            title = title.ifBlank { "Investment" },
            amount = amount,
            category = category.ifBlank { "Stocks & Equities" },
            brokerOrPlatform = brokerOrPlatform,
            transactionType = transactionType,
            description = description,
            date = com.finance.splitup.data.DateUtils.normalizeExpenseDate(date),
            currency = currency
        )
        val eurAmount = Currencies.convertToEUR(amount, currency)
        if (eurAmount >= INVESTMENT_ALERT_EUR_THRESHOLD) {
            postSystemNotification(
                "Investment Recorded 📈",
                "You logged investment '${investment.title}' of ${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)}.",
                CHANNEL_FINANCIAL_ALERTS
            )
        }
        if (repository != null) {
            viewModelScope.launch {
                repository.insertInvestment(investment)
                com.finance.splitup.data.CloudSyncManager.syncInvestmentToCloud(investment)
                triggerImmediateSync()
            }
        } else {
            _investmentsFallback.update { current -> listOf(investment) + current }
        }
    }

    fun deleteInvestment(id: String) {
        if (repository != null) {
            viewModelScope.launch {
                repository.deleteInvestment(id)
                com.finance.splitup.data.CloudSyncManager.deleteInvestmentFromCloud(id)
                triggerImmediateSync()
            }
        } else {
            _investmentsFallback.update { current -> current.filter { it.id != id } }
        }
    }

    fun updateInvestment(investment: com.finance.splitup.data.db.InvestmentEntity) {
        val normalized = investment.copy(
            date = com.finance.splitup.data.DateUtils.normalizeExpenseDate(investment.date),
            updatedAt = System.currentTimeMillis(),
            isDirty = true
        )
        if (repository != null) {
            viewModelScope.launch {
                repository.insertInvestment(normalized)
                com.finance.splitup.data.CloudSyncManager.syncInvestmentToCloud(normalized)
                triggerImmediateSync()
            }
        } else {
            _investmentsFallback.update { current ->
                current.map { if (it.id == normalized.id) normalized else it }
            }
        }
    }

    fun upsertRecurringExpense(
        id: String? = null,
        title: String,
        amount: Double,
        category: String,
        frequency: String,
        nextDueDate: String,
        currency: String = defaultCurrency,
        isIncome: Boolean = false
    ) {
        val recurring = com.finance.splitup.data.db.RecurringExpenseEntity(
            id = id ?: java.util.UUID.randomUUID().toString(),
            title = title.ifBlank { "Subscription" },
            amount = amount,
            category = category,
            frequency = frequency,
            nextDueDate = nextDueDate.ifBlank { "Next Month" },
            currency = currency,
            isIncome = isIncome
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

    fun addRecurringExpense(title: String, amount: Double, category: String, frequency: String, nextDueDate: String, currency: String = defaultCurrency, isIncome: Boolean = false) {
        upsertRecurringExpense(null, title, amount, category, frequency, nextDueDate, currency, isIncome)
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
                // After the initial sync completes, drop receipt image files no
                // longer referenced by any expense.
                cleanupOrphanedReceipts()
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
                        // Cap the seen-id sets so long sessions don't grow them unbounded.
                        if (notifiedGroupIds.size > 1000) notifiedGroupIds.clear()
                        if (notifiedExpenseIds.size > 1000) notifiedExpenseIds.clear()

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
                                    if (existingLocalGroup != null && existingLocalGroup.updatedAt >= group.updatedAt) {
                                        continue
                                    }

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

                                    val isUserMemberOrPending = group.members.any { it.equals("You", ignoreCase = true) || it.equals(selfName, ignoreCase = true) || (userEmail.isNotBlank() && it.equals(userEmail, ignoreCase = true)) } ||
                                            group.pendingMembers.any { it.equals("You", ignoreCase = true) || it.equals(selfName, ignoreCase = true) || (userEmail.isNotBlank() && it.equals(userEmail, ignoreCase = true)) } ||
                                            (group.createdBy.isNotBlank() && (group.createdBy.equals(selfName, ignoreCase = true) || group.createdBy.equals("You", ignoreCase = true)))

                                    val needsUidUpdate = isUserMemberOrPending && !group.memberUids.contains(user.uid)
                                    val needsEmailUpdate = isUserMemberOrPending && userEmail.isNotBlank() && !group.memberEmails.map { it.lowercase() }.contains(userEmail)
                                    val updatedGroup = if (needsUidUpdate || needsEmailUpdate) {
                                        val uids = if (needsUidUpdate) group.memberUids + user.uid else group.memberUids
                                        val emails = if (needsEmailUpdate) group.memberEmails + userEmail else group.memberEmails
                                        group.copy(memberUids = uids.distinct(), memberEmails = emails.map { it.lowercase() }.distinct())
                                    } else group

                                    if (needsUidUpdate || needsEmailUpdate) {
                                        com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup)
                                    }

                                    if (!isUserMemberOrPending && repository != null) {
                                        repository.deleteGroup(group.id)
                                    } else if (repository != null) {
                                        repository.insertGroup(updatedGroup)
                                    } else {
                                        _groupsFallback.update { current ->
                                            if (isUserMemberOrPending) {
                                                current.map { if (it.id == updatedGroup.id) updatedGroup else it } +
                                                        if (current.none { it.id == updatedGroup.id }) listOf(updatedGroup) else emptyList()
                                            } else {
                                                current.filter { it.id != group.id }
                                            }
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

    /** True when the local Room DB already holds user-created data (used to skip
     *  re-onboarding for a guest who just signed up and transferred their data). */
    suspend fun hasLocalData(): Boolean {
        val repo = repository ?: return false
        return try {
            repo.getExpenseCount() > 0 || repo.getGroupCount() > 0 ||
                repo.getBudgetCount() > 0 || repo.getIncomeCount() > 0
        } catch (_: Exception) {
            false
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

    private fun getLatestGroupActivityTimestamp(group: Group): Long {
        val maxExpenseTime = group.expenses.maxOfOrNull { exp ->
            try {
                val parsed = java.time.LocalDateTime.parse(exp.date, java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy, h:mm a", java.util.Locale.US))
                parsed.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        } ?: 0L
        return maxOf(group.updatedAt, maxExpenseTime)
    }

    val groups: StateFlow<List<Group>> = if (repository != null) {
        repository.getAllGroups()
            .map { list -> list.sortedByDescending { getLatestGroupActivityTimestamp(it) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    } else {
        _groupsFallback
            .map { list -> list.sortedByDescending { getLatestGroupActivityTimestamp(it) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private val _selectedDashboardDateRange = MutableStateFlow("This Month")
    val selectedDashboardDateRange: StateFlow<String> = _selectedDashboardDateRange.asStateFlow()

    fun updateDashboardDateRange(range: String) {
        _selectedDashboardDateRange.value = range
    }

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        expenses,
        groups,
        budgets,
        incomes,
        recurringExpenses,
        investments,
        _selectedDashboardDateRange
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val expList = flows[0] as List<Expense>
        @Suppress("UNCHECKED_CAST")
        val grpList = flows[1] as List<Group>
        @Suppress("UNCHECKED_CAST")
        val budgetMap = flows[2] as Map<String, Double>
        val range = flows[6] as String

        val selfName = currentUserName
        val selfEmail = currentUserEmail
        val targetCurr = defaultCurrency

        val filteredExp = expList.filter { com.finance.splitup.data.DateUtils.matchesDateRange(it.date, it.updatedAt, range) }
        val filteredInc = (flows[3] as List<com.finance.splitup.data.db.IncomeEntity>).filter { com.finance.splitup.data.DateUtils.matchesDateRange(it.date, it.updatedAt, range) }
        val filteredInv = (flows[5] as List<com.finance.splitup.data.db.InvestmentEntity>).filter { com.finance.splitup.data.DateUtils.matchesDateRange(it.date, it.updatedAt, range) }

        val spent = filteredExp
            .filter { it.category != ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, targetCurr) }

        val expenseIncomes = filteredExp
            .filter { it.category == ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, targetCurr) }

        val registeredIncomes = filteredInc
            .sumOf { Currencies.convert(it.amount, it.currency, targetCurr) }

        val registeredInvestments = filteredInv
            .sumOf { Currencies.convert(it.amount, it.currency, targetCurr) }

        val income = registeredIncomes + expenseIncomes

        val cashFlow = income - spent

        var owedToUser = 0.0
        var userOwes = 0.0
        for (g in grpList) {
            val bal = calculateGroupMetricsUseCase.calculateUserNetBalance(g, selfName, targetCurr, selfEmail)
            if (bal > 0.01) {
                owedToUser += bal
            } else if (bal < -0.01) {
                userOwes += -bal
            }
        }
        val totalGroupBal = owedToUser - userOwes

        val nonIncomeExp = filteredExp.filter { it.category != ExpenseCategories.INCOME }
        val categoryTotals = nonIncomeExp.groupBy { it.category }.mapValues { entry ->
            entry.value.sumOf { Currencies.convert(it.amount, it.currency, targetCurr) }
        }
        val categoryBreakdownList = categoryTotals.map { (cat, amt) ->
            com.finance.splitup.ui.state.CategorySpend(
                category = cat,
                amount = amt,
                percentage = if (spent > 0.0) (amt / spent) * 100.0 else 0.0
            )
        }.sortedByDescending { it.amount }

        val recurringList = flows[4] as List<com.finance.splitup.data.db.RecurringExpenseEntity>
        val subsCount = recurringList.count { !it.isIncome && it.category != ExpenseCategories.INCOME }

        val exceededCount = budgetMap.count { (cat, limit) ->
            val catSpent = expList.filter { it.category == cat && com.finance.splitup.data.DateUtils.matchesDateRange(it.date, it.updatedAt, range) }
                .sumOf { Currencies.convert(it.amount, it.currency, targetCurr) }
            limit > 0.0 && catSpent >= limit
        }

        DashboardUiState.Success(
            data = com.finance.splitup.ui.state.DashboardUiData(
                selectedDateRange = range,
                totalSpent = spent,
                totalIncome = income,
                totalInvestments = registeredInvestments,
                netCashFlow = cashFlow,
                netGroupOwed = owedToUser,
                netGroupOwes = userOwes,
                totalGroupBalance = totalGroupBal,
                categoryBreakdown = categoryBreakdownList,
                activeSubscriptionsCount = subsCount,
                exceededBudgetsCount = exceededCount,
                defaultCurrency = targetCurr,
                filteredExpenses = filteredExp,
                groups = grpList,
                isLoading = false
            )
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
            .sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
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
            java.io.FileOutputStream(file).use { stream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                stream.flush()
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes receipt image files in filesDir/receipts that are no longer
     * referenced by any expense (personal or group). Expenses are deleted and
     * re-scanned over time; without this sweep the folder grows forever.
     * Safe against in-flight saves: a file is only removed when no record
     * points to it, and re-captures always create a new UUID filename.
     */
    fun cleanupOrphanedReceipts() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val repo = repository ?: return@launch
                val referencedPaths = mutableSetOf<String>()
                repo.getAllExpenses().first().forEach { exp -> exp.receiptImagePath?.let { referencedPaths.add(it) } }
                repo.getAllGroups().first().forEach { group ->
                    group.expenses.forEach { gExp -> gExp.receiptImagePath?.let { referencedPaths.add(it) } }
                }
                val dir = java.io.File(getApplication<Application>().filesDir, "receipts")
                val files = dir.listFiles() ?: return@launch
                var deleted = 0
                for (file in files) {
                    val path = file.absolutePath
                    if (path !in referencedPaths) {
                        if (file.delete()) deleted++
                    }
                }
                if (deleted > 0) {
                    android.util.Log.d("FinanceViewModel", "Cleaned up $deleted orphaned receipt image(s)")
                }
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Receipt cleanup failed", e)
            }
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
        receiptImagePath: String? = null,
        lineItems: List<com.finance.splitup.data.ReceiptLineItem> = emptyList()
    ) {
        if (!category.equals(ExpenseCategories.INCOME, ignoreCase = true)) {
            val currentSpending = getSpendingForCategory(category)
            val limit = getBudgetForCategory(category)
            val convertedAmount = Currencies.convert(amount, currency, defaultCurrency)
            val projected = currentSpending + convertedAmount
            if (projected > limit) {
                val msg = "Warning: Adding '$title' (${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)}) exceeds your monthly $category budget limit (${Currencies.getSymbol(defaultCurrency)}${String.format(java.util.Locale.US, "%.0f", limit)})!"
                budgetWarningMessage = msg
                postSystemNotification("SplitUp Budget Exceeded", msg, CHANNEL_FINANCIAL_ALERTS)
            } else if (projected >= limit * 0.8) {
                val msg = "Alert: $category spending is at ${(projected / limit * 100).toInt()}% of monthly limit (${Currencies.getSymbol(defaultCurrency)}${String.format(java.util.Locale.US, "%.0f", limit)})."
                budgetWarningMessage = msg
                postSystemNotification("SplitUp Budget Alert", msg, CHANNEL_FINANCIAL_ALERTS)
            }
        }

        val eurAmount = Currencies.convertToEUR(amount, currency)
        if (eurAmount >= EXPENSE_ALERT_EUR_THRESHOLD) {
            if (category.equals(ExpenseCategories.INCOME, ignoreCase = true)) {
                postSystemNotification(
                    "High Income Recorded 💰",
                    "You logged income '$title' of ${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)}.",
                    CHANNEL_FINANCIAL_ALERTS
                )
            } else {
                postSystemNotification(
                    "High-Value Expense Logged 💸",
                    "You logged '$title' of ${Currencies.getSymbol(currency)}${String.format(java.util.Locale.US, "%.2f", amount)} under '$category'.",
                    CHANNEL_FINANCIAL_ALERTS
                )
            }
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
            receiptImagePath = receiptImagePath,
            lineItems = lineItems
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
        pendingMembers: List<String> = emptyList(),
        requireApproval: Boolean = true
    ): String {
        val selfName = currentUserName
        val selfEmail = currentUserEmail.lowercase()
        val cleanMembers = if (members.isEmpty()) listOf(selfName) else members
        val mappedMembers = cleanMembers.map { if (it == "You") selfName else it }
        val finalMembers = if (mappedMembers.contains(selfName)) mappedMembers else listOf(selfName) + mappedMembers

        val newGroup = Group(
            name = name.ifBlank { "New Group" },
            type = type.ifBlank { "Trip" },
            emoji = emoji,
            colorHex = colorHex,
            members = finalMembers.distinct(),
            pendingMembers = pendingMembers.filter { !isUserMatch(it, selfName, selfEmail) }.distinct(),
            memberEmails = memberEmails,
            expenses = emptyList(),
            createdBy = selfName,
            requireApproval = requireApproval
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

    fun isUserMatch(memberName: String, selfName: String, selfEmail: String): Boolean {
        if (memberName.isBlank()) return false
        val mLower = memberName.lowercase().trim()
        val sNameLower = selfName.lowercase().trim()
        val sEmailLower = selfEmail.lowercase().trim()

        if (mLower == "you") return true
        if (sNameLower.isNotBlank()) {
            if (mLower == sNameLower) return true
            if (mLower == sNameLower.substringBefore(" ")) return true
        }
        if (sEmailLower.isNotBlank()) {
            if (mLower == sEmailLower) return true
            if (mLower == sEmailLower.substringBefore("@") && (sNameLower.isBlank() || sNameLower.contains(mLower))) return true
        }
        return false
    }

    fun isMemberMatch(memberInGroup: String, targetName: String, targetEmail: String): Boolean {
        if (memberInGroup.isBlank()) return false
        val mLower = memberInGroup.lowercase().trim()
        val tNameLower = targetName.lowercase().trim()
        val tEmailLower = targetEmail.lowercase().trim()

        if (tNameLower.isNotBlank()) {
            if (mLower == tNameLower) return true
            if (mLower == tNameLower.substringBefore(" ")) return true
        }
        if (tEmailLower.isNotBlank()) {
            if (mLower == tEmailLower) return true
            if (mLower == tEmailLower.substringBefore("@") && (tNameLower.isBlank() || tNameLower.contains(mLower))) return true
        }
        return false
    }

    fun acceptGroupInvitation(groupId: String) {
        viewModelScope.launch {
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val selfName = currentUserName
            val selfEmail = currentUserEmail.lowercase()
            val userAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val currentUid = userAuth?.uid ?: ""

            val updatedMembers = if (!group.members.contains(selfName)) group.members + selfName else group.members
            val updatedPending = group.pendingMembers.filter { !isUserMatch(it, selfName, selfEmail) }
            val updatedUids = if (currentUid.isNotBlank() && !group.memberUids.contains(currentUid)) group.memberUids + currentUid else group.memberUids
            val updatedEmails = if (selfEmail.isNotBlank() && !group.memberEmails.map { it.lowercase() }.contains(selfEmail)) {
                group.memberEmails + selfEmail
            } else group.memberEmails

            val updatedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberUids = updatedUids, memberEmails = updatedEmails)
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
            val userAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val currentUid = userAuth?.uid ?: ""

            val updatedMembers = group.members.filter { !isUserMatch(it, selfName, selfEmail) }
            val updatedPending = group.pendingMembers.filter { !isUserMatch(it, selfName, selfEmail) }
            val updatedEmails = group.memberEmails.filter { e ->
                val eLower = e.lowercase().trim()
                eLower != selfEmail
            }
            val updatedUids = group.memberUids.filter { it != currentUid }
            val updatedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberEmails = updatedEmails, memberUids = updatedUids)

            logActivity(groupId, selfName, "MEMBER_DECLINED", "$selfName declined the invitation to join ${group.name}")
            postSystemNotification("Invitation Declined", "You declined the invitation to join '${group.name}'.", CHANNEL_GROUP_ACTIVITY)

            if (repository != null) {
                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                if (currentUid.isNotBlank()) {
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(currentUid).collection("groups").document(groupId).delete().await()
                    } catch (_: Exception) {}
                }
                repository.deleteGroup(groupId)
                triggerImmediateSync()
            } else {
                _groupsFallback.update { current ->
                    current.filter { it.id != groupId }
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
            } else {
                _groupsFallback.update { current -> current.map { if (it.id == groupId) updated else it } }
            }
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val groupMap = mapOf(
                    "id" to updated.id,
                    "name" to updated.name,
                    "type" to updated.type,
                    "members" to updated.members,
                    "pendingMembers" to updated.pendingMembers,
                    "memberUids" to updated.memberUids,
                    "memberEmails" to updated.memberEmails,
                    "expenses" to updated.expenses,
                    "createdBy" to updated.createdBy,
                    "updatedAt" to updated.updatedAt,
                    "emoji" to updated.emoji,
                    "colorHex" to updated.colorHex,
                    "isArchived" to updated.isArchived
                )
                val batch = db.batch()
                val globalRef = db.collection("groups").document(groupId)
                batch.set(globalRef, groupMap, com.google.firebase.firestore.SetOptions.merge())
                if (currentUser != null) {
                    val userGroupRef = db.collection("users").document(currentUser.uid).collection("groups").document(groupId)
                    batch.set(userGroupRef, groupMap, com.google.firebase.firestore.SetOptions.merge())
                }
                batch.commit().await()
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Failed to sync group archive state to cloud", e)
            }
            triggerImmediateSync()
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
            } else {
                _groupsFallback.update { current -> current.map { if (it.id == groupId) updated else it } }
            }
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val groupMap = mapOf(
                    "id" to updated.id,
                    "name" to updated.name,
                    "type" to updated.type,
                    "members" to updated.members,
                    "pendingMembers" to updated.pendingMembers,
                    "memberUids" to updated.memberUids,
                    "memberEmails" to updated.memberEmails,
                    "expenses" to updated.expenses,
                    "createdBy" to updated.createdBy,
                    "updatedAt" to updated.updatedAt,
                    "emoji" to updated.emoji,
                    "colorHex" to updated.colorHex,
                    "isArchived" to updated.isArchived
                )
                val batch = db.batch()
                val globalRef = db.collection("groups").document(groupId)
                batch.set(globalRef, groupMap, com.google.firebase.firestore.SetOptions.merge())
                if (currentUser != null) {
                    val userGroupRef = db.collection("users").document(currentUser.uid).collection("groups").document(groupId)
                    batch.set(userGroupRef, groupMap, com.google.firebase.firestore.SetOptions.merge())
                }
                batch.commit().await()
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Failed to sync group unarchive state to cloud", e)
            }
            triggerImmediateSync()
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

    fun addGroupMember(groupId: String, memberName: String, memberEmail: String = "", isPending: Boolean = true) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            if (memberName.isBlank()) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val selfName = currentUserName
            val selfEmail = currentUserEmail.lowercase()
            val resolvedName = if (memberName == "You") selfName else memberName

            // Prevent adding oneself or duplicate existing members
            val isSelf = isUserMatch(resolvedName, selfName, selfEmail) || (memberEmail.isNotBlank() && memberEmail.lowercase() == selfEmail)

            val isAlreadyMember = group.members.any { isMemberMatch(it, resolvedName, memberEmail) } ||
                    group.pendingMembers.any { isMemberMatch(it, resolvedName, memberEmail) } ||
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
                updatedPending = group.pendingMembers.filter { !isMemberMatch(it, resolvedName, memberEmail) }
            }

            val updatedEmails = if (memberEmail.isNotBlank() && !group.memberEmails.map { it.lowercase() }.contains(memberEmail.lowercase())) {
                group.memberEmails + memberEmail
            } else group.memberEmails

            if (updatedMembers != group.members || updatedPending != group.pendingMembers || updatedEmails != group.memberEmails) {
                val updatedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberEmails = updatedEmails)
                logActivity(groupId, selfName, "MEMBER_ADDED", "Invited member '$resolvedName'")
                if (isPending) {
                    postSystemNotification("✉️ Group Invitation Sent", "Invitation sent to $resolvedName for ${group.name}.", CHANNEL_GROUP_ACTIVITY)
                } else {
                    postSystemNotification("🎉 Group Member Joined", "$resolvedName has joined ${group.name}!", CHANNEL_GROUP_ACTIVITY)
                }
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
            
            val targetNameLower = memberName.lowercase().trim()
            val targetClean = targetNameLower.replace(" ", "").replace(".", "")
            
            val resolvedEmail = if (memberEmail.isNotBlank()) {
                memberEmail.lowercase().trim()
            } else {
                _registeredUsers.value.find { it.name.equals(memberName, ignoreCase = true) }?.email?.lowercase()?.trim() ?: ""
            }

            var removedUid = ""
            if (resolvedEmail.isNotBlank()) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val userSnap = db.collection("users").whereEqualTo("email", resolvedEmail).get().await()
                    removedUid = userSnap.documents.firstOrNull()?.id ?: ""
                } catch (_: Exception) {}
            }

            val updatedMembers = group.members.filter { !it.equals(memberName, ignoreCase = true) && (resolvedEmail.isBlank() || it.lowercase() != resolvedEmail) }
            val updatedPending = group.pendingMembers.filter { !it.equals(memberName, ignoreCase = true) && (resolvedEmail.isBlank() || it.lowercase() != resolvedEmail) }
            val updatedEmails = group.memberEmails.filter { email ->
                val emailLower = email.lowercase().trim()
                val emailPrefix = emailLower.substringBefore("@").replace(".", "")
                
                val matchesName = targetClean.isNotBlank() && (
                    emailLower == targetNameLower ||
                    emailPrefix == targetClean
                )
                val matchesEmail = resolvedEmail.isNotBlank() && emailLower == resolvedEmail

                !matchesName && !matchesEmail
            }
            val updatedUids = if (removedUid.isNotBlank()) group.memberUids.filter { it != removedUid } else group.memberUids

            val updatedGroup = group.copy(
                members = updatedMembers,
                pendingMembers = updatedPending,
                memberEmails = updatedEmails,
                memberUids = updatedUids
            )
            if (removedUid.isNotBlank()) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("users").document(removedUid).collection("groups").document(groupId).delete()
                } catch (_: Exception) {}
            }
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
                        val currentEmail = userAuth?.email?.lowercase() ?: ""
                        val currentUid = userAuth?.uid ?: ""

                        val updatedMembers = if (group.members.any { isUserMatch(it, currentUserName, currentEmail) }) group.members else group.members + currentUserName
                        val updatedPending = group.pendingMembers.filter { !isUserMatch(it, currentUserName, currentEmail) }
                        val updatedUids = if (currentUid.isNotBlank() && !group.memberUids.contains(currentUid)) group.memberUids + currentUid else group.memberUids
                        val updatedEmails = if (currentEmail.isNotBlank() && !group.memberEmails.map { it.lowercase() }.contains(currentEmail)) group.memberEmails + currentEmail else group.memberEmails

                        val joinedGroup = group.copy(members = updatedMembers, pendingMembers = updatedPending, memberUids = updatedUids, memberEmails = updatedEmails)

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
        receiptImagePath: String? = null,
        lineItems: List<com.finance.splitup.data.ReceiptLineItem> = emptyList()
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
                receiptImagePath = receiptImagePath,
                lineItems = lineItems
            )
            com.finance.splitup.data.CategoryDetector.learnCategory(actualTitle, category, getApplication())
            val updatedGroup = group.copy(
                expenses = listOf(expense) + group.expenses,
                updatedAt = System.currentTimeMillis()
            )
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
            val deletedExpense = group.expenses.find { it.id == expenseId } ?: return@launch

            val requiresApproval = group.requireApproval && !isGroupOwner(group)

            if (requiresApproval) {
                val ownerName = group.createdBy.ifBlank { group.members.firstOrNull() ?: "Owner" }
                val ownerEmail = group.memberEmails.find { e ->
                    e.isNotBlank() && !isUserMatch(e, currentUserName, currentUserEmail)
                } ?: group.memberEmails.firstOrNull() ?: ownerName
                createApprovalRequest(
                    group = group,
                    targetId = expenseId,
                    requestType = "EXPENSE_DELETE",
                    ownerName = ownerName,
                    ownerEmail = ownerEmail,
                    payloadJson = expenseId
                )
            } else {
                val updatedGroup = group.copy(
                    expenses = group.expenses.filter { it.id != expenseId },
                    updatedAt = System.currentTimeMillis()
                )
                logActivity(groupId, "You", "EXPENSE_DELETED", "Deleted expense '${deletedExpense.title}'")
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
    }

    fun updateGroupExpense(groupId: String, expense: GroupExpense) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val normalizedExpense = expense.copy(
                date = com.finance.splitup.data.DateUtils.normalizeExpenseDate(expense.date)
            )

            val requiresApproval = group.requireApproval && !isGroupOwner(group)

            if (requiresApproval) {
                val ownerName = group.createdBy.ifBlank { group.members.firstOrNull() ?: "Owner" }
                val ownerEmail = group.memberEmails.find { e ->
                    e.isNotBlank() && !isUserMatch(e, currentUserName, currentUserEmail)
                } ?: group.memberEmails.firstOrNull() ?: ownerName
                val payload = kotlinx.serialization.json.Json.encodeToString(GroupExpense.serializer(), normalizedExpense)
                createApprovalRequest(
                    group = group,
                    targetId = normalizedExpense.id,
                    requestType = "EXPENSE_EDIT",
                    ownerName = ownerName,
                    ownerEmail = ownerEmail,
                    payloadJson = payload
                )
            } else {
                val updatedExpenses = group.expenses.map { if (it.id == normalizedExpense.id) normalizedExpense else it }
                val updatedGroup = group.copy(
                    expenses = updatedExpenses,
                    updatedAt = System.currentTimeMillis()
                )
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
    }

    fun updateGroup(groupId: String, name: String, type: String, members: List<String>, emoji: String = "✈️", colorHex: String = "#3B82F6", requireApproval: Boolean = true) {
        viewModelScope.launch {
            if (!verifyGroupActiveOrPurge(groupId)) return@launch
            val group = groups.value.find { it.id == groupId } ?: repository?.getGroupById(groupId) ?: return@launch
            val updatedGroup = group.copy(
                name = name.ifBlank { group.name },
                type = type,
                emoji = emoji.ifBlank { group.emoji },
                colorHex = colorHex.ifBlank { group.colorHex },
                members = members.ifEmpty { group.members },
                requireApproval = requireApproval
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

    fun isExpenseOwner(expense: com.finance.splitup.data.GroupExpense, group: Group): Boolean {
        val selfName = currentUserName
        val selfEmail = currentUserEmail.lowercase()
        if (expense.paidBy.isBlank()) return true
        if (isUserMatch(expense.paidBy, selfName, selfEmail)) return true
        if (group.createdBy.isNotBlank() && isUserMatch(group.createdBy, selfName, selfEmail)) return true
        return false
    }

    fun isGroupOwner(group: Group): Boolean {
        val selfName = currentUserName
        val selfEmail = currentUserEmail.lowercase()
        if (group.createdBy.isBlank() || group.createdBy.equals("You", ignoreCase = true)) return true
        return isUserMatch(group.createdBy, selfName, selfEmail)
    }

    fun recordSettlementPayment(
        group: Group,
        payer: String,
        payee: String,
        amount: Double,
        currency: String = Currencies.EUR,
        onResult: (isPendingApproval: Boolean) -> Unit
    ) {
        val actualPayer = payer.ifBlank { "You" }
        val actualPayee = payee.ifBlank { "You" }
        val selfName = currentUserName
        val isSelfPayee = actualPayee.equals(selfName, ignoreCase = true) || actualPayee.equals("You", ignoreCase = true)

        val requiresApproval = group.requireApproval && !isSelfPayee

        val settlementExpense = GroupExpense(
            title = "Payment: $actualPayer to $actualPayee",
            amount = amount,
            paidBy = actualPayer,
            date = "Today",
            splitAmong = listOf(actualPayee),
            currency = currency,
            splitMode = "EQUAL",
            memberSplits = emptyMap(),
            category = ExpenseCategories.SETTLEMENT
        )

        if (requiresApproval) {
            val payload = kotlinx.serialization.json.Json.encodeToString(GroupExpense.serializer(), settlementExpense)
            val payeeEmail = group.memberEmails.find { email ->
                val idx = group.members.indexOfFirst { it.equals(actualPayee, ignoreCase = true) }
                idx != -1 && group.memberEmails.getOrNull(idx) == email
            } ?: actualPayee

            createApprovalRequest(
                group = group,
                targetId = settlementExpense.id,
                requestType = "SETTLEMENT_RECORD",
                ownerName = actualPayee,
                ownerEmail = payeeEmail,
                payloadJson = payload
            )
            onResult(true)
        } else {
            addGroupExpense(
                groupId = group.id,
                title = settlementExpense.title,
                amount = settlementExpense.amount,
                paidBy = settlementExpense.paidBy,
                splitAmong = settlementExpense.splitAmong,
                date = settlementExpense.date,
                currency = settlementExpense.currency,
                splitMode = settlementExpense.splitMode,
                memberSplits = settlementExpense.memberSplits,
                category = settlementExpense.category
            )
            onResult(false)
        }
    }

    fun createApprovalRequest(
        group: Group,
        targetId: String,
        requestType: String,
        ownerName: String,
        ownerEmail: String,
        payloadJson: String
    ) {
        val req = com.finance.splitup.data.db.GroupApprovalRequestEntity(
            groupId = group.id,
            groupName = group.name,
            targetId = targetId,
            requestType = requestType,
            requestedBy = currentUserName,
            requestedByEmail = currentUserEmail,
            ownerName = ownerName,
            ownerEmail = ownerEmail.ifBlank { ownerName },
            payloadJson = payloadJson,
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            if (repository != null) {
                repository.upsertApprovalRequest(req)
            }
            com.finance.splitup.data.CloudSyncManager.syncApprovalRequestToCloud(req)
            com.finance.splitup.data.CloudSyncManager.sendGroupActivityNotification(
                group = group,
                title = "Approval Needed ✋ • ${group.name}",
                body = "${currentUserName} requested to ${if (requestType == "EXPENSE_EDIT") "edit an expense" else if (requestType == "EXPENSE_DELETE") "delete an expense" else "record a settlement"} in '${group.name}'",
                requestId = req.id,
                targetEmail = ownerEmail,
                targetName = ownerName,
                requestType = requestType,
                targetId = targetId,
                payloadJson = payloadJson
            )
            val msgTitle = if (requestType == "SETTLEMENT_RECORD") "Settlement Approval Requested ✉️" else "Approval Request Sent ✉️"
            val msgBody = when (requestType) {
                "SETTLEMENT_RECORD" -> "Your recorded settlement payment in '${group.name}' was sent to $ownerName for approval."
                "EXPENSE_EDIT" -> "Your edited expense in '${group.name}' was sent to $ownerName for approval."
                "EXPENSE_DELETE" -> "Your request to delete expense in '${group.name}' was sent to $ownerName for approval."
                else -> "Your request in '${group.name}' was sent to $ownerName for approval."
            }
            postSystemNotification(msgTitle, msgBody, CHANNEL_GROUP_ACTIVITY)
        }
    }

    fun approveGroupRequest(request: com.finance.splitup.data.db.GroupApprovalRequestEntity) {
        viewModelScope.launch {
            when (request.requestType) {
                "SETTLEMENT_RECORD" -> {
                    try {
                        val settlementExpense = kotlinx.serialization.json.Json.decodeFromString(GroupExpense.serializer(), request.payloadJson)
                        val group = groups.value.find { it.id == request.groupId } ?: repository?.getGroupById(request.groupId)
                        if (group != null) {
                            val updatedExpenses = listOf(settlementExpense) + group.expenses.filter { it.id != settlementExpense.id }
                            val updatedGroup = group.copy(
                                expenses = updatedExpenses,
                                updatedAt = System.currentTimeMillis()
                            )
                            if (repository != null) {
                                repository.insertGroup(updatedGroup)
                                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                            } else {
                                _groupsFallback.update { current ->
                                    current.map { if (it.id == group.id) updatedGroup else it }
                                }
                            }
                            val symbol = Currencies.getSymbol(settlementExpense.currency)
                            val amtFormatted = String.format(java.util.Locale.US, "%.2f", settlementExpense.amount)
                            logActivity(group.id, request.ownerName, "EXPENSE_ADDED", "Approved settlement of $symbol$amtFormatted from ${request.requestedBy}", referenceId = settlementExpense.id)
                        }
                    } catch (_: Exception) {}
                }
                "EXPENSE_DELETE" -> {
                    val group = groups.value.find { it.id == request.groupId }
                    if (group != null) {
                        val updatedGroup = group.copy(
                            expenses = group.expenses.filter { it.id != request.targetId },
                            updatedAt = System.currentTimeMillis()
                        )
                        if (repository != null) {
                            repository.insertGroup(updatedGroup)
                            com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                        } else {
                            _groupsFallback.update { current ->
                                current.map { if (it.id == group.id) updatedGroup else it }
                            }
                        }
                        com.finance.splitup.data.CloudSyncManager.deleteGroupExpenseFromCloud(request.groupId, request.targetId)
                    }
                }
                "EXPENSE_EDIT", "EXPENSE_ADD" -> {
                    try {
                        val editedExpense = kotlinx.serialization.json.Json.decodeFromString(GroupExpense.serializer(), request.payloadJson)
                        val group = groups.value.find { it.id == request.groupId }
                        if (group != null) {
                            val updatedExpenses = if (group.expenses.any { it.id == editedExpense.id }) {
                                group.expenses.map { if (it.id == editedExpense.id) editedExpense else it }
                            } else {
                                listOf(editedExpense) + group.expenses
                            }
                            val updatedGroup = group.copy(
                                expenses = updatedExpenses,
                                updatedAt = System.currentTimeMillis()
                            )
                            if (repository != null) {
                                repository.insertGroup(updatedGroup)
                                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                            } else {
                                _groupsFallback.update { current ->
                                    current.map { if (it.id == group.id) updatedGroup else it }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                "MEMBER_REMOVE" -> {
                    val group = groups.value.find { it.id == request.groupId }
                    if (group != null) {
                        val updatedMembers = group.members.filter { !it.equals(request.targetId, ignoreCase = true) }
                        val updatedPending = group.pendingMembers.filter { !it.equals(request.targetId, ignoreCase = true) }
                        val updatedGroup = group.copy(
                            members = updatedMembers,
                            pendingMembers = updatedPending,
                            updatedAt = System.currentTimeMillis()
                        )
                        if (repository != null) {
                            repository.insertGroup(updatedGroup)
                            com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                        } else {
                            _groupsFallback.update { current ->
                                current.map { if (it.id == group.id) updatedGroup else it }
                            }
                        }
                    }
                }
            }
            if (repository != null) {
                repository.updateApprovalRequestStatus(request.id, "APPROVED")
            }
            _pendingApprovalRequestsFallback.update { current -> current.filter { it.id != request.id } }
            com.finance.splitup.data.CloudSyncManager.updateApprovalRequestStatusInCloud(request.id, "APPROVED")
            val notifTitle = if (request.requestType == "SETTLEMENT_RECORD") "Settlement Approved! 🎉" else "Request Approved! ✅"
            val notifMsg = if (request.requestType == "SETTLEMENT_RECORD") "Approved settlement payment from ${request.requestedBy} in '${request.groupName}'" else "Approved ${request.requestedBy}'s request in '${request.groupName}'"
            postSystemNotification(notifTitle, notifMsg, CHANNEL_GROUP_ACTIVITY, groupId = request.groupId)
            val group = groups.value.find { it.id == request.groupId }
            if (group != null) {
                com.finance.splitup.data.CloudSyncManager.sendGroupActivityNotification(
                    group,
                    "Request Approved! ✅",
                    "${request.ownerName} approved ${request.requestedBy}'s ${request.requestType} request in '${request.groupName}'."
                )
            }
        }
    }

    fun rejectGroupRequest(request: com.finance.splitup.data.db.GroupApprovalRequestEntity) {
        viewModelScope.launch {
            if (repository != null) {
                repository.updateApprovalRequestStatus(request.id, "REJECTED")
            }
            _pendingApprovalRequestsFallback.update { current -> current.filter { it.id != request.id } }
            com.finance.splitup.data.CloudSyncManager.updateApprovalRequestStatusInCloud(request.id, "REJECTED")
            val notifTitle = if (request.requestType == "SETTLEMENT_RECORD") "Settlement Declined ❌" else "Request Declined ❌"
            val notifMsg = if (request.requestType == "SETTLEMENT_RECORD") "Declined settlement payment from ${request.requestedBy} in '${request.groupName}'" else "Declined ${request.requestedBy}'s request in '${request.groupName}'"
            postSystemNotification(notifTitle, notifMsg, CHANNEL_GROUP_ACTIVITY, groupId = request.groupId)
            val group = groups.value.find { it.id == request.groupId }
            if (group != null) {
                com.finance.splitup.data.CloudSyncManager.sendGroupActivityNotification(
                    group,
                    "Request Declined ❌",
                    "${request.ownerName} declined ${request.requestedBy}'s ${request.requestType} request in '${request.groupName}'."
                )
            }
        }
    }

    fun approveGroupRequestById(requestId: String) {
        viewModelScope.launch {
            val req = pendingApprovalRequests.value.find { it.id == requestId }
            if (req != null) {
                approveGroupRequest(req)
            }
        }
    }

    fun rejectGroupRequestById(requestId: String) {
        viewModelScope.launch {
            val req = pendingApprovalRequests.value.find { it.id == requestId }
            if (req != null) {
                rejectGroupRequest(req)
            }
        }
    }

    fun sendSampleApprovalRequest(groupId: String? = null, requestType: String = "EXPENSE_EDIT") {
        viewModelScope.launch {
            var targetGroup = groups.value.find { it.id == groupId } ?: groups.value.firstOrNull()
            if (targetGroup == null) {
                val newGroup = Group(
                    name = "Sample Trip",
                    type = "Trip",
                    members = listOf("You", "Alice", "Bob"),
                    requireApproval = true
                )
                if (repository != null) {
                    repository.insertGroup(newGroup)
                }
                _groupsFallback.update { listOf(newGroup) + it }
                targetGroup = newGroup
            }

            val sampleExpense = targetGroup.expenses.firstOrNull() ?: GroupExpense(
                title = "Dinner & Drinks",
                amount = 90.0,
                paidBy = "You",
                date = "Today",
                splitAmong = listOf("You", "Alice", "Bob"),
                currency = Currencies.EUR
            )

            val editedExpense = sampleExpense.copy(
                title = "${sampleExpense.title} (Edited)",
                amount = sampleExpense.amount + 30.0
            )
            val payloadJson = kotlinx.serialization.json.Json.encodeToString(GroupExpense.serializer(), editedExpense)

            val req = com.finance.splitup.data.db.GroupApprovalRequestEntity(
                id = "sample_req_${System.currentTimeMillis()}",
                groupId = targetGroup.id,
                groupName = targetGroup.name,
                targetId = sampleExpense.id,
                requestType = requestType,
                requestedBy = "Alice",
                requestedByEmail = "alice@example.com",
                ownerName = currentUserName,
                ownerEmail = currentUserEmail.ifBlank { "You" },
                payloadJson = payloadJson,
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )

            if (repository != null) {
                try {
                    repository.upsertApprovalRequest(req)
                } catch (_: Exception) {}
            }
            _pendingApprovalRequestsFallback.update { current -> listOf(req) + current.filter { it.id != req.id } }
            com.finance.splitup.data.CloudSyncManager.syncApprovalRequestToCloud(req)
            postSystemNotification(
                "Approval Needed ✋ • ${targetGroup.name}",
                "Alice requested to edit expense '${sampleExpense.title}' to €${String.format(java.util.Locale.US, "%.2f", editedExpense.amount)}",
                CHANNEL_GROUP_ACTIVITY,
                groupId = targetGroup.id,
                requestId = req.id
            )
        }
    }

    fun undoActivityLog(log: com.finance.splitup.data.db.ActivityLogEntity) {
        viewModelScope.launch {
            try {
                when (log.actionType) {
                    "PERSONAL_EXPENSE_ADDED" -> {
                        deleteExpense(log.referenceId)
                    }
                    "PERSONAL_EXPENSE_DELETED" -> {
                        if (log.payloadJson.isNotBlank()) {
                            val exp = kotlinx.serialization.json.Json.decodeFromString(Expense.serializer(), log.payloadJson)
                            addExpense(exp.title, exp.amount, exp.category, exp.date, exp.paidBy, exp.currency, exp.description, exp.paymentMethod, null, exp.lineItems)
                        }
                    }
                    "PERSONAL_INCOME_DELETED" -> {
                        if (log.payloadJson.isNotBlank()) {
                            val inc = kotlinx.serialization.json.Json.decodeFromString(com.finance.splitup.data.db.IncomeEntity.serializer(), log.payloadJson)
                            addIncome(inc.title, inc.amount, inc.source, inc.currency, inc.date)
                        }
                    }
                    "GROUP_EXPENSE_DELETED", "EXPENSE_DELETED" -> {
                        if (log.payloadJson.isNotBlank() && log.groupId.isNotBlank()) {
                            val deletedExp = kotlinx.serialization.json.Json.decodeFromString(GroupExpense.serializer(), log.payloadJson)
                            addGroupExpense(log.groupId, deletedExp.title, deletedExp.amount, deletedExp.paidBy, deletedExp.splitAmong, deletedExp.date, deletedExp.currency, deletedExp.splitMode, deletedExp.memberSplits, deletedExp.category, null, deletedExp.lineItems)
                        }
                    }
                    "GROUP_EXPENSE_UPDATED", "EXPENSE_UPDATED" -> {
                        if (log.payloadJson.isNotBlank() && log.groupId.isNotBlank()) {
                            val oldExp = kotlinx.serialization.json.Json.decodeFromString(GroupExpense.serializer(), log.payloadJson)
                            val group = groups.value.find { it.id == log.groupId }
                            if (group != null) {
                                val restoredExpenses = group.expenses.map { if (it.id == oldExp.id) oldExp else it }
                                val updatedGroup = group.copy(expenses = restoredExpenses)
                                repository?.insertGroup(updatedGroup)
                                com.finance.splitup.data.CloudSyncManager.syncGroupToCloud(updatedGroup, repository, getApplication())
                            }
                        }
                    }
                }
                if (repository != null) {
                    repository.markActivityLogUndone(log.id)
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        // Large-amount alert thresholds (EUR-equivalent), centralized so the
        // income/expense/investment notification policies stay consistent.
        const val INCOME_ALERT_EUR_THRESHOLD = 200.0
        const val EXPENSE_ALERT_EUR_THRESHOLD = 200.0
        const val INVESTMENT_ALERT_EUR_THRESHOLD = 500.0

        const val CHANNEL_GROUP_ACTIVITY = "splitup_group_activity"
        const val CHANNEL_FINANCIAL_ALERTS = "splitup_financial_alerts"
        const val CHANNEL_RECURRING_BILLS = "splitup_recurring_bills"
        const val CHANNEL_PAYMENT_DETECTION = "splitup_payment_detection"
        const val CHANNEL_EXPORTS_INSIGHTS = "splitup_exports_insights"
        const val CHANNEL_SECURITY_ALERTS = "splitup_security_alerts"
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun postSystemNotification(
        title: String,
        message: String,
        channelId: String = CHANNEL_FINANCIAL_ALERTS,
        targetScreen: String? = null,
        groupId: String? = null,
        category: String? = null,
        pendingIntent: android.app.PendingIntent? = null,
        requestId: String? = null
    ) {
        val app = getApplication<Application>()
        if (app is DummyApplication) return
        com.finance.splitup.data.NotificationHelper.post(
            context = app,
            title = title,
            message = message,
            channelId = channelId,
            targetScreen = targetScreen,
            groupId = groupId,
            category = category,
            pendingIntent = pendingIntent,
            requestId = requestId
        )
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
