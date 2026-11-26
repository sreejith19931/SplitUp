package com.finance.splitup.ui.state

import androidx.compose.runtime.Immutable
import com.finance.splitup.data.Expense
import com.finance.splitup.data.Group

@Immutable
data class CategorySpend(
    val category: String,
    val amount: Double,
    val percentage: Double
)

@Immutable
data class DashboardUiData(
    val selectedDateRange: String = "This Month",
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalInvestments: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val netGroupOwed: Double = 0.0,
    val netGroupOwes: Double = 0.0,
    val totalGroupBalance: Double = 0.0,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val activeSubscriptionsCount: Int = 0,
    val exceededBudgetsCount: Int = 0,
    val defaultCurrency: String = "EUR (€)",
    val filteredExpenses: List<Expense> = emptyList(),
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false
)

@Immutable
sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val data: DashboardUiData
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}
