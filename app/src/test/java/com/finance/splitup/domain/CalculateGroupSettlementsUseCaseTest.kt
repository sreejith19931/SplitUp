package com.finance.splitup.domain

import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Group
import com.finance.splitup.data.GroupExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateGroupSettlementsUseCaseTest {

    private val useCase = CalculateGroupSettlementsUseCase()

    @Test
    fun testEqualSplitNetBalances() {
        val expense = GroupExpense(
            title = "Dinner",
            amount = 60.0,
            paidBy = "Alice",
            splitAmong = listOf("Alice", "Bob", "Charlie"),
            currency = Currencies.EUR
        )
        val group = Group(
            id = "g1",
            name = "Trip",
            members = listOf("Alice", "Bob", "Charlie"),
            expenses = listOf(expense)
        )

        val balances = useCase.calculateNetBalances(group, Currencies.EUR, "Alice")

        assertEquals(40.0, balances["Alice"] ?: 0.0, 0.01)
        assertEquals(-20.0, balances["Bob"] ?: 0.0, 0.01)
        assertEquals(-20.0, balances["Charlie"] ?: 0.0, 0.01)
    }

    @Test
    fun testCalculateSettlementsSimplification() {
        val expense = GroupExpense(
            title = "Hotel",
            amount = 90.0,
            paidBy = "Alice",
            splitAmong = listOf("Alice", "Bob", "Charlie"),
            currency = Currencies.EUR
        )
        val group = Group(
            id = "g1",
            name = "Trip",
            members = listOf("Alice", "Bob", "Charlie"),
            expenses = listOf(expense)
        )

        val settlements = useCase.calculateSettlements(group, Currencies.EUR, "Alice")

        assertEquals(2, settlements.size)
        assertTrue(settlements.any { it.from == "Bob" && it.to == "Alice" && abs(it.amount - 30.0) < 0.01 })
        assertTrue(settlements.any { it.from == "Charlie" && it.to == "Alice" && abs(it.amount - 30.0) < 0.01 })
    }

    private fun abs(value: Double) = kotlin.math.abs(value)
}
