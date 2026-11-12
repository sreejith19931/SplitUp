package com.finance.splitup.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM expenses ORDER BY ROWID DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpenseCount(): Int

    @Upsert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT * FROM user_groups")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM user_groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT COUNT(*) FROM user_groups")
    suspend fun getGroupCount(): Int

    @Upsert
    suspend fun insertGroup(group: GroupEntity)

    @Query("DELETE FROM user_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun getBudgetCount(): Int

    @Upsert
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("SELECT * FROM user_profiles WHERE id = 'current_user'")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM activity_logs WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getActivityLogsForGroup(groupId: String): Flow<List<ActivityLogEntity>>

    @Upsert
    suspend fun insertActivityLog(log: ActivityLogEntity)

    @Query("SELECT * FROM recurring_expenses")
    fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Upsert
    suspend fun upsertRecurringExpense(recurring: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringExpense(id: String)

    @Query("SELECT * FROM incomes")
    fun getAllIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT COUNT(*) FROM incomes")
    suspend fun getIncomeCount(): Int

    @Upsert
    suspend fun insertIncome(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncome(id: String)

    // Dirty state queries for Delta Sync
    @Query("SELECT * FROM expenses WHERE isDirty = 1")
    suspend fun getDirtyExpenses(): List<ExpenseEntity>

    @Query("UPDATE expenses SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markExpensesClean(ids: List<String>)

    @Query("SELECT * FROM user_groups WHERE isDirty = 1")
    suspend fun getDirtyGroups(): List<GroupEntity>

    @Query("UPDATE user_groups SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markGroupsClean(ids: List<String>)

    @Query("SELECT * FROM incomes WHERE isDirty = 1")
    suspend fun getDirtyIncomes(): List<IncomeEntity>

    @Query("UPDATE incomes SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markIncomesClean(ids: List<String>)

    @Query("SELECT * FROM recurring_expenses WHERE isDirty = 1")
    suspend fun getDirtyRecurringExpenses(): List<RecurringExpenseEntity>

    @Query("UPDATE recurring_expenses SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markRecurringExpensesClean(ids: List<String>)

    @Query("SELECT * FROM budgets WHERE isDirty = 1")
    suspend fun getDirtyBudgets(): List<BudgetEntity>

    @Query("UPDATE budgets SET isDirty = 0 WHERE category IN (:categories)")
    suspend fun markBudgetsClean(categories: List<String>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM user_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM group_expenses")
    suspend fun deleteAllGroupExpenses()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAllUserProfiles()

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAllActivityLogs()

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAllRecurringExpenses()

    @Query("DELETE FROM incomes")
    suspend fun deleteAllIncomes()

    @androidx.room.Transaction
    suspend fun clearAllLocalData() {
        deleteAllExpenses()
        deleteAllGroups()
        deleteAllGroupExpenses()
        deleteAllBudgets()
        deleteAllUserProfiles()
        deleteAllActivityLogs()
        deleteAllRecurringExpenses()
        deleteAllIncomes()
    }
}

@Database(entities = [ExpenseEntity::class, GroupEntity::class, GroupExpenseEntity::class, BudgetEntity::class, UserProfileEntity::class, ActivityLogEntity::class, RecurringExpenseEntity::class, IncomeEntity::class], version = 16, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_logs ADD COLUMN referenceId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE group_expenses ADD COLUMN receiptUrl TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "splitup_database"
                )
                .addMigrations(MIGRATION_14_15, MIGRATION_15_16)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
