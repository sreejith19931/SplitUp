package com.finance.splitup.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.finance.splitup.data.db.AppDatabase
import com.finance.splitup.data.repository.OfflineFirstFinanceRepository
import kotlinx.coroutines.flow.firstOrNull

class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CloudSyncWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = OfflineFirstFinanceRepository(database.financeDao())

            val prefs = applicationContext.getSharedPreferences("splitup_sync_prefs", Context.MODE_PRIVATE)
            var lastCheckTime = prefs.getLong("last_notification_check_time", 0L)
            if (lastCheckTime == 0L) {
                lastCheckTime = System.currentTimeMillis()
                prefs.edit().putLong("last_notification_check_time", lastCheckTime).apply()
            }

            // Capture existing local expense IDs before syncing
            val existingExpenseIds = repository.getAllExpenses().firstOrNull()?.map { it.id }?.toSet() ?: emptySet()

            CloudSyncManager.performCloudSync(repository, applicationContext)

            // Check for truly new expenses pulled from the cloud (not created locally by this user)
            val allExpenses = repository.getAllExpenses().firstOrNull() ?: emptyList()
            val trulyNewRemoteExpenses = allExpenses.filter { expense ->
                expense.id !in existingExpenseIds && expense.updatedAt > lastCheckTime
            }

            if (trulyNewRemoteExpenses.isNotEmpty()) {
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
                            "splitup_cloud_sync_notifications",
                            "Cloud Sync & Group Updates",
                            android.app.NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { description = "Notifications for new expenses and updates from group members" }
                        notificationManager.createNotificationChannel(channel)
                    }

                    val intent = android.content.Intent(applicationContext, com.finance.splitup.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        applicationContext,
                        System.currentTimeMillis().toInt(),
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    val latest = trulyNewRemoteExpenses.maxByOrNull { it.updatedAt }
                    val titleText = if (trulyNewRemoteExpenses.size == 1 && latest != null) {
                        "New Expense: ${latest.title} 💸"
                    } else {
                        "New Updates (${trulyNewRemoteExpenses.size}) in SplitUp 💸"
                    }
                    val bodyText = if (trulyNewRemoteExpenses.size == 1 && latest != null) {
                        "${latest.currency} ${latest.amount} added (${latest.paidBy})"
                    } else {
                        "New expenses or updates were synced from group members."
                    }

                    val builder = androidx.core.app.NotificationCompat.Builder(applicationContext, "splitup_cloud_sync_notifications")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(titleText)
                        .setContentText(bodyText)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

                    @android.annotation.SuppressLint("MissingPermission")
                    androidx.core.app.NotificationManagerCompat.from(applicationContext)
                        .notify(3001, builder.build())
                }
            }

            prefs.edit().putLong("last_notification_check_time", System.currentTimeMillis()).apply()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CloudSyncWorker background sync failed", e)
            Result.retry()
        }
    }
}
