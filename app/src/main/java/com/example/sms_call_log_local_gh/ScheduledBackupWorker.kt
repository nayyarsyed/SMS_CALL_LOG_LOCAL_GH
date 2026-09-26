package com.example.sms_call_log_local_gh

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ScheduledBackupWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {
    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            BackupFileWriter.PREFS,
            Context.MODE_PRIVATE
        )
        if (!preferences.getBoolean(BackupScheduler.KEY_ENABLED, false)) return Result.success()

        BackupCoordinator(applicationContext).runBackup()
        BackupScheduler.enqueueNext(applicationContext)
        return Result.success()
    }
}
