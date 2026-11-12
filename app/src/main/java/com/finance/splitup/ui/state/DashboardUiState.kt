package com.finance.splitup.ui.state

import androidx.compose.runtime.Immutable
import com.finance.splitup.data.Expense
import com.finance.splitup.data.Group

@Immutable
sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val expenses: List<Expense>,
        val groups: List<Group>,
        val totalPersonalSpent: Double,
        val totalGroupSpent: Double,
        val totalNetOwed: Double,
        val defaultCurrency: String,
        val notificationCount: Int
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}
