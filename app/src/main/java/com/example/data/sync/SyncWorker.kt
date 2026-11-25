package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.NoteaApp
import java.util.concurrent.TimeUnit

/** Uploads the local change queue. Retries with exponential backoff until everything is pushed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.failure()
        val container = (applicationContext as NoteaApp).container
        // Never upload another account's queue under the wrong session.
        if (container.currentUid() != uid || !container.syncAvailable(uid)) return Result.success()
        if (container.syncEngine.pushAll(uid)) return Result.success()
        com.example.core.Diagnostics.log(applicationContext, "sync", "Upload incomplete (attempt ${runAttemptCount + 1}); will retry")
        return if (runAttemptCount < 12) Result.retry() else Result.failure()
    }

    companion object {
        private const val KEY_UID = "uid"

        fun enqueue(context: Context, uid: String, delaySeconds: Long = 2) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_UID to uid))
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("notea-sync-$uid", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
