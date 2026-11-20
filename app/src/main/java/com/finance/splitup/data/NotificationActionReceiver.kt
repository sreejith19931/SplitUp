package com.finance.splitup.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.finance.splitup.MainActivity
import com.finance.splitup.data.db.AppDatabase
import com.finance.splitup.data.repository.OfflineFirstFinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reqId = intent.getStringExtra("request_id") ?: return
        val notifId = intent.getIntExtra("notification_id", -1)

        val notificationManager = NotificationManagerCompat.from(context)
        if (notifId != -1) {
            notificationManager.cancel(notifId)
        }

        val database = AppDatabase.getDatabase(context)
        val dao = database.financeDao()
        val repository = OfflineFirstFinanceRepository(dao)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = dao.getApprovalRequestById(reqId) ?: (dao.getPendingApprovalRequestsForOwner().firstOrNull() ?: emptyList()).find { it.id == reqId }
                if (req != null) {
                    val groupEntity = dao.getGroupById(req.groupId)
                    if (groupEntity != null) {
                        val group = groupEntity.toDomain()
                        
                        val clickIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("extra_target_screen", "group_detail")
                            putExtra("extra_group_id", req.groupId)
                        }
                        val clickPendingIntent = PendingIntent.getActivity(
                            context,
                            System.currentTimeMillis().toInt(),
                            clickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        if (action == "ACTION_APPROVE_REQUEST") {
                            dao.updateApprovalRequestStatus(req.id, "APPROVED")
                            CloudSyncManager.updateApprovalRequestStatusInCloud(req.id, "APPROVED")

                            when (req.requestType) {
                                "SETTLEMENT_RECORD" -> {
                                    val expense = kotlinx.serialization.json.Json.decodeFromString(GroupExpense.serializer(), req.payloadJson)
                                    val updatedExpenses = listOf(expense) + group.expenses.filter { it.id != expense.id }
                                    val updatedGroup = group.copy(expenses = updatedExpenses, updatedAt = System.currentTimeMillis())
                                    repository.insertGroup(updatedGroup)
                                    CloudSyncManager.syncGroupToCloud(updatedGroup, repository, context)
                                }
                                "EXPENSE_EDIT", "EXPENSE_ADD" -> {
                                    val expense = kotlinx.serialization.json.Json.decodeFromString(GroupExpense.serializer(), req.payloadJson)
                                    val updatedExpenses = if (group.expenses.any { it.id == expense.id }) {
                                        group.expenses.map { if (it.id == expense.id) expense else it }
                                    } else {
                                        listOf(expense) + group.expenses
                                    }
                                    val updatedGroup = group.copy(expenses = updatedExpenses, updatedAt = System.currentTimeMillis())
                                    repository.insertGroup(updatedGroup)
                                    CloudSyncManager.syncGroupToCloud(updatedGroup, repository, context)
                                }
                                "EXPENSE_DELETE" -> {
                                    val updatedExpenses = group.expenses.filter { it.id != req.targetId }
                                    val updatedGroup = group.copy(expenses = updatedExpenses, updatedAt = System.currentTimeMillis())
                                    repository.insertGroup(updatedGroup)
                                    CloudSyncManager.syncGroupToCloud(updatedGroup, repository, context)
                                    CloudSyncManager.deleteGroupExpenseFromCloud(req.groupId, req.targetId)
                                }
                            }

                            val confBuilder = NotificationCompat.Builder(context, SplitUpMessagingService.CHANNEL_GROUP_APPROVALS)
                                .setSmallIcon(com.finance.splitup.R.mipmap.ic_launcher_round)
                                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, com.finance.splitup.R.mipmap.ic_launcher_round))
                                .setContentTitle("Request Approved! ✅")
                                .setContentText("Approved ${req.requestedBy}'s request in '${req.groupName}'")
                                .setAutoCancel(true)
                                .setContentIntent(clickPendingIntent)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                            notificationManager.notify(System.currentTimeMillis().toInt(), confBuilder.build())

                        } else if (action == "ACTION_DECLINE_REQUEST") {
                            dao.updateApprovalRequestStatus(req.id, "REJECTED")
                            CloudSyncManager.updateApprovalRequestStatusInCloud(req.id, "REJECTED")

                            val confBuilder = NotificationCompat.Builder(context, SplitUpMessagingService.CHANNEL_GROUP_APPROVALS)
                                .setSmallIcon(com.finance.splitup.R.mipmap.ic_launcher_round)
                                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, com.finance.splitup.R.mipmap.ic_launcher_round))
                                .setContentTitle("Request Declined ❌")
                                .setContentText("Declined ${req.requestedBy}'s request in '${req.groupName}'")
                                .setAutoCancel(true)
                                .setContentIntent(clickPendingIntent)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                            notificationManager.notify(System.currentTimeMillis().toInt(), confBuilder.build())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
