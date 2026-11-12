package com.finance.splitup.domain

import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import com.finance.splitup.data.Settlement
import kotlin.math.abs
import kotlin.math.round

data class SettlementResult(
    val netBalances: Map<String, Double>,
    val settlements: List<Settlement>
)

class CalculateGroupSettlementsUseCase {

    fun calculateNetBalances(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: Currencies.EUR,
        currentUserName: String = "You"
    ): Map<String, Double> {
        val canonicalSelf = group.members.firstOrNull { it == "You" || it.equals("You", ignoreCase = true) }
            ?: group.members.firstOrNull { currentUserName.isNotBlank() && it.equals(currentUserName, ignoreCase = true) }
            ?: "You"

        fun matchMember(name: String): String {
            val trimmed = name.trim()
            if (trimmed == "You" || trimmed.equals("You", ignoreCase = true) || (currentUserName.isNotBlank() && trimmed.equals(currentUserName, ignoreCase = true))) {
                return canonicalSelf
            }
            return group.members.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
        }

        val distinctMembers = group.members.map { matchMember(it) }.distinct()
        val balances = distinctMembers.associateWith { 0.0 }.toMutableMap()

        for (expense in group.expenses) {
            val payerName = expense.paidBy.trim()
            val matchedPayer = matchMember(payerName)
            val isIncome = expense.category == ExpenseCategories.INCOME || expense.amount < 0
            val convertedAmount = Currencies.convert(abs(expense.amount), expense.currency, targetCurrency)

            if (isIncome) {
                balances[matchedPayer] = (balances[matchedPayer] ?: 0.0) - convertedAmount

                if (expense.splitMode == "CUSTOM" && expense.memberSplits.isNotEmpty()) {
                    for ((member, shareAmount) in expense.memberSplits) {
                        val matchedMember = matchMember(member)
                        val convertedShare = Currencies.convert(abs(shareAmount), expense.currency, targetCurrency)
                        balances[matchedMember] = (balances[matchedMember] ?: 0.0) + convertedShare
                    }
                } else {
                    val cleanSplitAmong = expense.splitAmong.map { matchMember(it) }.distinct()
                    val splitCount = cleanSplitAmong.size
                    if (splitCount > 0) {
                        val rawShare = convertedAmount / splitCount
                        val roundedShare = round(rawShare * 100) / 100.0
                        val remainder = round((convertedAmount - (roundedShare * splitCount)) * 100) / 100.0

                        cleanSplitAmong.forEachIndexed { index, matchedMember ->
                            val finalShare = if (index == 0) roundedShare + remainder else roundedShare
                            balances[matchedMember] = (balances[matchedMember] ?: 0.0) + finalShare
                        }
                    }
                }
            } else {
                balances[matchedPayer] = (balances[matchedPayer] ?: 0.0) + convertedAmount

                if (expense.splitMode == "CUSTOM" && expense.memberSplits.isNotEmpty()) {
                    for ((member, shareAmount) in expense.memberSplits) {
                        val matchedMember = matchMember(member)
                        val convertedShare = Currencies.convert(abs(shareAmount), expense.currency, targetCurrency)
                        balances[matchedMember] = (balances[matchedMember] ?: 0.0) - convertedShare
                    }
                } else {
                    val cleanSplitAmong = expense.splitAmong.map { matchMember(it) }.distinct()
                    val splitCount = cleanSplitAmong.size
                    if (splitCount > 0) {
                        val rawShare = convertedAmount / splitCount
                        val roundedShare = round(rawShare * 100) / 100.0
                        val remainder = round((convertedAmount - (roundedShare * splitCount)) * 100) / 100.0

                        cleanSplitAmong.forEachIndexed { index, matchedMember ->
                            val finalShare = if (index == 0) roundedShare + remainder else roundedShare
                            balances[matchedMember] = (balances[matchedMember] ?: 0.0) - finalShare
                        }
                    }
                }
            }
        }

        val roundedBalances = balances.mapValues { round(it.value * 100) / 100.0 }.toMutableMap()
        val totalResidual = round(roundedBalances.values.sum() * 100) / 100.0
        if (abs(totalResidual) in 0.009..0.05 && roundedBalances.isNotEmpty()) {
            val maxEntry = roundedBalances.maxByOrNull { abs(it.value) }
            if (maxEntry != null) {
                roundedBalances[maxEntry.key] = round((maxEntry.value - totalResidual) * 100) / 100.0
            }
        }

        return roundedBalances
    }

    fun calculateSettlements(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: Currencies.EUR,
        currentUserName: String = "You"
    ): List<Settlement> {
        val netBalances = calculateNetBalances(group, targetCurrency, currentUserName)
        val debtors = netBalances.filter { it.value < -0.01 }.mapValues { -it.value }.toMutableMap()
        val creditors = netBalances.filter { it.value > 0.01 }.toMutableMap()
        val settlements = mutableListOf<Settlement>()

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val maxDebtor = debtors.maxByOrNull { it.value } ?: break
            val maxCreditor = creditors.maxByOrNull { it.value } ?: break

            val debtor = maxDebtor.key
            val debtorAmount = maxDebtor.value

            val creditor = maxCreditor.key
            val creditorAmount = maxCreditor.value

            val settleAmount = round(minOf(debtorAmount, creditorAmount) * 100) / 100.0
            if (settleAmount > 0.009) {
                settlements.add(Settlement(from = debtor, to = creditor, amount = settleAmount))
            }

            val newDebtorBal = round((debtorAmount - settleAmount) * 100) / 100.0
            if (newDebtorBal < 0.01) {
                debtors.remove(debtor)
            } else {
                debtors[debtor] = newDebtorBal
            }

            val newCreditorBal = round((creditorAmount - settleAmount) * 100) / 100.0
            if (newCreditorBal < 0.01) {
                creditors.remove(creditor)
            } else {
                creditors[creditor] = newCreditorBal
            }
        }

        val combinedMap = mutableMapOf<Pair<String, String>, Double>()
        for (s in settlements) {
            val key = Pair(s.from, s.to)
            combinedMap[key] = (combinedMap[key] ?: 0.0) + s.amount
        }
        return combinedMap.map { (key, amount) ->
            Settlement(from = key.first, to = key.second, amount = round(amount * 100) / 100.0)
        }.filter { it.amount > 0.009 }
    }

    fun execute(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: Currencies.EUR,
        currentUserName: String = "You"
    ): SettlementResult {
        val balances = calculateNetBalances(group, targetCurrency, currentUserName)
        val settlements = calculateSettlements(group, targetCurrency, currentUserName)
        return SettlementResult(netBalances = balances, settlements = settlements)
    }
}
