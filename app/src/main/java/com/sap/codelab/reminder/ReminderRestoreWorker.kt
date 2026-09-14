package com.sap.codelab.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sap.codelab.repository.App

internal class ReminderRestoreWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as App
        return runCatching {
            app.container.reminderManager.restoreReminders()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
            }
        )
    }

    private companion object {
        const val MAX_RETRY_COUNT = 2
    }
}

internal object ReminderRestoreWork {

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderRestoreWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private const val UNIQUE_WORK_NAME = "restore-location-reminders"
}
