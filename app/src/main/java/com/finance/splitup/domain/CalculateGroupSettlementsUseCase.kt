package com.finance.splitup.domain

import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import com.finance.splitup.data.Settlement
import kotlin.math.abs

data class SettlementResult(
    val netBalances: Map<String, Double>,
    val settlements: List<Settlement>
)

class CalculateGroupSettlementsUseCase {

    fun calculateNetBalances(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: Currencies.EUR,
        currentUserName: String = "You",
        currentUserEmail: String = ""
    ): Map<String, Double> {
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

        fun isSelfUser(n: String): Boolean {
            val trimmed = n.trim()
            if (trimmed.equals(canonicalSelf, ignoreCase = true)) return true
            if (trimmed.equals("You", ignoreCase = true)) return true
            if (currentUserName.isNotBlank() && trimmed.equals(currentUserName.trim(), ignoreCase = true)) return true
            if (currentUserEmail.isNotBlank()) {
                val emailLower = currentUserEmail.trim().lowercase()
                if (trimmed.equals(emailLower, ignoreCase = true)) return true
                val memberIdx = group.members.indexOfFirst { it.equals(n, ignoreCase = true) }
                if (memberIdx >= 0) {
                    val memberEmail = group.memberEmails.getOrNull(memberIdx)
                    if (memberEmail != null && memberEmail.equals(currentUserEmail, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        fun matchMember(name: String): String {
            if (isSelfUser(name)) {
                return canonicalSelf
            }
            return group.allMembers.firstOrNull { it.equals(name.trim(), ignoreCase = true) } ?: name.trim()
        }

        val distinctMembers = group.allMembers.map { matchMember(it) }.distinct()
        val balances = distinctMembers.associateWith { 0.0 }.toMutableMap()

        for (expense in group.expenses) {
            val payerName = expense.paidBy.trim()
            val matchedPayer = matchMember(payerName)
            val isIncome = expense.category == ExpenseCategories.INCOME || expense.amount < 0
            val convertedAmount = Currencies.convert(abs(expense.amount), expense.currency, targetCurrency)

            if (isIncome) {
                balances[matchedPayer] = (balances[matchedPayer] ?: 0.0) - convertedAmount

                if (expense.splitMode == "CUSTOM" && expense.memberSplits.isNotEmpty()) {
                    val totalRawShares = expense.memberSplits.values.sumOf { abs(it) }
                    val splitsList = expense.memberSplits.entries.toList()
                    val count = splitsList.size
                    var accumulatedShare = 0.0
                    splitsList.forEachIndexed { index, (member, shareAmount) ->
                        val matchedMember = matchMember(member)
                        val ratio = if (totalRawShares > 0) abs(shareAmount) / totalRawShares else 1.0 / count
                        val convertedShare = if (index == count - 1) {
                            MoneyMath.round2((convertedAmount - accumulatedShare))
                        } else {
                            val rounded = MoneyMath.round2((convertedAmount * ratio))
                            accumulatedShare += rounded
                            rounded
                        }
                        balances[matchedMember] = (balances[matchedMember] ?: 0.0) + convertedShare
                    }
                } else {
                    val cleanSplitAmong = expense.splitAmong.map { matchMember(it) }.distinct().ifEmpty { distinctMembers }
                    val splitCount = cleanSplitAmong.size
                    if (splitCount > 0) {
                        val rawShare = convertedAmount / splitCount
                        val roundedShare = MoneyMath.round2(rawShare)
                        val remainder = MoneyMath.round2((convertedAmount - (roundedShare * splitCount)))

                        cleanSplitAmong.forEachIndexed { index, matchedMember ->
                            val finalShare = if (index == 0) roundedShare + remainder else roundedShare
                            balances[matchedMember] = (balances[matchedMember] ?: 0.0) + finalShare
                        }
                    }
                }
            } else {
                balances[matchedPayer] = (balances[matchedPayer] ?: 0.0) + convertedAmount

                if (expense.splitMode == "CUSTOM" && expense.memberSplits.isNotEmpty()) {
                    val totalRawShares = expense.memberSplits.values.sumOf { abs(it) }
                    val splitsList = expense.memberSplits.entries.toList()
                    val count = splitsList.size
                    var accumulatedShare = 0.0
                    splitsList.forEachIndexed { index, (member, shareAmount) ->
                        val matchedMember = matchMember(member)
                        val ratio = if (totalRawShares > 0) abs(shareAmount) / totalRawShares else 1.0 / count
                        val convertedShare = if (index == count - 1) {
                            MoneyMath.round2((convertedAmount - accumulatedShare))
                        } else {
                            val rounded = MoneyMath.round2((convertedAmount * ratio))
                            accumulatedShare += rounded
                            rounded
                        }
                        balances[matchedMember] = (balances[matchedMember] ?: 0.0) - convertedShare
                    }
                } else {
                    val cleanSplitAmong = expense.splitAmong.map { matchMember(it) }.distinct().ifEmpty { distinctMembers }
                    val splitCount = cleanSplitAmong.size
                    if (splitCount > 0) {
                        val rawShare = convertedAmount / splitCount
                        val roundedShare = MoneyMath.round2(rawShare)
                        val remainder = MoneyMath.round2((convertedAmount - (roundedShare * splitCount)))

                        cleanSplitAmong.forEachIndexed { index, matchedMember ->
                            val finalShare = if (index == 0) roundedShare + remainder else roundedShare
                            balances[matchedMember] = (balances[matchedMember] ?: 0.0) - finalShare
                        }
                    }
                }
            }
        }

        val roundedBalances = balances.mapValues { MoneyMath.round2(it.value) }.toMutableMap()
        val totalResidual = MoneyMath.round2(roundedBalances.values.sum())
        if (abs(totalResidual) in 0.009..0.05 && roundedBalances.isNotEmpty()) {
            val maxEntry = roundedBalances.maxByOrNull { abs(it.value) }
            if (maxEntry != null) {
                roundedBalances[maxEntry.key] = MoneyMath.round2((maxEntry.value - totalResidual))
            }
        }

        return roundedBalances
    }

    fun calculateSettlements(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: Currencies.EUR,
        currentUserName: String = "You",
        currentUserEmail: String = ""
    ): List<Settlement> {
        val netBalances = calculateNetBalances(group, targetCurrency, currentUserName, currentUserEmail)
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

            val settleAmount = MoneyMath.round2(minOf(debtorAmount, creditorAmount))
            if (settleAmount > 0.009) {
                settlements.add(Settlement(from = debtor, to = creditor, amount = settleAmount))
            }

            val newDebtorBal = MoneyMath.round2((debtorAmount - settleAmount))
            if (newDebtorBal < 0.01) {
                debtors.remove(debtor)
            } else {
                debtors[debtor] = newDebtorBal
            }

            val newCreditorBal = MoneyMath.round2((creditorAmount - settleAmount))
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
            Settlement(from = key.first, to = key.second, amount = MoneyMath.round2(amount))
        }.filter { it.amount > 0.009 }
    }

    fun execute(
        group: Group,
        targetCurrency: String = group.expenses.firstOrNull()?.currency ?: Currencies.EUR,
        currentUserName: String = "You",
        currentUserEmail: String = ""
    ): SettlementResult {
        val balances = calculateNetBalances(group, targetCurrency, currentUserName, currentUserEmail)
        val settlements = calculateSettlements(group, targetCurrency, currentUserName, currentUserEmail)
        return SettlementResult(netBalances = balances, settlements = settlements)
    }
}
