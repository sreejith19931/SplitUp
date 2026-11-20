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
        val canonicalSelf = group.allMembers.firstOrNull { member ->
            val mTrim = member.trim()
            if (currentUserEmail.isNotBlank()) {
                val emailLower = currentUserEmail.trim().lowercase()
                if (mTrim.equals(emailLower, ignoreCase = true)) return@firstOrNull true
                val memberIdx = group.members.indexOfFirst { it.equals(mTrim, ignoreCase = true) }
                if (memberIdx >= 0) {
                    val memberEmail = group.memberEmails.getOrNull(memberIdx)
                    if (memberEmail != null && memberEmail.equals(currentUserEmail, ignoreCase = true)) {
                        return@firstOrNull true
                    }
                }
            }
            if (currentUserName.isNotBlank() && !currentUserName.equals("You", ignoreCase = true)) {
                if (mTrim.equals(currentUserName.trim(), ignoreCase = true)) return@firstOrNull true
            }
            false
        } ?: group.allMembers.firstOrNull { it.trim().equals("You", ignoreCase = true) } ?: "You"
        return balances[canonicalSelf] ?: balances["You"] ?: 0.0
    }
}
