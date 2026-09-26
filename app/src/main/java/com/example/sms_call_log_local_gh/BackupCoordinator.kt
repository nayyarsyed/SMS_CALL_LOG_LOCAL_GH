package com.example.sms_call_log_local_gh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException

class BackupCoordinator(private val context: Context) {
    private val database = BackupDatabase(context)

    fun runBackup(): BackupRunResult = synchronized(RUN_LOCK) {
        runBackupLocked()
    }

    private fun runBackupLocked(): BackupRunResult {
        val failures = mutableListOf<String>()
        val formats = selectedFormats()
        val reader = DeviceDataReader(context)

        backupKind(
            kind = BackupKind.SMS,
            permission = Manifest.permission.READ_SMS,
            read = reader::readSms,
            formats = formats,
            failures = failures
        )
        backupKind(
            kind = BackupKind.CALL,
            permission = Manifest.permission.READ_CALL_LOG,
            read = reader::readCalls,
            formats = formats,
            failures = failures
        )

        val preferences = context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        preferences.edit().apply {
            if (failures.isEmpty()) {
                putLong(KEY_LAST_SUCCESS, now)
                remove(KEY_LAST_FAILURE_TIME)
                remove(KEY_LAST_FAILURE_REASON)
            } else {
                putLong(KEY_LAST_FAILURE_TIME, now)
                putString(KEY_LAST_FAILURE_REASON, failures.joinToString("; "))
            }
        }.apply()

        return BackupRunResult(
            smsCount = database.count(BackupKind.SMS),
            callCount = database.count(BackupKind.CALL),
            failures = failures
        )
    }

    private fun backupKind(
        kind: BackupKind,
        permission: String,
        read: () -> List<BackupEntry>,
        formats: List<BackupFormat>,
        failures: MutableList<String>
    ) {
        val sourceFailures = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            val reason = "${kind.label()}: permission denied."
            Log.e(TAG, reason)
            failures += reason
            sourceFailures += reason
            recordSourceFailure(kind, sourceFailures)
            return
        }
        val entries = try {
            read()
        } catch (exception: SecurityException) {
            val reason = "${kind.label()}: permission denied (${exception.message ?: "access blocked"})."
            Log.e(TAG, reason, exception)
            failures += reason
            sourceFailures += reason
            recordSourceFailure(kind, sourceFailures)
            return
        } catch (exception: IOException) {
            val reason = "${kind.label()}: could not read device data (${exception.message})."
            Log.e(TAG, reason, exception)
            failures += reason
            sourceFailures += reason
            recordSourceFailure(kind, sourceFailures)
            return
        }

        entries.forEach(database::addIfNew)
        val snapshot = database.entries(kind)
        var anyFormatWritten = false
        if (formats.isEmpty()) {
            val reason = "${kind.label()}: select at least one backup format in Settings."
            failures += reason
            sourceFailures += reason
        }
        formats.forEach { format ->
            try {
                BackupFileWriter(context).write(kind, format, snapshot)
                anyFormatWritten = true
            } catch (exception: IOException) {
                val reason = "${kind.label()} ${format.name}: ${exception.message ?: "file write failed"}"
                Log.e(TAG, reason, exception)
                failures += reason
                sourceFailures += reason
            } catch (exception: SecurityException) {
                val reason = "${kind.label()} ${format.name}: access to the backup folder was denied."
                Log.e(TAG, reason, exception)
                failures += reason
                sourceFailures += reason
            }
        }
        if (anyFormatWritten) {
            database.markBackedUp(kind)
            context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong("${kind.databaseValue}_last_success", System.currentTimeMillis())
                .apply()
        }
        if (sourceFailures.isNotEmpty()) {
            recordSourceFailure(kind, sourceFailures)
        } else if (anyFormatWritten) {
            context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("${kind.databaseValue}_last_failure_time")
                .remove("${kind.databaseValue}_last_failure_reason")
                .apply()
        }
    }

    private fun recordSourceFailure(kind: BackupKind, reasons: List<String>) {
        context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("${kind.databaseValue}_last_failure_time", System.currentTimeMillis())
            .putString("${kind.databaseValue}_last_failure_reason", reasons.joinToString("; "))
            .apply()
    }

    private fun selectedFormats(): List<BackupFormat> {
        val preferences = context.getSharedPreferences(BackupFileWriter.PREFS, Context.MODE_PRIVATE)
        return BackupFormat.entries.filter { format ->
            preferences.getBoolean("format_${format.name}", format == BackupFormat.JSON)
        }
    }

    companion object {
        private val RUN_LOCK = Any()
        private const val TAG = "BackupCoordinator"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_LAST_FAILURE_TIME = "last_failure_time"
        const val KEY_LAST_FAILURE_REASON = "last_failure_reason"
    }
}

private fun BackupKind.label(): String = if (this == BackupKind.SMS) "SMS" else "Call Log"
