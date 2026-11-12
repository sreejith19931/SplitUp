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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SplitUpMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SplitUpMessagingService"
        const val CHANNEL_ID = "splitup_fcm_notifications"

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

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Group & Cloud Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Instant push notifications for group expenses and updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            @android.annotation.SuppressLint("MissingPermission")
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show FCM notification", e)
        }
    }
}
