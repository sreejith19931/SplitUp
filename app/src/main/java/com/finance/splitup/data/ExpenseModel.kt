package com.finance.splitup.data

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

@Immutable
@Serializable
data class Expense(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val date: String = "",
    val description: String = "",
    val paidBy: String = "",
    val currency: String = "EUR (€)",
    val paymentMethod: String = "Cash",
    val receiptImagePath: String? = null,
    val receiptUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

object PaymentMethods {
    const val CASH = "Cash"
    const val CREDIT_CARD = "Credit Card"
    const val DEBIT_CARD = "Debit Card"
    const val UPI = "UPI / Transfer"

    val all = listOf(CASH, CREDIT_CARD, DEBIT_CARD, UPI)
}

@Immutable
@Serializable
data class CategoryBudget(
    val category: String,
    val limit: Double
)

object Currencies {
    const val EUR = "EUR (€)"
    const val USD = "USD ($)"
    const val GBP = "GBP (£)"
    const val INR = "INR (₹)"
    const val CAD = "CAD (CA$)"
    const val AUD = "AUD (A$)"
    const val JPY = "JPY (¥)"
    const val CHF = "CHF (CHF)"
    const val SGD = "SGD (S$)"
    const val AED = "AED (AED)"
    const val CNY = "CNY (¥)"

    private val rates = java.util.concurrent.ConcurrentHashMap<String, Double>().apply {
        put("USD", 1.08)
        put("GBP", 0.85)
        put("INR", 90.0)
        put("CAD", 1.48)
        put("AUD", 1.65)
        put("JPY", 162.0)
        put("CHF", 0.95)
        put("SGD", 1.45)
        put("AED", 3.96)
        put("CNY", 7.80)
    }

    var liveUsdRate: Double
        get() = rates["USD"] ?: 1.08
        set(value) { rates["USD"] = value }

    var liveGbpRate: Double
        get() = rates["GBP"] ?: 0.85
        set(value) { rates["GBP"] = value }

    var liveInrRate: Double
        get() = rates["INR"] ?: 90.0
        set(value) { rates["INR"] = value }

    var liveCadRate: Double
        get() = rates["CAD"] ?: 1.48
        set(value) { rates["CAD"] = value }

    var liveAudRate: Double
        get() = rates["AUD"] ?: 1.65
        set(value) { rates["AUD"] = value }

    var liveJpyRate: Double
        get() = rates["JPY"] ?: 162.0
        set(value) { rates["JPY"] = value }

    var liveChfRate: Double
        get() = rates["CHF"] ?: 0.95
        set(value) { rates["CHF"] = value }

    var liveSgdRate: Double
        get() = rates["SGD"] ?: 1.45
        set(value) { rates["SGD"] = value }

    var liveAedRate: Double
        get() = rates["AED"] ?: 3.96
        set(value) { rates["AED"] = value }

    var liveCnyRate: Double
        get() = rates["CNY"] ?: 7.80
        set(value) { rates["CNY"] = value }

    val all = listOf(EUR, USD, GBP, INR, CAD, AUD, JPY, CHF, SGD, AED, CNY)

    fun getFlag(currencyStr: String): String {
        return when {
            currencyStr.contains("EUR") || currencyStr.contains("€") -> "🇪🇺"
            currencyStr.contains("USD") || (currencyStr.contains("$") && !currencyStr.contains("CA$") && !currencyStr.contains("A$") && !currencyStr.contains("S$")) -> "🇺🇸"
            currencyStr.contains("GBP") || currencyStr.contains("£") -> "🇬🇧"
            currencyStr.contains("INR") || currencyStr.contains("₹") -> "🇮🇳"
            currencyStr.contains("CAD") || currencyStr.contains("CA$") -> "🇨🇦"
            currencyStr.contains("AUD") || currencyStr.contains("A$") -> "🇦🇺"
            currencyStr.contains("JPY") || currencyStr.contains("¥") -> "🇯🇵"
            currencyStr.contains("CHF") -> "🇨🇭"
            currencyStr.contains("SGD") || currencyStr.contains("S$") -> "🇸🇬"
            currencyStr.contains("AED") -> "🇦🇪"
            currencyStr.contains("CNY") -> "🇨🇳"
            else -> "🇪🇺"
        }
    }

    fun getSymbol(currencyStr: String): String {
        return when {
            currencyStr.contains("€") -> "€"
            currencyStr.contains("CA$") -> "CA$"
            currencyStr.contains("A$") -> "A$"
            currencyStr.contains("S$") -> "S$"
            currencyStr.contains("$") -> "$"
            currencyStr.contains("£") -> "£"
            currencyStr.contains("₹") -> "₹"
            currencyStr.contains("¥") -> "¥"
            currencyStr.contains("CHF") -> "CHF"
            currencyStr.contains("AED") -> "AED"
            else -> "€"
        }
    }

    fun convertToEUR(amount: Double, currencyStr: String): Double {
        return when {
            currencyStr.contains("USD") || (currencyStr.contains("$") && !currencyStr.contains("CA$") && !currencyStr.contains("A$") && !currencyStr.contains("S$")) -> amount / liveUsdRate
            currencyStr.contains("GBP") || currencyStr.contains("£") -> amount / liveGbpRate
            currencyStr.contains("INR") || currencyStr.contains("₹") -> amount / liveInrRate
            currencyStr.contains("CAD") || currencyStr.contains("CA$") -> amount / liveCadRate
            currencyStr.contains("AUD") || currencyStr.contains("A$") -> amount / liveAudRate
            currencyStr.contains("JPY") || currencyStr.contains("¥") -> amount / liveJpyRate
            currencyStr.contains("CHF") -> amount / liveChfRate
            currencyStr.contains("SGD") || currencyStr.contains("S$") -> amount / liveSgdRate
            currencyStr.contains("AED") -> amount / liveAedRate
            currencyStr.contains("CNY") -> amount / liveCnyRate
            else -> amount
        }
    }

    fun convert(amount: Double, fromCurrencyStr: String, toCurrencyStr: String): Double {
        val eurAmount = convertToEUR(amount, fromCurrencyStr)
        return when {
            toCurrencyStr.contains("USD") || (toCurrencyStr.contains("$") && !toCurrencyStr.contains("CA$") && !toCurrencyStr.contains("A$") && !toCurrencyStr.contains("S$")) -> eurAmount * liveUsdRate
            toCurrencyStr.contains("GBP") || toCurrencyStr.contains("£") -> eurAmount * liveGbpRate
            toCurrencyStr.contains("INR") || toCurrencyStr.contains("₹") -> eurAmount * liveInrRate
            toCurrencyStr.contains("CAD") || toCurrencyStr.contains("CA$") -> eurAmount * liveCadRate
            toCurrencyStr.contains("AUD") || toCurrencyStr.contains("A$") -> eurAmount * liveAudRate
            toCurrencyStr.contains("JPY") || toCurrencyStr.contains("¥") -> eurAmount * liveJpyRate
            toCurrencyStr.contains("CHF") -> eurAmount * liveChfRate
            toCurrencyStr.contains("SGD") || toCurrencyStr.contains("S$") -> eurAmount * liveSgdRate
            toCurrencyStr.contains("AED") -> eurAmount * liveAedRate
            toCurrencyStr.contains("CNY") -> eurAmount * liveCnyRate
            else -> eurAmount
        }
    }
}

object ExpenseCategories {
    const val FOOD = "Food & Dining"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills & Utilities"
    const val ENTERTAINMENT = "Entertainment"
    const val OTHER = "Other"
    const val INCOME = "Income"
    const val SETTLEMENT = "Settlement"

    val all = listOf(FOOD, TRANSPORT, SHOPPING, BILLS, ENTERTAINMENT, OTHER, INCOME)
}

object DateUtils {
    private val dateFormats = listOf(
        java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy, h:mm a", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.US)
    )

    private val outputDateFormat = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", java.util.Locale.US)
    private val fullDateFormat = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy, h:mm a", java.util.Locale.US)

    private fun extractTime(input: String): String? {
        val timeMatcher = java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}\\s*(?:AM|PM)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input)
        return if (timeMatcher.find()) timeMatcher.group(0) else null
    }

    fun normalizeExpenseDate(dateInput: String): String {
        val trimmed = dateInput.trim()
        val now = java.time.LocalDateTime.now()
        val timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US))
        val extractedTime = extractTime(trimmed) ?: timeStr

        if (trimmed.isBlank() || trimmed.equals("Today", ignoreCase = true) || trimmed.startsWith("Today,", ignoreCase = true)) {
            val datePart = now.format(outputDateFormat)
            return "$datePart, $extractedTime"
        }
        if (trimmed.equals("Yesterday", ignoreCase = true) || trimmed.startsWith("Yesterday,", ignoreCase = true)) {
            val datePart = now.minusDays(1).format(outputDateFormat)
            return "$datePart, $extractedTime"
        }

        for (formatter in dateFormats) {
            try {
                val dt = java.time.LocalDateTime.parse(trimmed, formatter)
                return dt.format(fullDateFormat)
            } catch (_: Exception) {}
            try {
                val ld = java.time.LocalDate.parse(trimmed, formatter)
                return "${ld.format(outputDateFormat)}, $extractedTime"
            } catch (_: Exception) {}
        }

        try {
            val shortPattern = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)
            val monthDay = java.time.MonthDay.parse(trimmed.take(6).trim(), shortPattern)
            val ld = monthDay.atYear(now.year)
            return "${ld.format(outputDateFormat)}, $extractedTime"
        } catch (_: Exception) {}

        return "${now.format(outputDateFormat)}, $timeStr"
    }

    fun parseExpenseDate(dateStr: String, updatedAt: Long = 0L): java.time.LocalDate {
        val trimmed = dateStr.trim()
        if (trimmed.isBlank()) {
            return if (updatedAt > 0L) {
                java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            } else java.time.LocalDate.now()
        }

        if (trimmed.startsWith("Today", ignoreCase = true)) {
            return if (updatedAt > 0L) {
                java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            } else java.time.LocalDate.now()
        }

        if (trimmed.startsWith("Yesterday", ignoreCase = true)) {
            return if (updatedAt > 0L) {
                java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().minusDays(1)
            } else java.time.LocalDate.now().minusDays(1)
        }

        for (formatter in dateFormats) {
            try {
                return java.time.LocalDate.parse(trimmed, formatter)
            } catch (_: Exception) {}
            try {
                return java.time.LocalDateTime.parse(trimmed, formatter).toLocalDate()
            } catch (_: Exception) {}
        }

        try {
            val shortPattern = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)
            val monthDay = java.time.MonthDay.parse(trimmed.take(6).trim(), shortPattern)
            val year = if (updatedAt > 0L) {
                java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).year
            } else java.time.LocalDate.now().year
            return monthDay.atYear(year)
        } catch (_: Exception) {}

        return if (updatedAt > 0L) {
            java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } else java.time.LocalDate.now()
    }

    fun formatDisplayDate(dateStr: String, updatedAt: Long = 0L, todayOverride: java.time.LocalDate? = null): String {
        val parsedDate = parseExpenseDate(dateStr, updatedAt)
        val today = todayOverride ?: java.time.LocalDate.now()
        val timeStr = extractTime(dateStr)
        val timePart = if (timeStr != null) ", $timeStr" else ""

        return when (parsedDate) {
            today -> "Today$timePart"
            today.minusDays(1) -> "Yesterday"
            else -> parsedDate.format(outputDateFormat)
        }
    }

    fun matchesDateRange(dateStr: String, updatedAt: Long = 0L, selectedRange: String): Boolean {
        if (selectedRange.isBlank() || selectedRange == "All") return true
        val parsedDate = parseExpenseDate(dateStr, updatedAt)
        val today = java.time.LocalDate.now()

        return when (selectedRange) {
            "This Month" -> parsedDate.year == today.year && parsedDate.month == today.month
            "Last 30 Days" -> !parsedDate.isBefore(today.minusDays(30)) && !parsedDate.isAfter(today)
            "This Year" -> parsedDate.year == today.year
            else -> true
        }
    }
}

object SampleData {
    val initialExpenses = listOf(
        Expense(
            title = "Grocery Shopping",
            amount = 84.50,
            category = ExpenseCategories.FOOD,
            date = "Today",
            description = "Weekly organic groceries from Whole Foods",
            paidBy = "You",
            currency = Currencies.EUR
        ),
        Expense(
            title = "Uber Ride",
            amount = 22.00,
            category = ExpenseCategories.TRANSPORT,
            date = "Yesterday",
            description = "Ride to downtown office",
            paidBy = "You",
            currency = Currencies.EUR
        ),
        Expense(
            title = "Electric Bill",
            amount = 115.20,
            category = ExpenseCategories.BILLS,
            date = "Oct 24",
            description = "Monthly utility bill split with roommates",
            paidBy = "Roommate",
            currency = Currencies.EUR
        ),
        Expense(
            title = "Movie Night",
            amount = 35.00,
            category = ExpenseCategories.ENTERTAINMENT,
            date = "Oct 22",
            description = "IMAX tickets & popcorn",
            paidBy = "You",
            currency = Currencies.EUR
        ),
        Expense(
            title = "New Headphones",
            amount = 149.99,
            category = ExpenseCategories.SHOPPING,
            date = "Oct 20",
            description = "Wireless noise cancelling headphones",
            paidBy = "You",
            currency = Currencies.EUR
        )
    )

    val initialBudgets = mapOf(
        ExpenseCategories.FOOD to 400.0,
        ExpenseCategories.TRANSPORT to 200.0,
        ExpenseCategories.SHOPPING to 300.0,
        ExpenseCategories.BILLS to 250.0,
        ExpenseCategories.ENTERTAINMENT to 150.0,
        ExpenseCategories.OTHER to 100.0
    )

    val initialIncomes = listOf(
        com.finance.splitup.data.db.IncomeEntity(
            title = "Monthly Salary",
            amount = 3500.0,
            source = "Employer",
            date = "1st of month",
            currency = Currencies.EUR
        ),
        com.finance.splitup.data.db.IncomeEntity(
            title = "Freelance Project",
            amount = 650.0,
            source = "Client Work",
            date = "15th of month",
            currency = Currencies.EUR
        )
    )
}
