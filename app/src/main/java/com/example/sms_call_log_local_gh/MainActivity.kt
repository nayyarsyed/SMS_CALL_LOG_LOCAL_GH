package com.example.sms_call_log_local_gh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.card.MaterialCardView
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var contentArea: LinearLayout
    private var selectedTab = 0
    private var statusMessage = "Ready"
    private val executor = Executors.newSingleThreadExecutor()

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }.keys
        statusMessage = if (denied.isEmpty()) {
            "Permissions granted. Device data is ready."
        } else {
            "Permission denied. SMS and call-log access can be granted in Android Settings."
        }
        render()
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE).edit()
                    .putString(BackupFileWriter.KEY_TREE_URI, uri.toString())
                    .putBoolean(BackupFileWriter.KEY_DOWNLOADS_CONFIRMED, true)
                    .apply()
                statusMessage = "Backup folder selected."
            } catch (exception: SecurityException) {
                statusMessage = "Could not keep access to the selected folder: ${exception.message}"
                recordFailure(statusMessage)
            }
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && selectedTab == 0) render()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            setBackgroundColor(getColor(android.R.color.background_light))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("SMS & Call Backup", 22f, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(button("Settings") { showSettings() })
        root.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
        }
        listOf("Dashboard", "SMS", "Call Logs").forEachIndexed { index, label ->
            val tab = button(label) {
                selectedTab = index
                render()
            }
            tab.isAllCaps = false
            tab.alpha = if (selectedTab == index) 1f else 0.62f
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(6)
            })
        }
        root.addView(tabs)

        contentArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(contentArea)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        when (selectedTab) {
            0 -> renderDashboard()
            1 -> renderEntryList(BackupKind.SMS)
            else -> renderEntryList(BackupKind.CALL)
        }
    }

    private fun renderDashboard() {
        val database = BackupDatabase(this)
        val preferences = getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE)
        val directoryUri = preferences.getString(BackupFileWriter.KEY_TREE_URI, null)
        val folderName = if (directoryUri == null) {
            "Downloads/SMS_Call_Backup"
        } else {
            try {
                DocumentFile.fromTreeUri(this, Uri.parse(directoryUri))?.name ?: "Unavailable"
            } catch (exception: SecurityException) {
                Log.w("MainActivity", "Selected backup folder is no longer accessible.", exception)
                "Unavailable"
            }
        }
        val lastSuccess = preferences.getLong(BackupCoordinator.KEY_LAST_SUCCESS, 0L)
        val lastFailure = preferences.getLong(BackupCoordinator.KEY_LAST_FAILURE_TIME, 0L)
        val reason = preferences.getString(BackupCoordinator.KEY_LAST_FAILURE_REASON, "None")
        val smsFailure = preferences.getString("sms_last_failure_reason", "None")
        val callFailure = preferences.getString("call_last_failure_reason", "None")
        val schedule = BackupScheduler.summary(this)

        contentArea.addView(panel("Backup status") {
            addView(text(statusMessage, 15f, false))
            addView(text("Last successful backup: ${dateOrNever(lastSuccess)}", 14f, false))
            addView(text("Last failed backup: ${dateOrNever(lastFailure)}", 14f, false))
            addView(text("Failure reason: $reason", 14f, false))
            addView(text("SMS last successful: ${dateOrNever(preferences.getLong("sms_last_success", 0L))}", 14f, false))
            addView(text("SMS last failed: ${dateOrNever(preferences.getLong("sms_last_failure_time", 0L))} · $smsFailure", 14f, false))
            addView(text("Call Log last successful: ${dateOrNever(preferences.getLong("call_last_success", 0L))}", 14f, false))
            addView(text("Call Log last failed: ${dateOrNever(preferences.getLong("call_last_failure_time", 0L))} · $callFailure", 14f, false))
        })
        contentArea.addView(panel("Backed-up entries") {
            addView(text("SMS messages: ${database.count(BackupKind.SMS)}", 16f, true))
            addView(text("Call logs: ${database.count(BackupKind.CALL)}", 16f, true))
        })
        contentArea.addView(panel("Backup files") {
            addView(text("Folder: $folderName", 14f, false))
            BackupFormat.entries.forEach { format ->
                if (preferences.getBoolean("format_${format.name}", format == BackupFormat.JSON)) {
                    addView(text("SMS: sms_backup.${format.name.lowercase()}", 14f, false))
                    addView(text("Calls: call_log_backup.${format.name.lowercase()}", 14f, false))
                }
            }
            addView(button("Choose SD-card folder") { folderPicker.launch(null) })
            addView(button("Use Downloads/SMS_Call_Backup") {
                useDownloadsFolder()
                statusMessage = "Backup location set to Downloads/SMS_Call_Backup."
                render()
            })
        })
        contentArea.addView(panel("Automatic backup") {
            addView(text(schedule, 14f, false))
            addView(button("Schedule Backup") { startActivity(Intent(this@MainActivity, ScheduleActivity::class.java)) })
        })
        contentArea.addView(button("Run Backup Now") { runBackup() })
        contentArea.addView(button("Grant SMS and Call Log Permissions") { requestPermissionsWithExplanation() })
    }

    private fun renderEntryList(kind: BackupKind) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            if (kind == BackupKind.SMS) Manifest.permission.READ_SMS else Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        contentArea.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text(if (kind == BackupKind.SMS) "All device SMS messages" else "All device call logs", 18f, true),
                LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(button("Refresh") { loadEntries(kind) })
        })
        if (!hasPermission) {
            contentArea.addView(text("Permission is required to read this list.", 15f, false))
            contentArea.addView(button("Grant permission") { requestPermissionsWithExplanation() })
        } else {
            loadEntries(kind)
        }
    }

    private fun loadEntries(kind: BackupKind) {
        if (ContextCompat.checkSelfPermission(
                this,
                if (kind == BackupKind.SMS) Manifest.permission.READ_SMS else Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionsWithExplanation()
            return
        }
        contentArea.removeViews(1, (contentArea.childCount - 1).coerceAtLeast(0))
        contentArea.addView(text("Loading…", 15f, false))
        executor.execute {
            val entries = try {
                if (kind == BackupKind.SMS) DeviceDataReader(this).readSms()
                else DeviceDataReader(this).readCalls()
            } catch (exception: SecurityException) {
                showListError("Permission denied: ${exception.message ?: "access blocked"}", exception)
                return@execute
            } catch (exception: IOException) {
                showListError("Could not read ${if (kind == BackupKind.SMS) "SMS" else "Call Logs"}: ${exception.message}", exception)
                return@execute
            }
            runOnUiThread {
                if (selectedTab != (if (kind == BackupKind.SMS) 1 else 2)) return@runOnUiThread
                contentArea.removeViews(1, (contentArea.childCount - 1).coerceAtLeast(0))
                contentArea.addView(text("${entries.size} entries", 13f, false))
                entries.asReversed().forEach { entry -> contentArea.addView(entryCard(entry)) }
            }
        }
    }

    private fun showListError(reason: String, exception: Exception) {
        Log.e("MainActivity", reason, exception)
        runOnUiThread {
            statusMessage = reason
            if (contentArea.childCount > 1) {
                contentArea.removeViews(1, contentArea.childCount - 1)
            }
            contentArea.addView(text(reason, 15f, false))
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        }
    }

    private fun entryCard(entry: BackupEntry): MaterialCardView =
        panel(date(entry.timestamp)) {
            addView(text("${entry.number.ifBlank { "Unknown number" }} · ${entry.category}", 15f, true))
            if (entry.kind == BackupKind.SMS) {
                val body = text(entry.body.ifBlank { "(empty message)" }, 14f, false).apply {
                    maxLines = 3
                    setOnClickListener { maxLines = if (maxLines == 3) Int.MAX_VALUE else 3 }
                }
                addView(body)
                addView(text("Tap message text to expand or collapse", 12f, false))
            } else {
                addView(text("Duration: ${entry.durationSeconds} seconds", 14f, false))
            }
        }

    private fun runBackup() {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_SMS)
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_CALL_LOG)
            }
        }
        if (missing.isNotEmpty()) {
            statusMessage = "Backup failed: SMS or Call Log permission denied."
            recordFailure(statusMessage)
            requestPermissionsWithExplanation()
            return
        }
        val preferences = getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE)
        val savedTree = preferences.getString(BackupFileWriter.KEY_TREE_URI, null)
        if (savedTree != null) {
            val directory = DocumentFile.fromTreeUri(this, Uri.parse(savedTree))
            val directoryAvailable = try {
                directory != null && directory.exists() && directory.canWrite()
            } catch (_: SecurityException) {
                false
            }
            if (!directoryAvailable) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Backup folder unavailable")
                    .setMessage("The selected folder may have been removed or disconnected. Choose another folder or use Downloads/SMS_Call_Backup.")
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Choose another folder") { _, _ -> folderPicker.launch(null) }
                    .setPositiveButton("Use Downloads") { _, _ ->
                        useDownloadsFolder()
                        executeBackup()
                    }
                    .show()
                return
            }
        }
        if (preferences.getString(BackupFileWriter.KEY_TREE_URI, null) == null &&
            !preferences.getBoolean(BackupFileWriter.KEY_DOWNLOADS_CONFIRMED, false)
        ) {
            val sdCardPresent = (getSystemService(STORAGE_SERVICE) as StorageManager)
                .storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            val message = if (sdCardPresent) {
                "An SD card is available. Choose a folder on it for the primary backup location, or use Downloads/SMS_Call_Backup."
            } else {
                "No mounted SD card was detected. Backups can be saved in Downloads/SMS_Call_Backup."
            }
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choose backup storage")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use Downloads") { _, _ ->
                    useDownloadsFolder()
                    executeBackup()
                }
            if (sdCardPresent) {
                dialog.setNeutralButton("Choose SD card") { _, _ -> folderPicker.launch(null) }
            }
            dialog.show()
            return
        }
        executeBackup()
    }

    private fun executeBackup() {
        statusMessage = "Backup in progress…"
        render()
        executor.execute {
            val result = BackupCoordinator(this).runBackup()
            runOnUiThread {
                statusMessage = if (result.succeeded) {
                    "Backup completed successfully."
                } else {
                    "Backup partially failed: ${result.failures.joinToString("; ")}"
                }
                selectedTab = 0
                render()
            }
        }
    }

    private fun useDownloadsFolder() {
        getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE).edit()
            .remove(BackupFileWriter.KEY_TREE_URI)
            .putBoolean(BackupFileWriter.KEY_DOWNLOADS_CONFIRMED, true)
            .apply()
    }

    private fun recordFailure(reason: String) {
        getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE).edit()
            .putLong(BackupCoordinator.KEY_LAST_FAILURE_TIME, System.currentTimeMillis())
            .putString(BackupCoordinator.KEY_LAST_FAILURE_REASON, reason)
            .apply()
    }

    private fun requestPermissionsWithExplanation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Allow device data access")
            .setMessage("SMS permission reads messages for backup. Call Log permission reads call history. Data stays on your device and is written to the folder you select.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue") { _, _ ->
                permissionRequest.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CALL_LOG))
            }
            .show()
    }

    private fun showSettings() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(arrayOf("Backup formats", "Schedule Backup", "About")) { _, choice ->
                when (choice) {
                    0 -> showFormatSettings()
                    1 -> startActivity(Intent(this, ScheduleActivity::class.java))
                    else -> showAbout()
                }
            }
            .show()
    }

    private fun showFormatSettings() {
        val formats = BackupFormat.entries
        val preferences = getSharedPreferences(BackupFileWriter.PREFS, MODE_PRIVATE)
        val checked = formats.map { format ->
            preferences.getBoolean("format_${format.name}", format == BackupFormat.JSON)
        }.toBooleanArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Backup formats")
            .setMultiChoiceItems(formats.map { it.name }.toTypedArray(), checked) { _, index, value ->
                checked[index] = value
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                if (checked.none { it }) {
                    Toast.makeText(this, "Select at least one format.", Toast.LENGTH_LONG).show()
                } else {
                    preferences.edit().apply {
                        formats.forEachIndexed { index, format ->
                            putBoolean("format_${format.name}", checked[index])
                        }
                    }.apply()
                    render()
                }
            }
            .show()
    }

    private fun showAbout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About SMS & Call Backup")
            .setMessage(
                "Version 1.0\n\nBacks up SMS messages and call history to user-selected storage in JSON, XML, or CSV.\n\nDeveloper/support: Placeholder\n\nPermissions: Read SMS and Call Log are used to list and back up device records. Folder access is granted through Android's system folder picker. No broad storage permission is used."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun panel(title: String, content: (LinearLayout.() -> Unit)? = null): MaterialCardView =
        MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = dp(2).toFloat()
            setContentPadding(dp(16), dp(14), dp(16), dp(14))
            val column = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 17f, true))
                content?.invoke(this)
            }
            addView(column)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

    private fun button(label: String, action: () -> Unit): Button =
        com.google.android.material.button.MaterialButton(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun text(value: String, sizeSp: Float, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun date(timestamp: Long): String = DateFormat.getDateTimeInstance().format(Date(timestamp))
    private fun dateOrNever(timestamp: Long): String = if (timestamp == 0L) "Never" else date(timestamp)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
