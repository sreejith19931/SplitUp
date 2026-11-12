package com.finance.splitup.domain

import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import kotlin.math.abs

class CalculateGroupMetricsUseCase {

    private val settlementsUseCase = CalculateGroupSettlementsUseCase()

    fun calculateTotalGroupSpent(group: Group, targetCurrency: String = Currencies.EUR): Double {
        val groupCurrency = group.expenses.firstOrNull()?.currency ?: targetCurrency
        return group.expenses
            .filter { it.category != ExpenseCategories.SETTLEMENT && !it.title.contains("Settlement", ignoreCase = true) }
            .sumOf {
                val amt = Currencies.convert(it.amount, it.currency, groupCurrency)
                if (it.category == ExpenseCategories.INCOME || it.amount < 0) -abs(amt) else abs(amt)
            }
    }

    fun calculateUserNetBalance(group: Group, currentUserName: String = "You", targetCurrency: String = Currencies.EUR): Double {
        val balances = settlementsUseCase.calculateNetBalances(group, targetCurrency, currentUserName)
        val canonicalSelf = group.members.firstOrNull { it == "You" || it.equals("You", ignoreCase = true) }
            ?: group.members.firstOrNull { currentUserName.isNotBlank() && it.equals(currentUserName, ignoreCase = true) }
            ?: "You"
        return balances[canonicalSelf] ?: balances["You"] ?: 0.0
    }
}
