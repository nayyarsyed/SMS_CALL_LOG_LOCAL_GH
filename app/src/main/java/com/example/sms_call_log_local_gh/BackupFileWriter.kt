package com.example.sms_call_log_local_gh

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.StringWriter
import java.util.UUID

class BackupFileWriter(private val context: Context) {

    fun write(kind: BackupKind, format: BackupFormat, entries: List<BackupEntry>) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val extension = format.name.lowercase()
        val prefix = if (kind == BackupKind.SMS) "sms" else "call_log"
        val fileName = "${prefix}_backup.$extension"
        val mimeType = when (format) {
            BackupFormat.JSON -> "application/json"
            BackupFormat.XML -> "application/xml"
            BackupFormat.CSV -> "text/csv"
        }
        val contents = serialize(format, entries)
        val treeUri = preferences.getString(KEY_TREE_URI, null)
        if (treeUri == null) {
            if (!preferences.getBoolean(KEY_DOWNLOADS_CONFIRMED, false)) {
                throw IOException("Choose SD-card storage or confirm Downloads/SMS_Call_Backup first.")
            }
            replaceDownloadAtomically(fileName, mimeType, contents)
        } else {
            val directory = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                ?: throw IOException("The selected backup folder is no longer available.")
            if (!directory.canWrite()) {
                throw IOException("The selected backup folder is not writable.")
            }
            replaceAtomically(directory, fileName, mimeType, contents)
        }
    }

    private fun replaceDownloadAtomically(fileName: String, mimeType: String, contents: String) {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/SMS_Call_Backup/"
        val suffix = UUID.randomUUID().toString()
        val stagedName = ".$fileName.$suffix.tmp"
        val previousName = ".$fileName.$suffix.previous"
        val stagedValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stagedName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val stagedUri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            stagedValues
        ) ?: throw IOException("Could not create a file in Downloads/SMS_Call_Backup.")

        var previousUri: Uri? = null
        var previousMoved = false
        var published = false
        try {
            val output = context.contentResolver.openOutputStream(stagedUri, "wt")
                ?: throw IOException("Could not open the temporary Downloads backup.")
            output.use {
                it.write(contents.toByteArray(Charsets.UTF_8))
                it.flush()
            }

            previousUri = findDownload(fileName, relativePath)
            if (previousUri != null) {
                val renamePrevious = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, previousName)
                }
                if (context.contentResolver.update(previousUri, renamePrevious, null, null) != 1) {
                    throw IOException("Could not safely replace $fileName in Downloads.")
                }
                previousMoved = true
            }
            val publish = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (context.contentResolver.update(stagedUri, publish, null, null) != 1) {
                throw IOException("Could not finish writing $fileName in Downloads.")
            }
            previousMoved = false
            published = true
        } catch (exception: IOException) {
            restoreDownloadIfNeeded(previousUri, previousMoved, fileName, exception)
            deleteDownloadStage(stagedUri)
            throw exception
        } catch (exception: SecurityException) {
            val failure = IOException("Access to Downloads was denied.", exception)
            restoreDownloadIfNeeded(previousUri, previousMoved, fileName, failure)
            deleteDownloadStage(stagedUri)
            throw failure
        }
        if (published && previousUri != null) {
            try {
                if (context.contentResolver.delete(previousUri, null, null) != 1) {
                    Log.w(TAG, "Could not remove superseded Downloads backup $fileName.")
                }
            } catch (exception: SecurityException) {
                Log.w(TAG, "Could not remove superseded Downloads backup $fileName.", exception)
            }
        }
    }

    private fun findDownload(fileName: String, relativePath: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val cursor = context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(fileName, relativePath),
            null
        ) ?: throw IOException("Could not inspect the Downloads backup folder.")
        cursor.use {
            if (cursor.moveToFirst()) {
                return Uri.withAppendedPath(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0).toString()
                )
            }
        }
        return null
    }

    private fun restoreDownloadIfNeeded(
        uri: Uri?,
        moved: Boolean,
        fileName: String,
        failure: IOException
    ) {
        if (!moved || uri == null) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        }
        try {
            if (context.contentResolver.update(uri, values, null, null) != 1) {
                failure.addSuppressed(IOException("The previous Downloads backup could not be restored."))
            }
        } catch (exception: SecurityException) {
            failure.addSuppressed(IOException("The previous Downloads backup could not be restored.", exception))
        }
    }

    private fun deleteDownloadStage(uri: Uri) {
        try {
            if (context.contentResolver.delete(uri, null, null) != 1) {
                Log.w(TAG, "Could not remove temporary Downloads backup $uri.")
            }
        } catch (exception: SecurityException) {
            Log.w(TAG, "Could not remove temporary Downloads backup $uri.", exception)
        }
    }

    private fun replaceAtomically(
        directory: DocumentFile,
        fileName: String,
        mimeType: String,
        contents: String
    ) {
        val suffix = UUID.randomUUID().toString()
        val stagedName = ".$fileName.$suffix.tmp"
        val previousName = ".$fileName.$suffix.previous"
        val staged = directory.createFile(mimeType, stagedName)
            ?: throw IOException("Could not create a temporary backup file.")

        var previous: DocumentFile? = null
        var previousMoved = false
        var published = false
        try {
            val output = context.contentResolver.openOutputStream(staged.uri, "wt")
                ?: throw IOException("Could not open the temporary backup file.")
            output.use {
                it.write(contents.toByteArray(Charsets.UTF_8))
                it.flush()
            }

            val current = directory.findFile(fileName)
            if (current != null && !current.renameTo(previousName)) {
                throw IOException("Could not safely replace $fileName.")
            }
            if (current != null) {
                previous = current
                previousMoved = true
            }

            if (!staged.renameTo(fileName)) {
                throw IOException("Could not finish writing $fileName.")
            }
            previousMoved = false
            published = true
        } catch (exception: IOException) {
            restoreSafIfNeeded(previous, previousMoved, fileName, exception)
            deleteSafStage(staged)
            throw exception
        } catch (exception: SecurityException) {
            val failure = IOException("Access to the selected folder was revoked.", exception)
            restoreSafIfNeeded(previous, previousMoved, fileName, failure)
            deleteSafStage(staged)
            throw failure
        }
        if (published) {
            previous?.let {
                try {
                    if (!it.delete()) Log.w(TAG, "Could not remove superseded backup $fileName.")
                } catch (exception: SecurityException) {
                    Log.w(TAG, "Could not remove superseded backup $fileName.", exception)
                }
            }
        }
    }

    private fun restoreSafIfNeeded(
        previous: DocumentFile?,
        moved: Boolean,
        fileName: String,
        failure: IOException
    ) {
        if (moved && previous != null) {
            try {
                if (!previous.renameTo(fileName)) {
                    failure.addSuppressed(IOException("The previous backup could not be restored."))
                }
            } catch (exception: SecurityException) {
                failure.addSuppressed(IOException("The previous backup could not be restored.", exception))
            }
        }
    }

    private fun deleteSafStage(staged: DocumentFile) {
        try {
            if (!staged.delete()) Log.w(TAG, "Could not remove temporary backup ${staged.name}.")
        } catch (exception: SecurityException) {
            Log.w(TAG, "Could not remove temporary backup ${staged.name}.", exception)
        }
    }

    private fun serialize(format: BackupFormat, entries: List<BackupEntry>): String =
        when (format) {
            BackupFormat.JSON -> serializeJson(entries)
            BackupFormat.XML -> serializeXml(entries)
            BackupFormat.CSV -> serializeCsv(entries)
        }

    private fun serializeJson(entries: List<BackupEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("number", entry.number)
                    .put("type", entry.category)
                    .put("body", entry.body)
                    .put("durationSeconds", entry.durationSeconds)
            )
        }
        return JSONObject().put("entries", array).toString(2) + "\n"
    }

    private fun serializeXml(entries: List<BackupEntry>): String {
        val output = StringWriter()
        val serializer: XmlSerializer = Xml.newSerializer()
        serializer.setOutput(output)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "entries")
        entries.forEach { entry ->
            serializer.startTag(null, "entry")
            serializer.attribute(null, "timestamp", entry.timestamp.toString())
            serializer.attribute(null, "number", entry.number)
            serializer.attribute(null, "type", entry.category)
            serializer.attribute(null, "durationSeconds", entry.durationSeconds.toString())
            serializer.startTag(null, "body")
            serializer.text(entry.body)
            serializer.endTag(null, "body")
            serializer.endTag(null, "entry")
        }
        serializer.endTag(null, "entries")
        serializer.endDocument()
        return output.toString()
    }

    private fun serializeCsv(entries: List<BackupEntry>): String = buildString {
        appendLine("timestamp,number,type,body,duration_seconds")
        entries.forEach { entry ->
            append(entry.timestamp).append(',')
            append(csv(entry.number)).append(',')
            append(csv(entry.category)).append(',')
            append(csv(entry.body)).append(',')
            append(entry.durationSeconds).appendLine()
        }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private const val TAG = "BackupFileWriter"
        const val PREFS = "backup_settings"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_DOWNLOADS_CONFIRMED = "downloads_confirmed"
    }
}
