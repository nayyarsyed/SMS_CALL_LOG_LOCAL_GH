package com.example.sms_call_log_local_gh

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackupScheduler {
    const val WORK_NAME = "scheduled-incremental-backup"
    const val KEY_ENABLED = "schedule_enabled"
    const val KEY_FREQUENCY = "schedule_frequency"
    const val KEY_HOUR = "schedule_hour"
    const val KEY_MINUTE = "schedule_minute"
    const val KEY_WEEKDAYS = "schedule_weekdays"
    const val KEY_MONTH_DAY = "schedule_month_day"

    fun configure(context: Context, enabled: Boolean) {
        val preferences = context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            enqueue(context, ExistingWorkPolicy.REPLACE)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    fun enqueueNext(context: Context) {
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val delay = (nextRunMillis(context) - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledBackupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun summary(context: Context): String {
        val preferences = context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_ENABLED, false)) return "Disabled"
        val frequency = preferences.getString(KEY_FREQUENCY, "Daily") ?: "Daily"
        return "$frequency · next run ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(nextRunMillis(context)))}"
    }

    private fun nextRunMillis(context: Context): Long {
        val preferences = context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
        val frequency = preferences.getString(KEY_FREQUENCY, "Daily") ?: "Daily"
        val hour = preferences.getInt(KEY_HOUR, 2)
        val minute = preferences.getInt(KEY_MINUTE, 0)
        val now = Calendar.getInstance()
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (frequency) {
            "Hourly" -> {
                candidate.set(Calendar.MINUTE, 0)
                if (!candidate.after(now)) candidate.add(Calendar.HOUR_OF_DAY, 1)
            }
            "Daily" -> if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_MONTH, 1)
            "Weekly" -> {
                val savedMask = preferences.getInt(KEY_WEEKDAYS, 1 shl Calendar.MONDAY)
                val mask = if (savedMask == 0) 1 shl Calendar.MONDAY else savedMask
                for (offset in 0..7) {
                    val day = (now.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_MONTH, offset)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if ((mask and (1 shl day.get(Calendar.DAY_OF_WEEK))) != 0 && day.after(now)) {
                        return day.timeInMillis
                    }
                }
                candidate.add(Calendar.DAY_OF_MONTH, 7)
            }
            "Monthly" -> {
                val selected = preferences.getInt(KEY_MONTH_DAY, 1)
                candidate.set(Calendar.DAY_OF_MONTH, minOf(selected, candidate.getActualMaximum(Calendar.DAY_OF_MONTH)))
                if (!candidate.after(now)) {
                    candidate.set(Calendar.DAY_OF_MONTH, 1)
                    candidate.add(Calendar.MONTH, 1)
                    candidate.set(Calendar.DAY_OF_MONTH, minOf(selected, candidate.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
            }
        }
        return candidate.timeInMillis
    }
}
