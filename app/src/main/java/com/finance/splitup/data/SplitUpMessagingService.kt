package com.finance.splitup.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.finance.splitup.MainActivity
import com.finance.splitup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SplitUpMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SplitUpMessagingService"
        
        // Dedicated Notification Channels
        const val CHANNEL_BUDGET_ALERTS = "splitup_budget_alerts"
        const val CHANNEL_GROUP_APPROVALS = "splitup_group_approvals"
        const val CHANNEL_PAYMENTS = "splitup_payments"
        const val CHANNEL_SUBSCRIPTIONS = "splitup_subscriptions"
        const val CHANNEL_DEFAULT = "splitup_fcm_notifications"
        const val CHANNEL_ID = CHANNEL_DEFAULT

        fun uploadTokenToFirestore() {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload FCM token to Firestore", e)
            }
        }

        fun createAllNotificationChannels(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Budget & Pacing Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when approaching or exceeding monthly category budgets"
                enableVibration(true)
                enableLights(true)
            }

            val approvalChannel = NotificationChannel(
                CHANNEL_GROUP_APPROVALS,
                "Group Approvals & Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Requests for expense changes, deletions, and member approvals"
                enableVibration(true)
                enableLights(true)
            }

            val paymentChannel = NotificationChannel(
                CHANNEL_PAYMENTS,
                "Payment & Auto-Detector Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Detected bank and payment app transactions"
            }

            val subChannel = NotificationChannel(
                CHANNEL_SUBSCRIPTIONS,
                "Subscription Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Upcoming subscription billing reminders"
            }

            val defaultChannel = NotificationChannel(
                CHANNEL_DEFAULT,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            notificationManager.createNotificationChannels(
                listOf(budgetChannel, approvalChannel, paymentChannel, subChannel, defaultChannel)
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        uploadTokenToFirestore()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Update in SplitUp"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You have a new update from your group."
        val type = remoteMessage.data["type"] ?: "DEFAULT"
        val requestId = remoteMessage.data["requestId"] ?: ""
        val groupId = remoteMessage.data["groupId"] ?: ""

        showRichNotification(title, body, type, requestId, groupId)
    }

    private fun showRichNotification(title: String, body: String, type: String, requestId: String, groupId: String) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) return

        createAllNotificationChannels(this)

        val channelId = when (type) {
            "BUDGET_ALERT" -> CHANNEL_BUDGET_ALERTS
            "APPROVAL_REQUEST" -> CHANNEL_GROUP_APPROVALS
            "PAYMENT_DETECTED" -> CHANNEL_PAYMENTS
            "SUBSCRIPTION" -> CHANNEL_SUBSCRIPTIONS
            else -> CHANNEL_DEFAULT
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigation_group_id", groupId)
            putExtra("notification_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                when (type) {
                    "BUDGET_ALERT" -> NotificationCompat.PRIORITY_HIGH
                    "SUBSCRIPTION" -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }
            )

        // Add Actionable Quick-Actions (Approve / Decline) for Group Approvals
        val notifId = System.currentTimeMillis().toInt()
        if (type == "APPROVAL_REQUEST" && requestId.isNotBlank()) {
            val approveIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = "ACTION_APPROVE_REQUEST"
                putExtra("request_id", requestId)
                putExtra("group_id", groupId)
                putExtra("notification_id", notifId)
            }
            val approvePendingIntent = PendingIntent.getBroadcast(
                this,
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

            val declineIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = "ACTION_DECLINE_REQUEST"
                putExtra("request_id", requestId)
                putExtra("group_id", groupId)
                putExtra("notification_id", notifId)
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                this,
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

        try {
            @android.annotation.SuppressLint("MissingPermission")
            NotificationManagerCompat.from(this).notify(notifId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show rich FCM notification", e)
        }
    }
}
