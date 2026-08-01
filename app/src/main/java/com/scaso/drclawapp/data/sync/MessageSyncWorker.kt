package com.scaso.drclawapp.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.entity.SyncState
import com.scaso.drclawapp.data.websocket.GatewayClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs pending offline messages when network is available.
 * Queries all PENDING messages from Room, sends them in order via GatewayClient,
 * and marks each as SYNCED or FAILED.
 */
@HiltWorker
class MessageSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageDao: MessageDao,
    private val gatewayClient: GatewayClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pending = messageDao.getPendingMessages()
        if (pending.isEmpty()) {
            Log.i(TAG, "No pending messages to sync")
            return Result.success()
        }

        Log.i(TAG, "Syncing ${pending.size} pending messages")
        var failCount = 0

        for (message in pending) {
            try {
                val response = gatewayClient.sendMessage(message.content)
                if (response.ok) {
                    messageDao.updateSyncState(message.id, SyncState.SYNCED.name)
                    Log.d(TAG, "Synced message ${message.id}")
                } else {
                    messageDao.updateSyncState(message.id, SyncState.FAILED.name)
                    failCount++
                    Log.w(TAG, "Failed to sync message ${message.id}: server rejected")
                }
            } catch (e: Exception) {
                messageDao.updateSyncState(message.id, SyncState.FAILED.name)
                failCount++
                Log.w(TAG, "Failed to sync message ${message.id}: ${e.message}")
            }
        }

        return if (failCount > 0 && failCount == pending.size) {
            // All failed — retry with backoff
            Log.w(TAG, "All messages failed to sync, will retry")
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val TAG = "MessageSyncWorker"
        const val WORK_NAME = "message_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MessageSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            Log.i(TAG, "Enqueued sync work")
        }
    }
}
