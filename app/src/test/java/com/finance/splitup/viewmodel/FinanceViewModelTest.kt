package com.finance.splitup.viewmodel

import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testGroupExpenseAndSettlementCalculation() {
        val viewModel = FinanceViewModel()

        // Create a new group
        viewModel.addGroup("Test Trip", "Trip", listOf("Alice", "Bob"))
        val group = viewModel.groups.value.first { it.name == "Test Trip" }

        // Add expense paid by You, split among You, Alice, Bob
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Dinner",
            amount = 90.0,
            paidBy = "You",
            splitAmong = listOf("You", "Alice", "Bob"),
            date = "Today"
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        assertEquals(60.0, netBalances["You"] ?: 0.0, 0.01)
        assertEquals(-30.0, netBalances["Alice"] ?: 0.0, 0.01)
        assertEquals(-30.0, netBalances["Bob"] ?: 0.0, 0.01)

        val settlements = viewModel.calculateSettlements(updatedGroup)
        assertTrue(settlements.isNotEmpty())

        val expenseIdToDelete = updatedGroup.expenses.first().id
        viewModel.deleteGroupExpense(group.id, expenseIdToDelete)

        val groupAfterDelete = viewModel.groups.value.first { it.id == group.id }
        assertTrue(groupAfterDelete.expenses.none { it.id == expenseIdToDelete })
    }

    @Test
    fun testNonEvenSplitRounding() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Trio Group", "Dinner", listOf("Alice", "Bob"))
        val group = viewModel.groups.value.first { it.name == "Trio Group" }

        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Pizza",
            amount = 10.0,
            paidBy = "You",
            splitAmong = listOf("You", "Alice", "Bob"),
            date = "Today"
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        // 10.0 / 3 = 3.3333333333333335 -> rounded share 3.33
        // You paid 10.0, share 3.33 -> net balance 6.67
        assertEquals(6.67, netBalances["You"] ?: 0.0, 0.01)
        assertEquals(-3.33, netBalances["Alice"] ?: 0.0, 0.01)
        assertEquals(-3.33, netBalances["Bob"] ?: 0.0, 0.01)
    }

    @Test
    fun testMemberNameNormalizationAndCaseMatching() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Cabin Trip", "Trip", listOf("Alice"))
        val group = viewModel.groups.value.first { it.name == "Cabin Trip" }

        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Groceries",
            amount = 50.0,
            paidBy = "alice ",
            splitAmong = listOf("You", "ALICE"),
            date = "Today"
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        assertEquals(-25.0, netBalances["You"] ?: 0.0, 0.01)
        assertEquals(25.0, netBalances["Alice"] ?: 0.0, 0.01)
    }

    @Test
    fun testMemberManagement() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Roommates", "Roommates", listOf("Alice"))
        val group = viewModel.groups.value.first { it.name == "Roommates" }

        assertTrue(group.members.contains("Alice"))

        // Test adding pending member (e.g. custom invite)
        viewModel.addGroupMember(group.id, "Charlie", isPending = true)
        var updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertTrue(updatedGroup.pendingMembers.contains("Charlie"))
        assertFalse(updatedGroup.members.contains("Charlie"))

        // Test adding active member (e.g. SplitUp user / contact)
        viewModel.addGroupMember(group.id, "Dave", "dave@example.com", isPending = false)
        updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertTrue(updatedGroup.members.contains("Dave"))
        assertFalse(updatedGroup.pendingMembers.contains("Dave"))

        viewModel.removeGroupMember(group.id, "Alice")
        updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertFalse(updatedGroup.members.contains("Alice"))

        viewModel.removeGroupMember(group.id, "Dave")
        updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertFalse(updatedGroup.members.contains("Dave"))
    }

    @Test
    fun testDeclineAndReAddGroupMember() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Trip Group", "Trip", listOf("Alice"))
        val group = viewModel.groups.value.first { it.name == "Trip Group" }

        // Add Geetika as pending member
        viewModel.addGroupMember(group.id, "Geetika", "geetika@example.com", isPending = true)
        var updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertTrue(updatedGroup.pendingMembers.contains("Geetika"))

        // Re-adding Geetika should keep her as pending invitation rather than adding directly to members
        viewModel.addGroupMember(group.id, "Geetika", "geetika@example.com", isPending = true)
        updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertFalse(updatedGroup.members.contains("Geetika"))

        // Test matching helper functions
        assertTrue(viewModel.isUserMatch("Geetika", "Geetika Sharma", "geetika@example.com"))
        assertTrue(viewModel.isMemberMatch("Geetika", "Geetika", "geetika@example.com"))
    }

    @Test
    fun testBudgetThresholdWarning() {
        val viewModel = FinanceViewModel()
        val category = ExpenseCategories.FOOD
        viewModel.updateBudget(category, 100.0)

        assertNull(viewModel.budgetWarningMessage)

        // Adding 90.0 in Food exceeds 80% threshold (80.0)
        viewModel.addExpense(
            title = "Fancy Restaurant",
            amount = 90.0,
            category = category,
            date = "Today",
            description = "Dinner",
            paidBy = "You",
            currency = Currencies.EUR
        )

        assertNotNull(viewModel.budgetWarningMessage)
        viewModel.clearBudgetWarning()
        assertNull(viewModel.budgetWarningMessage)
    }

    @Test
    fun testMultiCurrencyExpenseCreation() {
        val viewModel = FinanceViewModel()
        val initialCount = viewModel.expenses.value.size

        viewModel.addExpense(
            title = "Tokyo Ramen",
            amount = 1200.0,
            category = ExpenseCategories.FOOD,
            date = "Today",
            description = "Shinjuku",
            paidBy = "You",
            currency = Currencies.EUR
        )

        assertEquals(initialCount + 1, viewModel.expenses.value.size)
        val added = viewModel.expenses.value.first()
        assertEquals("Tokyo Ramen", added.title)
        assertEquals(Currencies.EUR, added.currency)
    }

    @Test
    fun testMinFlowDebtSimplification() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Trip Group", "Trip", listOf("Alice", "Bob", "Charlie"))
        val group = viewModel.groups.value.first { it.name == "Trip Group" }

        // Alice pays $30 for Alice and Bob -> Bob owes Alice $15
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Lunch",
            amount = 30.0,
            paidBy = "Alice",
            splitAmong = listOf("Alice", "Bob"),
            date = "Today"
        )

        // Bob pays $30 for Bob and Charlie -> Charlie owes Bob $15
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Taxi",
            amount = 30.0,
            paidBy = "Bob",
            splitAmong = listOf("Bob", "Charlie"),
            date = "Today"
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        assertEquals(15.0, netBalances["Alice"] ?: 0.0, 0.01)
        assertEquals(0.0, netBalances["Bob"] ?: 0.0, 0.01)
        assertEquals(-15.0, netBalances["Charlie"] ?: 0.0, 0.01)

        val settlements = viewModel.calculateSettlements(updatedGroup)
        // Min-Flow debt simplification reduces transactions to 1: Charlie pays Alice $15
        assertEquals(1, settlements.size)
        assertEquals("Charlie", settlements.first().from)
        assertEquals("Alice", settlements.first().to)
        assertEquals(15.0, settlements.first().amount, 0.01)
    }

    @Test
    fun testCustomMemberSplitCalculations() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Dinner Group", "Dinner", listOf("Alice", "Bob"))
        val group = viewModel.groups.value.first { it.name == "Dinner Group" }

        val customSplits = mapOf("You" to 20.0, "Alice" to 50.0, "Bob" to 30.0)
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Steakhouse",
            amount = 100.0,
            paidBy = "You",
            splitAmong = listOf("You", "Alice", "Bob"),
            date = "Today",
            splitMode = "CUSTOM",
            memberSplits = customSplits
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        assertEquals(80.0, netBalances["You"] ?: 0.0, 0.01)
        assertEquals(-50.0, netBalances["Alice"] ?: 0.0, 0.01)
        assertEquals(-30.0, netBalances["Bob"] ?: 0.0, 0.01)
    }

    @Test
    fun testDefaultCurrencyPreferenceChangePreservesRecordedExpenseAmount() {
        val viewModel = FinanceViewModel()
        viewModel.addExpense(
            title = "Book",
            amount = 50.0,
            category = ExpenseCategories.SHOPPING,
            date = "Today",
            description = "Tech Book",
            paidBy = "You",
            currency = Currencies.USD
        )

        val recorded = viewModel.expenses.value.first { it.title == "Book" }
        assertEquals(50.0, recorded.amount, 0.01)
        assertEquals(Currencies.USD, recorded.currency)

        // Changing display currency preference
        viewModel.updateDefaultCurrencyAndConvert(Currencies.EUR)

        val recordedAfterPrefChange = viewModel.expenses.value.first { it.title == "Book" }
        assertEquals(50.0, recordedAfterPrefChange.amount, 0.01)
        assertEquals(Currencies.USD, recordedAfterPrefChange.currency)
    }

    @Test
    fun testCustomMemberToMemberPaymentAdjustment() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Payment Group", "Trip", listOf("Alice", "Bob"))
        val group = viewModel.groups.value.first { it.name == "Payment Group" }

        // Alice pays $90 for Alice, Bob, You ($30 each)
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Dinner",
            amount = 90.0,
            paidBy = "Alice",
            splitAmong = listOf("You", "Alice", "Bob"),
            date = "Today"
        )

        // Bob pays You $30 directly instead of Alice
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Payment: Bob to You",
            amount = 30.0,
            paidBy = "Bob",
            splitAmong = listOf("You"),
            date = "Today",
            category = ExpenseCategories.SETTLEMENT
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        assertEquals(60.0, netBalances["Alice"] ?: 0.0, 0.01)
        assertEquals(0.0, netBalances["Bob"] ?: 0.0, 0.01)
        assertEquals(-60.0, netBalances["You"] ?: 0.0, 0.01)
    }

    @Test
    fun testGroupIncomeCalculation() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Income Group", "Trip", listOf("Alice", "Bob"))
        val group = viewModel.groups.value.first { it.name == "Income Group" }

        // Group receives $90 refund collected by Alice, split among You, Alice, Bob ($30 each)
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Vendor Refund",
            amount = 90.0,
            paidBy = "Alice",
            splitAmong = listOf("You", "Alice", "Bob"),
            date = "Today",
            category = ExpenseCategories.INCOME
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        assertEquals(-60.0, netBalances["Alice"] ?: 0.0, 0.01)
        assertEquals(30.0, netBalances["You"] ?: 0.0, 0.01)
        assertEquals(30.0, netBalances["Bob"] ?: 0.0, 0.01)
    }

    @Test
    fun testSettlementConsolidation() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Consolidate Group", "Trip", listOf("Alice"))
        val group = viewModel.groups.value.first { it.name == "Consolidate Group" }

        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Payment 1",
            amount = 20.0,
            paidBy = "You",
            splitAmong = listOf("Alice"),
            date = "Today",
            category = ExpenseCategories.SETTLEMENT
        )
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Payment 2",
            amount = 30.0,
            paidBy = "You",
            splitAmong = listOf("Alice"),
            date = "Today",
            category = ExpenseCategories.SETTLEMENT
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val settlements = viewModel.calculateSettlements(updatedGroup)

        assertEquals(1, settlements.size)
        assertEquals("Alice", settlements.first().from)
        assertEquals("You", settlements.first().to)
        assertEquals(50.0, settlements.first().amount, 0.01)
    }

    @Test
    fun testZeroSumPennyConservationInvariant() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Odd Split Group", "Trip", listOf("Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace"))
        val group = viewModel.groups.value.first { it.name == "Odd Split Group" }

        // $100 split among 8 members (100 / 8 = 12.50)
        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Big Dinner",
            amount = 100.0,
            paidBy = "You",
            splitAmong = group.members,
            date = "Today"
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalances = viewModel.calculateNetBalances(updatedGroup)

        // Verify Zero-Sum Conservation Invariant: sum of all net balances MUST equal 0.00
        val sum = kotlin.math.round(netBalances.values.sum() * 100) / 100.0
        assertEquals(0.00, sum, 0.001)

        val settlements = viewModel.calculateSettlements(updatedGroup)
        assertTrue(settlements.isNotEmpty())
        // Verify total settlement payments equal sum of positive credits
        val totalSettled = settlements.sumOf { it.amount }
        val totalCredited = netBalances.values.filter { it > 0 }.sum()
        assertEquals(totalCredited, totalSettled, 0.01)
    }

    @Test
    fun testTargetCurrencySettlementCalculation() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("US Dollar Trip", "Trip", listOf("Alice", "Bob"))
        val group = viewModel.groups.value.first { it.name == "US Dollar Trip" }

        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Hotel in NYC",
            amount = 150.0,
            paidBy = "You",
            splitAmong = listOf("You", "Alice", "Bob"),
            date = "Today",
            currency = Currencies.USD
        )

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        val netBalancesInUsd = viewModel.calculateNetBalances(updatedGroup, Currencies.USD)

        assertEquals(100.0, netBalancesInUsd["You"] ?: 0.0, 0.01)
        assertEquals(-50.0, netBalancesInUsd["Alice"] ?: 0.0, 0.01)
        assertEquals(-50.0, netBalancesInUsd["Bob"] ?: 0.0, 0.01)

        val settlementsInUsd = viewModel.calculateSettlements(updatedGroup, Currencies.USD)
        assertEquals(2, settlementsInUsd.size)
        assertTrue(settlementsInUsd.all { it.amount == 50.0 })
    }

    @Test
    fun testGroupApprovalSettlementRecordCreation() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Approval Group", "Trip", listOf("Alice", "Bob"), requireApproval = true)
        val group = viewModel.groups.value.first { it.name == "Approval Group" }
        assertTrue(group.requireApproval)

        var pendingTriggered = false
        viewModel.recordSettlementPayment(
            group = group,
            payer = "You",
            payee = "Alice",
            amount = 30.0,
            currency = Currencies.EUR,
            onResult = { isPending ->
                pendingTriggered = isPending
            }
        )

        assertTrue(pendingTriggered)
    }

    @Test
    fun testApproveSettlementRecordRequest() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Approval Group 2", "Trip", listOf("Alice", "Bob"), requireApproval = true)
        val group = viewModel.groups.value.first { it.name == "Approval Group 2" }

        val settlementExpense = com.finance.splitup.data.GroupExpense(
            id = "settlement_123",
            title = "Payment: You to Alice",
            amount = 30.0,
            paidBy = "You",
            date = "Today",
            splitAmong = listOf("Alice"),
            currency = Currencies.EUR,
            category = ExpenseCategories.SETTLEMENT
        )
        val payloadJson = kotlinx.serialization.json.Json.encodeToString(com.finance.splitup.data.GroupExpense.serializer(), settlementExpense)

        val request = com.finance.splitup.data.db.GroupApprovalRequestEntity(
            id = "req_1",
            groupId = group.id,
            groupName = group.name,
            targetId = settlementExpense.id,
            requestType = "SETTLEMENT_RECORD",
            requestedBy = "You",
            requestedByEmail = "you@example.com",
            ownerName = "Alice",
            ownerEmail = "alice@example.com",
            payloadJson = payloadJson,
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        viewModel.approveGroupRequest(request)

        val updatedGroup = viewModel.groups.value.first { it.id == group.id }
        assertTrue(updatedGroup.expenses.any { it.id == "settlement_123" })
        val addedExpense = updatedGroup.expenses.first { it.id == "settlement_123" }
        assertEquals(30.0, addedExpense.amount, 0.01)
        assertEquals("Payment: You to Alice", addedExpense.title)
    }

    @Test
    fun testApproveExpenseEditAndDeleteRequests() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Approval Group 3", "Trip", listOf("Alice"), requireApproval = true)
        var group = viewModel.groups.value.first { it.name == "Approval Group 3" }

        viewModel.addGroupExpense(
            groupId = group.id,
            title = "Lunch",
            amount = 40.0,
            paidBy = "You",
            splitAmong = listOf("You", "Alice"),
            date = "Today"
        )
        group = viewModel.groups.value.first { it.id == group.id }
        val expenseId = group.expenses.first().id

        // Test EXPENSE_EDIT approval
        val editedExpense = group.expenses.first().copy(amount = 60.0, title = "Fancy Lunch")
        val editPayload = kotlinx.serialization.json.Json.encodeToString(com.finance.splitup.data.GroupExpense.serializer(), editedExpense)
        val editRequest = com.finance.splitup.data.db.GroupApprovalRequestEntity(
            id = "req_edit",
            groupId = group.id,
            groupName = group.name,
            targetId = expenseId,
            requestType = "EXPENSE_EDIT",
            requestedBy = "Alice",
            requestedByEmail = "alice@example.com",
            ownerName = "You",
            ownerEmail = "you@example.com",
            payloadJson = editPayload,
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        viewModel.approveGroupRequest(editRequest)

        group = viewModel.groups.value.first { it.id == group.id }
        assertEquals(60.0, group.expenses.first().amount, 0.01)
        assertEquals("Fancy Lunch", group.expenses.first().title)

        // Test EXPENSE_DELETE approval
        val deleteRequest = com.finance.splitup.data.db.GroupApprovalRequestEntity(
            id = "req_del",
            groupId = group.id,
            groupName = group.name,
            targetId = expenseId,
            requestType = "EXPENSE_DELETE",
            requestedBy = "Alice",
            requestedByEmail = "alice@example.com",
            ownerName = "You",
            ownerEmail = "you@example.com",
            payloadJson = expenseId,
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        viewModel.approveGroupRequest(deleteRequest)

        group = viewModel.groups.value.first { it.id == group.id }
        assertTrue(group.expenses.none { it.id == expenseId })
    }

    @Test
    fun testApproveMemberRemoveAndRejectRequests() {
        val viewModel = FinanceViewModel()
        viewModel.addGroup("Approval Group 4", "Trip", listOf("Alice", "Bob"), requireApproval = true)
        var group = viewModel.groups.value.first { it.name == "Approval Group 4" }
        assertTrue(group.members.contains("Bob"))

        // Test MEMBER_REMOVE approval
        val removeRequest = com.finance.splitup.data.db.GroupApprovalRequestEntity(
            id = "req_remove",
            groupId = group.id,
            groupName = group.name,
            targetId = "Bob",
            requestType = "MEMBER_REMOVE",
            requestedBy = "Alice",
            requestedByEmail = "alice@example.com",
            ownerName = "You",
            ownerEmail = "you@example.com",
            payloadJson = "Bob",
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        viewModel.approveGroupRequest(removeRequest)

        group = viewModel.groups.value.first { it.id == group.id }
        assertFalse(group.members.contains("Bob"))

        // Test request rejection
        val rejectRequest = com.finance.splitup.data.db.GroupApprovalRequestEntity(
            id = "req_reject",
            groupId = group.id,
            groupName = group.name,
            targetId = "Alice",
            requestType = "MEMBER_REMOVE",
            requestedBy = "Bob",
            requestedByEmail = "bob@example.com",
            ownerName = "You",
            ownerEmail = "you@example.com",
            payloadJson = "Alice",
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        viewModel.rejectGroupRequest(rejectRequest)
        // Group members remain unchanged after rejection
        group = viewModel.groups.value.first { it.id == group.id }
        assertTrue(group.members.contains("Alice"))
    }

    @Test
    fun testDeclineExpenseEditRequestRemovesFromPendingList() {
        val viewModel = FinanceViewModel()
        viewModel.sendSampleApprovalRequest(null, "EXPENSE_EDIT")

        var pendingList = viewModel.pendingApprovalRequests.value
        assertTrue(pendingList.isNotEmpty())
        val reqToDecline = pendingList.first()

        viewModel.rejectGroupRequest(reqToDecline)

        pendingList = viewModel.pendingApprovalRequests.value
        assertFalse(pendingList.any { it.id == reqToDecline.id })
    }
}

