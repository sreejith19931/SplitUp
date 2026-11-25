package com.finance.splitup.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.finance.splitup.MainActivity
import com.finance.splitup.data.db.AppDatabase
import com.finance.splitup.data.repository.OfflineFirstFinanceRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.firstOrNull

/**
 * Applies an inline Approve/Decline action from the notification shade.
 * Runs inside WorkManager so database + network work is not bound by the
 * 10-second BroadcastReceiver window.
 */
class ApprovalActionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ApprovalActionWorker"
        const val KEY_ACTION = "action"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val ACTION_APPROVE = "ACTION_APPROVE_REQUEST"
        const val ACTION_DECLINE = "ACTION_DECLINE_REQUEST"
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
        val reqId = inputData.getString(KEY_REQUEST_ID) ?: return Result.success()
        val notifId = inputData.getInt(KEY_NOTIFICATION_ID, -1)

        return try {
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            if (notifId != -1) {
                notificationManager.cancel(notifId)
            }

            val database = AppDatabase.getDatabase(applicationContext)
            val dao = database.financeDao()
            val repository = OfflineFirstFinanceRepository(dao)

            val req = dao.getApprovalRequestById(reqId)
                ?: dao.getPendingApprovalRequestsForOwner().firstOrNull()?.find { it.id == reqId }
            if (req == null) return Result.success()

            val groupEntity = dao.getGroupById(req.groupId) ?: return Result.success()
            val group = groupEntity.toDomain()

            // Security guard: only the group owner may approve/decline via the
            // notification shade, even if a stale request row is present.
            if (!isCurrentUserGroupOwner(group)) {
                Log.w(TAG, "Blocked approval action by non-owner for group ${group.id}")
                return Result.success()
            }

            val clickIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_target_screen", "group_detail")
                putExtra("extra_group_id", req.groupId)
            }
            val clickPendingIntent = PendingIntent.getActivity(
                applicationContext,
                System.currentTimeMillis().toInt(),
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            when (action) {
                ACTION_APPROVE -> {
                    dao.updateApprovalRequestStatus(req.id, "APPROVED")
                    CloudSyncManager.updateApprovalRequestStatusInCloud(req.id, "APPROVED")

                    when (req.requestType) {
                        "SETTLEMENT_RECORD" -> {
                            val expense = kotlinx.serialization.json.Json.decodeFromString(
                                com.finance.splitup.data.GroupExpense.serializer(), req.payloadJson
                            )
                            val updatedExpenses = listOf(expense) + group.expenses.filter { it.id != expense.id }
                            val updatedGroup = group.copy(expenses = updatedExpenses, updatedAt = System.currentTimeMillis())
                            repository.insertGroup(updatedGroup)
                            CloudSyncManager.syncGroupToCloud(updatedGroup, repository, applicationContext)
                        }
                        "EXPENSE_EDIT", "EXPENSE_ADD" -> {
                            val expense = kotlinx.serialization.json.Json.decodeFromString(
                                com.finance.splitup.data.GroupExpense.serializer(), req.payloadJson
                            )
                            val updatedExpenses = if (group.expenses.any { it.id == expense.id }) {
                                group.expenses.map { if (it.id == expense.id) expense else it }
                            } else {
                                listOf(expense) + group.expenses
                            }
                            val updatedGroup = group.copy(expenses = updatedExpenses, updatedAt = System.currentTimeMillis())
                            repository.insertGroup(updatedGroup)
                            CloudSyncManager.syncGroupToCloud(updatedGroup, repository, applicationContext)
                        }
                        "EXPENSE_DELETE" -> {
                            val updatedExpenses = group.expenses.filter { it.id != req.targetId }
                            val updatedGroup = group.copy(expenses = updatedExpenses, updatedAt = System.currentTimeMillis())
                            repository.insertGroup(updatedGroup)
                            CloudSyncManager.syncGroupToCloud(updatedGroup, repository, applicationContext)
                            CloudSyncManager.deleteGroupExpenseFromCloud(req.groupId, req.targetId)
                        }
                    }

                    notificationManager.notify(
                        System.currentTimeMillis().toInt(),
                        NotificationCompat.Builder(applicationContext, SplitUpMessagingService.CHANNEL_GROUP_APPROVALS)
                            .setSmallIcon(com.finance.splitup.R.mipmap.ic_launcher_round)
                            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(applicationContext.resources, com.finance.splitup.R.mipmap.ic_launcher_round))
                            .setContentTitle("Request Approved! ✅")
                            .setContentText("Approved ${req.requestedBy}'s request in '${req.groupName}'")
                            .setAutoCancel(true)
                            .setContentIntent(clickPendingIntent)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .build()
                    )
                }
                ACTION_DECLINE -> {
                    dao.updateApprovalRequestStatus(req.id, "REJECTED")
                    CloudSyncManager.updateApprovalRequestStatusInCloud(req.id, "REJECTED")

                    notificationManager.notify(
                        System.currentTimeMillis().toInt(),
                        NotificationCompat.Builder(applicationContext, SplitUpMessagingService.CHANNEL_GROUP_APPROVALS)
                            .setSmallIcon(com.finance.splitup.R.mipmap.ic_launcher_round)
                            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(applicationContext.resources, com.finance.splitup.R.mipmap.ic_launcher_round))
                            .setContentTitle("Request Declined ❌")
                            .setContentText("Declined ${req.requestedBy}'s request in '${req.groupName}'")
                            .setAutoCancel(true)
                            .setContentIntent(clickPendingIntent)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .build()
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply approval action for $reqId", e)
            Result.retry()
        }
    }

    /** Mirrors FinanceViewModel.isUserMatch/isGroupOwner using only FirebaseAuth. */
    private fun isCurrentUserGroupOwner(group: com.finance.splitup.data.Group): Boolean {
        if (group.createdBy.isBlank() || group.createdBy.equals("You", ignoreCase = true)) return true
        val user = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null } ?: return false
        val selfName = (user.displayName?.ifBlank { null }) ?: (user.email?.substringBefore("@")) ?: return false
        val selfEmail = user.email?.lowercase() ?: ""

        val mLower = group.createdBy.lowercase().trim()
        val sNameLower = selfName.lowercase().trim()
        if (mLower == sNameLower || mLower == sNameLower.substringBefore(" ")) return true
        if (selfEmail.isNotBlank()) {
            if (mLower == selfEmail || (mLower == selfEmail.substringBefore("@") && sNameLower.contains(mLower))) return true
        }
        return false
    }
}
