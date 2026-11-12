package com.finance.splitup.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.finance.splitup.data.db.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class RecurringExpenseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val recurringList = database.financeDao().getAllRecurringExpenses().firstOrNull() ?: emptyList()

            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)

            for (item in recurringList) {
                if (item.autoAdd && item.nextDueDate.isNotBlank()) {
                    try {
                        val dueDate = LocalDate.parse(item.nextDueDate.trim(), formatter)
                        // If today is on or after the due date, auto-add expense and advance next due date
                        if (!today.isBefore(dueDate)) {
                            val expenseEntity = com.finance.splitup.data.db.ExpenseEntity(
                                id = UUID.randomUUID().toString(),
                                title = item.title,
                                amount = item.amount,
                                category = item.category,
                                date = item.nextDueDate,
                                description = "Automated Recurring Expense (${item.frequency})",
                                paidBy = "You",
                                currency = item.currency,
                                paymentMethod = "Auto Debit"
                            )
                            database.financeDao().insertExpense(expenseEntity)

                            // Advance next due date based on frequency
                            val nextDate = when (item.frequency.lowercase(Locale.ROOT)) {
                                "weekly" -> dueDate.plusWeeks(1)
                                "yearly" -> dueDate.plusYears(1)
                                else -> dueDate.plusMonths(1) // Monthly or default
                            }

                            val updatedItem = item.copy(
                                nextDueDate = nextDate.format(formatter),
                                updatedAt = System.currentTimeMillis(),
                                isDirty = true
                            )
                            database.financeDao().upsertRecurringExpense(updatedItem)

                            try {
                                val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        applicationContext,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                } else true

                                if (hasPermission) {
                                    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                    if (notificationManager != null) {
                                        val channel = android.app.NotificationChannel(
                                            "splitup_recurring_bills",
                                            "Subscriptions & Recurring Bills",
                                            android.app.NotificationManager.IMPORTANCE_DEFAULT
                                        ).apply { description = "Notifications for auto-logged subscriptions and bill reminders" }
                                        notificationManager.createNotificationChannel(channel)
                                    }

                                    val intent = android.content.Intent(applicationContext, com.finance.splitup.MainActivity::class.java).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra("extra_target_screen", "personal_expenses")
                                        putExtra("extra_category", item.category)
                                    }
                                    val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                    } else {
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                    }
                                    val pendingIntent = android.app.PendingIntent.getActivity(
                                        applicationContext,
                                        System.currentTimeMillis().toInt(),
                                        intent,
                                        flags
                                    )

                                    val builder = androidx.core.app.NotificationCompat.Builder(applicationContext, "splitup_recurring_bills")
                                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                                        .setContentTitle("Recurring Expense Logged 🔄")
                                        .setContentText("Auto-logged '${item.title}' (${item.currency} ${item.amount}) for due date ${item.nextDueDate}.")
                                        .setAutoCancel(true)
                                        .setContentIntent(pendingIntent)
                                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

                                    @android.annotation.SuppressLint("MissingPermission")
                                    androidx.core.app.NotificationManagerCompat.from(applicationContext)
                                        .notify(System.currentTimeMillis().toInt(), builder.build())
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RecurringExpenseWorker", "Failed to parse due date for ${item.title}: ${item.nextDueDate}", e)
                    }
                }
            }
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("RecurringExpenseWorker", "Worker failed", e)
            ListenableWorker.Result.failure()
        }
    }
}
