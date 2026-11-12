package com.finance.splitup.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportImporter {

    fun exportExpensesToCsv(context: Context, expenses: List<Expense>): File {
        val fileName = "SplitUp_Personal_Expenses_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
        val file = File(context.cacheDir, fileName)
        
        val sb = StringBuilder()
        sb.append("ID,Title,Amount,Category,Date,PaidBy,Currency,PaymentMethod,Description\n")

        for (exp in expenses) {
            val id = escapeCsv(exp.id)
            val title = escapeCsv(exp.title)
            val amount = String.format(Locale.US, "%.2f", exp.amount)
            val category = escapeCsv(exp.category)
            val date = escapeCsv(exp.date)
            val paidBy = escapeCsv(exp.paidBy)
            val currency = escapeCsv(exp.currency)
            val method = escapeCsv(exp.paymentMethod)
            val desc = escapeCsv(exp.description)
            sb.append("$id,$title,$amount,$category,$date,$paidBy,$currency,$method,$desc\n")
        }

        FileOutputStream(file).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun exportGroupToCsv(context: Context, group: Group): File {
        val fileName = "SplitUp_${group.name.replace("\\s+".toRegex(), "_")}_Expenses_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
        val file = File(context.cacheDir, fileName)

        val sb = StringBuilder()
        sb.append("ID,Group,Title,Amount,PaidBy,Date,SplitAmong,Currency,Category\n")

        for (exp in group.expenses) {
            val id = escapeCsv(exp.id)
            val groupName = escapeCsv(group.name)
            val title = escapeCsv(exp.title)
            val amount = String.format(Locale.US, "%.2f", exp.amount)
            val paidBy = escapeCsv(exp.paidBy)
            val date = escapeCsv(exp.date)
            val splitAmong = escapeCsv(exp.splitAmong.joinToString("; "))
            val currency = escapeCsv(exp.currency)
            val category = escapeCsv(exp.category)
            sb.append("$id,$groupName,$title,$amount,$paidBy,$date,$splitAmong,$currency,$category\n")
        }

        FileOutputStream(file).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun parseExpensesFromCsv(csvText: String): List<Expense> {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.size <= 1) return emptyList()

        val parsedExpenses = mutableListOf<Expense>()
        for (i in 1 until lines.size) {
            val tokens = parseCsvLine(lines[i])
            if (tokens.size >= 3) {
                val title = if (tokens[1].isNotBlank()) tokens[1] else tokens[0]
                val amountStr = tokens[2]
                val amount = amountStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                val category = if (tokens.size > 3 && tokens[3].isNotBlank()) tokens[3] else ExpenseCategories.OTHER
                val date = if (tokens.size > 4 && tokens[4].isNotBlank()) tokens[4] else "Today"
                val paidBy = if (tokens.size > 5 && tokens[5].isNotBlank()) tokens[5] else "You"
                val currency = if (tokens.size > 6 && tokens[6].isNotBlank()) tokens[6] else Currencies.EUR
                val paymentMethod = if (tokens.size > 7 && tokens[7].isNotBlank()) tokens[7] else "Cash"
                val desc = if (tokens.size > 8) tokens[8] else ""

                if (amount > 0.0 || title.isNotBlank()) {
                    parsedExpenses.add(
                        Expense(
                            title = title,
                            amount = amount,
                            category = category,
                            date = date,
                            paidBy = paidBy,
                            currency = currency,
                            paymentMethod = paymentMethod,
                            description = desc
                        )
                    )
                }
            }
        }
        return parsedExpenses
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else {
                        tokens.add(sb.toString().trim())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
