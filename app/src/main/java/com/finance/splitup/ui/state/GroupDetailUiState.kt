package com.finance.splitup.ui.state

import androidx.compose.runtime.Immutable
import com.finance.splitup.data.Group
import com.finance.splitup.data.Settlement

@Immutable
sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState

    data class Success(
        val group: Group,
        val netBalances: Map<String, Double>,
        val settlements: List<Settlement>,
        val totalSpent: Double,
        val userNetBalance: Double,
        val currencySymbol: String
    ) : GroupDetailUiState

    data class Error(val message: String) : GroupDetailUiState
}
