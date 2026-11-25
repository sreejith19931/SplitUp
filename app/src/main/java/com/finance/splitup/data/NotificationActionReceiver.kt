package com.finance.splitup.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Thin BroadcastReceiver for the inline Approve/Decline actions in the
 * notification shade. It only cancels the notification and enqueues
 * [ApprovalActionWorker] — the actual approval work (database mutation +
 * cloud sync) must not run inside onReceive/goAsync because network calls
 * can exceed the 10-second broadcast window.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reqId = intent.getStringExtra("request_id") ?: return
        val notifId = intent.getIntExtra("notification_id", -1)

        // Cancel the source notification immediately so the shade feels responsive.
        if (notifId != -1) {
            NotificationManagerCompat.from(context).cancel(notifId)
        }

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ApprovalActionWorker>()
                .setInputData(
                    workDataOf(
                        ApprovalActionWorker.KEY_ACTION to action,
                        ApprovalActionWorker.KEY_REQUEST_ID to reqId,
                        ApprovalActionWorker.KEY_NOTIFICATION_ID to notifId
                    )
                )
                .build()
        )
    }
}
