package com.finance.splitup.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute : NavKey

@Serializable
data class PersonalExpensesRoute(val initialCategory: String? = null) : NavKey

@Serializable
data object GroupsRoute : NavKey

@Serializable
data object ReceiptScannerRoute : NavKey

@Serializable
data object ReceiptExtractionRoute : NavKey

@Serializable
data object ReceiptAllocationRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AboutRoute : NavKey

@Serializable
data object SecuritySettingsRoute : NavKey

@Serializable
data object DataManagementRoute : NavKey

@Serializable
data object EditProfileRoute : NavKey

@Serializable
data class AddExpenseRoute(
    val expenseId: String? = null,
    val initialAmount: Double? = null,
    val initialTitle: String? = null,
    val initialCategory: String? = null,
    val initialIsIncome: Boolean = false
) : NavKey

@Serializable
data class GroupDetailRoute(val groupId: String) : NavKey

@Serializable
data class AddGroupExpenseRoute(val groupId: String, val expenseId: String? = null) : NavKey

@Serializable
data object CreateGroupRoute : NavKey

@Serializable
data object FindFriendsRoute : NavKey

@Serializable
data object GlobalSearchRoute : NavKey

@Serializable
data object SubscriptionsRoute : NavKey

@Serializable
data object InsightsRoute : NavKey


