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

    fun calculateUserNetBalance(
        group: Group,
        currentUserName: String = "You",
        targetCurrency: String = Currencies.EUR,
        currentUserEmail: String = ""
    ): Double {
        val balances = settlementsUseCase.calculateNetBalances(group, targetCurrency, currentUserName, currentUserEmail)
        val canonicalSelf = group.allMembers.firstOrNull { it == "You" || it.equals("You", ignoreCase = true) }
            ?: group.allMembers.firstOrNull { currentUserName.isNotBlank() && it.equals(currentUserName, ignoreCase = true) }
            ?: group.allMembers.firstOrNull { member ->
                if (currentUserEmail.isNotBlank()) {
                    val idx = group.members.indexOfFirst { it.equals(member, ignoreCase = true) }
                    if (idx >= 0) {
                        val email = group.memberEmails.getOrNull(idx)
                        email != null && email.equals(currentUserEmail, ignoreCase = true)
                    } else false
                } else false
            }
            ?: "You"
        return balances[canonicalSelf] ?: balances["You"] ?: 0.0
    }
}
