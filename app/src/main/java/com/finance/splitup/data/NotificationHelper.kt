package com.finance.splitup.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.finance.splitup.MainActivity
import com.finance.splitup.R

/**
 * System notification posting, extracted from FinanceViewModel so the
 * viewmodel stays focused on state and business logic. Channel ids are
 * mirrored as constants in FinanceViewModel's companion for compatibility.
 */
object NotificationHelper {
    const val CHANNEL_GROUP_ACTIVITY = "splitup_group_activity"
    const val CHANNEL_FINANCIAL_ALERTS = "splitup_financial_alerts"
    const val CHANNEL_RECURRING_BILLS = "splitup_recurring_bills"
    const val CHANNEL_PAYMENT_DETECTION = "splitup_payment_detection"
    const val CHANNEL_EXPORTS_INSIGHTS = "splitup_exports_insights"
    const val CHANNEL_SECURITY_ALERTS = "splitup_security_alerts"

    fun createAllNotificationChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_GROUP_ACTIVITY,
                    "Group Activity & Shared Expenses",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new group expenses, settlements, and member updates"
                    enableVibration(true)
                    enableLights(true)
                },
                NotificationChannel(
                    CHANNEL_FINANCIAL_ALERTS,
                    "Budget & Spending Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when budget thresholds are reached or high-value expenses are logged"
                    enableVibration(true)
                    enableLights(true)
                },
                NotificationChannel(
                    CHANNEL_RECURRING_BILLS,
                    "Subscriptions & Recurring Bills",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for auto-logged subscriptions and bill reminders"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_PAYMENT_DETECTION,
                    "Payment Auto-Detector",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Prompts when bank/wallet transaction notifications are detected"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_EXPORTS_INSIGHTS,
                    "Reports & AI Insights",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Notifications when PDF reports are generated or AI advice is ready" },
                NotificationChannel(
                    CHANNEL_SECURITY_ALERTS,
                    "Account & Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for password updates and account security changes"
                    enableVibration(true)
                }
            )
            for (ch in channels) {
                manager.createNotificationChannel(ch)
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun post(
        context: Context,
        title: String,
        message: String,
        channelId: String = CHANNEL_FINANCIAL_ALERTS,
        targetScreen: String? = null,
        groupId: String? = null,
        category: String? = null,
        pendingIntent: PendingIntent? = null,
        requestId: String? = null
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                createAllNotificationChannels(notificationManager)
            }

            val finalTargetScreen = targetScreen ?: when {
                !groupId.isNullOrBlank() -> "group_detail"
                !category.isNullOrBlank() -> "personal_expenses"
                channelId == CHANNEL_GROUP_ACTIVITY -> "groups"
                channelId == CHANNEL_SECURITY_ALERTS -> "settings"
                channelId == CHANNEL_FINANCIAL_ALERTS -> "personal_expenses"
                else -> "dashboard"
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_target_screen", finalTargetScreen)
                if (!groupId.isNullOrBlank()) putExtra("extra_group_id", groupId)
                if (!category.isNullOrBlank()) putExtra("extra_category", category)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val effectivePendingIntent = pendingIntent ?: PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt() + (0..1000).random(),
                intent,
                flags
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(effectivePendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            val notificationId = System.currentTimeMillis().toInt() + (0..1000).random()

            if ((channelId == CHANNEL_GROUP_ACTIVITY || title.contains("Approval Needed", ignoreCase = true)) && !requestId.isNullOrBlank()) {
                val approveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = "ACTION_APPROVE_REQUEST"
                    putExtra("request_id", requestId)
                    if (!groupId.isNullOrBlank()) putExtra("group_id", groupId)
                    putExtra("notification_id", notificationId)
                }
                val approvePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (System.currentTimeMillis() + 1).toInt(),
                    approveIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_input_add,
                        "Approve",
                        approvePendingIntent
                    )
                )

                val declineIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = "ACTION_DECLINE_REQUEST"
                    putExtra("request_id", requestId)
                    if (!groupId.isNullOrBlank()) putExtra("group_id", groupId)
                    putExtra("notification_id", notificationId)
                }
                val declinePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (System.currentTimeMillis() + 2).toInt(),
                    declineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_delete,
                        "Decline",
                        declinePendingIntent
                    )
                )
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
