package com.finance.splitup.domain

import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Group
import com.finance.splitup.data.GroupExpense
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateGroupMetricsUseCaseTest {

    private val metricsUseCase = CalculateGroupMetricsUseCase()

    @Test
    fun testTotalGroupSpentCalculation() {
        val exp1 = GroupExpense(title = "Lunch", amount = 30.0, paidBy = "Alice", currency = Currencies.EUR)
        val exp2 = GroupExpense(title = "Taxi", amount = 20.0, paidBy = "Bob", currency = Currencies.EUR)
        val group = Group(
            id = "g1",
            name = "Outing",
            members = listOf("Alice", "Bob"),
            expenses = listOf(exp1, exp2)
        )

        val totalSpent = metricsUseCase.calculateTotalGroupSpent(group, Currencies.EUR)
        assertEquals(50.0, totalSpent, 0.01)
    }

    @Test
    fun testUserNetBalanceCalculation() {
        val exp = GroupExpense(
            title = "Groceries",
            amount = 100.0,
            paidBy = "Alice",
            splitAmong = listOf("Alice", "Bob"),
            currency = Currencies.EUR
        )
        val group = Group(
            id = "g1",
            name = "Home",
            members = listOf("Alice", "Bob"),
            expenses = listOf(exp)
        )

        val aliceBal = metricsUseCase.calculateUserNetBalance(group, "Alice", Currencies.EUR)
        val bobBal = metricsUseCase.calculateUserNetBalance(group, "Bob", Currencies.EUR)

        assertEquals(50.0, aliceBal, 0.01)
        assertEquals(-50.0, bobBal, 0.01)
    }
}
