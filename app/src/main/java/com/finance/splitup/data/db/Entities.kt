package com.finance.splitup.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Expense
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import com.finance.splitup.data.GroupExpense
import kotlinx.serialization.Serializable
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    private val expenseListType = Types.newParameterizedType(List::class.java, GroupExpense::class.java)
    private val expenseListAdapter = moshi.adapter<List<GroupExpense>>(expenseListType)

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Double::class.javaObjectType)
    private val mapAdapter = moshi.adapter<Map<String, Double>>(mapType)

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return try {
            stringListAdapter.toJson(value) ?: "[]"
        } catch (_: Exception) {
            "[]"
        }
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            stringListAdapter.fromJson(value) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromGroupExpenseList(value: List<GroupExpense>): String {
        return try {
            expenseListAdapter.toJson(value) ?: "[]"
        } catch (_: Exception) {
            "[]"
        }
    }

    @TypeConverter
    fun toGroupExpenseList(value: String): List<GroupExpense> {
        if (value.isBlank()) return emptyList()
        return try {
            expenseListAdapter.fromJson(value) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringDoubleMap(value: Map<String, Double>): String {
        return try {
            mapAdapter.toJson(value) ?: "{}"
        } catch (_: Exception) {
            "{}"
        }
    }

    @TypeConverter
    fun toStringDoubleMap(value: String): Map<String, Double> {
        if (value.isBlank()) return emptyMap()
        return try {
            mapAdapter.fromJson(value) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val date: String = "",
    val description: String = "",
    val paidBy: String = "",
    val currency: String = Currencies.EUR,
    val paymentMethod: String = "Cash",
    val receiptImagePath: String? = null,
    val receiptUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true
) {
    fun toDomain() = Expense(id, title, amount, category, date, description, paidBy, currency, paymentMethod, receiptImagePath, receiptUrl, updatedAt)
    companion object {
        fun fromDomain(expense: Expense, isDirty: Boolean = true) = ExpenseEntity(
            expense.id, expense.title, expense.amount, expense.category, expense.date, expense.description, expense.paidBy, expense.currency, expense.paymentMethod, expense.receiptImagePath, expense.receiptUrl, expense.updatedAt, isDirty
        )
    }
}

@Entity(tableName = "user_groups")
data class GroupEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "",
    val members: List<String> = emptyList(),
    val pendingMembers: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val memberEmails: List<String> = emptyList(),
    val expenses: List<GroupExpense> = emptyList(),
    val createdBy: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val emoji: String = "✈️",
    val colorHex: String = "#3B82F6",
    val isArchived: Boolean = false,
    val isDirty: Boolean = true
) {
    fun toDomain() = Group(id, name, type, members, pendingMembers, memberUids, memberEmails, expenses, createdBy, updatedAt, emoji, colorHex, isArchived)
    companion object {
        fun fromDomain(group: Group, isDirty: Boolean = true) = GroupEntity(
            group.id, group.name, group.type, group.members, group.pendingMembers, group.memberUids, group.memberEmails, group.expenses, group.createdBy, group.updatedAt, group.emoji, group.colorHex, group.isArchived, isDirty
        )
    }
}

@Entity(
    tableName = "group_expenses",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("groupId")]
)
data class GroupExpenseEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val groupId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val paidBy: String = "",
    val date: String = "Today",
    val splitAmong: List<String> = emptyList(),
    val currency: String = Currencies.EUR,
    val splitMode: String = "EQUAL",
    val memberSplits: Map<String, Double> = emptyMap(),
    val category: String = ExpenseCategories.OTHER,
    val receiptImagePath: String? = null,
    val receiptUrl: String? = null
) {
    fun toDomain() = GroupExpense(id, title, amount, paidBy, date, splitAmong, currency, splitMode, memberSplits, category, receiptImagePath, receiptUrl)
    companion object {
        fun fromDomain(groupId: String, expense: GroupExpense) = GroupExpenseEntity(
            expense.id, groupId, expense.title, expense.amount, expense.paidBy, expense.date, expense.splitAmong, expense.currency, expense.splitMode, expense.memberSplits, expense.category, expense.receiptImagePath, expense.receiptUrl
        )
    }
}

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val category: String = "",
    val limit: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val id: String = "current_user",
    val upiVpa: String = "",
    val paypalHandle: String = "",
    val venmoHandle: String = "",
    val revolutHandle: String = ""
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val groupId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val actorName: String = "",
    val actionType: String = "",
    val description: String = "",
    val referenceId: String = ""
)

@Serializable
@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val frequency: String = "Monthly",
    val nextDueDate: String = "",
    val currency: String = Currencies.EUR,
    val autoAdd: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true
)

@Serializable
@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val amount: Double = 0.0,
    val source: String = "Salary",
    val date: String = "Today",
    val currency: String = Currencies.EUR,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true
)
