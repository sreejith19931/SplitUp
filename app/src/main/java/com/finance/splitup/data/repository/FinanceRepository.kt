package com.finance.splitup.data.repository

import com.finance.splitup.data.Expense
import com.finance.splitup.data.Group
import com.finance.splitup.data.db.BudgetEntity
import com.finance.splitup.data.db.ExpenseEntity
import com.finance.splitup.data.db.FinanceDao
import com.finance.splitup.data.db.GroupEntity
import com.finance.splitup.data.db.IncomeEntity
import com.finance.splitup.data.db.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FinanceRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseCount(): Int
    suspend fun insertExpense(expense: Expense)
    suspend fun deleteExpense(id: String)

    fun getAllGroups(): Flow<List<Group>>
    suspend fun getGroupById(id: String): Group?
    suspend fun getGroupCount(): Int
    suspend fun insertGroup(group: Group)
    suspend fun insertGroupClean(group: Group)
    suspend fun deleteGroup(id: String)

    fun getAllBudgets(): Flow<Map<String, Double>>
    suspend fun getBudgetCount(): Int
    suspend fun upsertBudget(category: String, limit: Double)

    fun getUserProfile(): Flow<com.finance.splitup.data.db.UserProfileEntity?>
    suspend fun upsertUserProfile(profile: com.finance.splitup.data.db.UserProfileEntity)

    fun getActivityLogsForGroup(groupId: String): Flow<List<com.finance.splitup.data.db.ActivityLogEntity>>
    suspend fun insertActivityLog(log: com.finance.splitup.data.db.ActivityLogEntity)

    fun getAllIncomes(): Flow<List<IncomeEntity>>
    suspend fun getIncomeCount(): Int
    suspend fun insertIncome(income: IncomeEntity)
    suspend fun deleteIncome(id: String)

    fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>>
    suspend fun upsertRecurringExpense(recurring: RecurringExpenseEntity)
    suspend fun deleteRecurringExpense(id: String)

    suspend fun getDirtyExpenses(): List<ExpenseEntity>
    suspend fun markExpensesClean(ids: List<String>)
    suspend fun getDirtyGroups(): List<GroupEntity>
    suspend fun markGroupsClean(ids: List<String>)
    suspend fun getDirtyIncomes(): List<IncomeEntity>
    suspend fun markIncomesClean(ids: List<String>)
    suspend fun getDirtyRecurringExpenses(): List<RecurringExpenseEntity>
    suspend fun markRecurringExpensesClean(ids: List<String>)
    suspend fun getDirtyBudgets(): List<BudgetEntity>
    suspend fun markBudgetsClean(categories: List<String>)

    suspend fun hasUnsyncedChanges(): Boolean

    suspend fun clearAllLocalData()
}

class OfflineFirstFinanceRepository(private val dao: FinanceDao) : FinanceRepository {
    override fun getAllExpenses(): Flow<List<Expense>> {
        return dao.getAllExpenses().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getExpenseCount(): Int {
        return dao.getExpenseCount()
    }

    override suspend fun insertExpense(expense: Expense) {
        dao.insertExpense(ExpenseEntity.fromDomain(expense, isDirty = true))
    }

    override suspend fun deleteExpense(id: String) {
        dao.deleteExpense(id)
    }

    override fun getAllGroups(): Flow<List<Group>> {
        return dao.getAllGroups().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getGroupById(id: String): Group? {
        return dao.getGroupById(id)?.toDomain()
    }

    override suspend fun getGroupCount(): Int {
        return dao.getGroupCount()
    }

    override suspend fun insertGroup(group: Group) {
        dao.insertGroup(GroupEntity.fromDomain(group, isDirty = true))
    }

    override suspend fun insertGroupClean(group: Group) {
        dao.insertGroup(GroupEntity.fromDomain(group, isDirty = false))
    }

    override suspend fun deleteGroup(id: String) {
        dao.deleteGroup(id)
    }

    override fun getAllBudgets(): Flow<Map<String, Double>> {
        return dao.getAllBudgets().map { entities -> entities.associate { it.category to it.limit } }
    }

    override suspend fun getBudgetCount(): Int {
        return dao.getBudgetCount()
    }

    override suspend fun upsertBudget(category: String, limit: Double) {
        dao.upsertBudget(BudgetEntity(category, limit, updatedAt = System.currentTimeMillis(), isDirty = true))
    }

    override fun getUserProfile(): Flow<com.finance.splitup.data.db.UserProfileEntity?> {
        return dao.getUserProfile()
    }

    override suspend fun upsertUserProfile(profile: com.finance.splitup.data.db.UserProfileEntity) {
        dao.upsertUserProfile(profile)
    }

    override fun getActivityLogsForGroup(groupId: String): Flow<List<com.finance.splitup.data.db.ActivityLogEntity>> {
        return dao.getActivityLogsForGroup(groupId)
    }

    override suspend fun insertActivityLog(log: com.finance.splitup.data.db.ActivityLogEntity) {
        dao.insertActivityLog(log)
    }

    override fun getAllIncomes(): Flow<List<IncomeEntity>> {
        return dao.getAllIncomes()
    }

    override suspend fun getIncomeCount(): Int {
        return dao.getIncomeCount()
    }

    override suspend fun insertIncome(income: IncomeEntity) {
        dao.insertIncome(income.copy(updatedAt = System.currentTimeMillis(), isDirty = true))
    }

    override suspend fun deleteIncome(id: String) {
        dao.deleteIncome(id)
    }

    override fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>> {
        return dao.getAllRecurringExpenses()
    }

    override suspend fun upsertRecurringExpense(recurring: RecurringExpenseEntity) {
        dao.upsertRecurringExpense(recurring.copy(updatedAt = System.currentTimeMillis(), isDirty = true))
    }

    override suspend fun deleteRecurringExpense(id: String) {
        dao.deleteRecurringExpense(id)
    }

    override suspend fun getDirtyExpenses(): List<ExpenseEntity> = dao.getDirtyExpenses()
    override suspend fun markExpensesClean(ids: List<String>) = dao.markExpensesClean(ids)

    override suspend fun getDirtyGroups(): List<GroupEntity> = dao.getDirtyGroups()
    override suspend fun markGroupsClean(ids: List<String>) = dao.markGroupsClean(ids)

    override suspend fun getDirtyIncomes(): List<IncomeEntity> = dao.getDirtyIncomes()
    override suspend fun markIncomesClean(ids: List<String>) = dao.markIncomesClean(ids)

    override suspend fun getDirtyRecurringExpenses(): List<RecurringExpenseEntity> = dao.getDirtyRecurringExpenses()
    override suspend fun markRecurringExpensesClean(ids: List<String>) = dao.markRecurringExpensesClean(ids)

    override suspend fun getDirtyBudgets(): List<BudgetEntity> = dao.getDirtyBudgets()
    override suspend fun markBudgetsClean(categories: List<String>) = dao.markBudgetsClean(categories)

    override suspend fun hasUnsyncedChanges(): Boolean {
        return getDirtyExpenses().isNotEmpty() ||
               getDirtyGroups().isNotEmpty() ||
               getDirtyIncomes().isNotEmpty() ||
               getDirtyRecurringExpenses().isNotEmpty() ||
               getDirtyBudgets().isNotEmpty()
    }

    override suspend fun clearAllLocalData() {
        dao.clearAllLocalData()
    }
}
