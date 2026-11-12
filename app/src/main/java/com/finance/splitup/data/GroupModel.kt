package com.finance.splitup.data

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
@Serializable
data class GroupExpense(
    val id: String = UUID.randomUUID().toString(),
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
)

@Immutable
@Serializable
data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "", // "Roommates", "Trip", "Dinner", "Other"
    val members: List<String> = emptyList(),
    val pendingMembers: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val memberEmails: List<String> = emptyList(),
    val expenses: List<GroupExpense> = emptyList(),
    val createdBy: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val emoji: String = "✈️",
    val colorHex: String = "#3B82F6"
) {
    val allMembers: List<String>
        get() = (members + pendingMembers).distinct()
}

@Immutable
data class Settlement(
    val from: String,
    val to: String,
    val amount: Double
)

@Immutable
@Serializable
data class AppContact(
    val name: String,
    val email: String,
    val isSplitUpMember: Boolean = false,
    val upiVpa: String = "",
    val paypalHandle: String = "",
    val venmoHandle: String = "",
    val revolutHandle: String = ""
)

object SampleContacts {
    fun loadDeviceContacts(context: android.content.Context): List<AppContact> {
        val list = mutableListOf<AppContact>()
        try {
            val emailCursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null,
                null,
                null
            )
            emailCursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                val emailIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else "Contact"
                    val email = if (emailIdx >= 0) it.getString(emailIdx) else ""
                    if (!name.isNullOrBlank() && !email.isNullOrBlank()) {
                        list.add(AppContact(name = name, email = email, isSplitUpMember = email.endsWith("@splitup.app") || email.endsWith("@splitup.finance") || email.contains("splitup")))
                    }
                }
            }

            val phoneCursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
            phoneCursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else "Contact"
                    val phone = if (phoneIdx >= 0) it.getString(phoneIdx) else ""
                    if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
                        list.add(AppContact(name = name, email = phone, isSplitUpMember = false))
                    }
                }
            }
        } catch (_: Exception) {}
        return list.distinctBy { it.email.lowercase() }
    }
}

object SampleGroups {
    val initialGroups = listOf(
        Group(
            id = "group_1",
            name = "Apartment 4B Roommates",
            type = "Roommates",
            members = listOf("You", "Alice", "Bob"),
            expenses = listOf(
                GroupExpense(
                    title = "Electric Bill",
                    amount = 120.0,
                    paidBy = "You",
                    date = "Oct 24",
                    splitAmong = listOf("You", "Alice", "Bob"),
                    currency = Currencies.EUR
                ),
                GroupExpense(
                    title = "Internet Monthly",
                    amount = 60.0,
                    paidBy = "Alice",
                    date = "Oct 22",
                    splitAmong = listOf("You", "Alice", "Bob"),
                    currency = Currencies.EUR
                ),
                GroupExpense(
                    title = "Groceries Stock",
                    amount = 90.0,
                    paidBy = "Bob",
                    date = "Oct 20",
                    splitAmong = listOf("You", "Alice", "Bob"),
                    currency = Currencies.EUR
                )
            )
        ),
        Group(
            id = "group_2",
            name = "Weekend Tahoe Trip",
            type = "Trip",
            members = listOf("You", "Charlie", "David", "Eve"),
            expenses = listOf(
                GroupExpense(
                    title = "Cabin Rental",
                    amount = 600.0,
                    paidBy = "You",
                    date = "Oct 15",
                    splitAmong = listOf("You", "Charlie", "David", "Eve"),
                    currency = Currencies.EUR
                ),
                GroupExpense(
                    title = "Gas & Tolls",
                    amount = 80.0,
                    paidBy = "Charlie",
                    date = "Oct 15",
                    splitAmong = listOf("You", "Charlie", "David", "Eve"),
                    currency = Currencies.EUR
                )
            )
        ),
        Group(
            id = "group_3",
            name = "Friday Dinner Club",
            type = "Dinner",
            members = listOf("You", "Alice", "Frank", "Grace"),
            expenses = listOf(
                GroupExpense(
                    title = "Italian Bistro",
                    amount = 160.0,
                    paidBy = "You",
                    date = "Oct 18",
                    splitAmong = listOf("You", "Alice", "Frank", "Grace"),
                    currency = Currencies.EUR
                )
            )
        )
    )
}
