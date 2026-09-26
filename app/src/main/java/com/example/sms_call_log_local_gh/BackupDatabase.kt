package com.example.sms_call_log_local_gh

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BackupDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "backup-index.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                hash TEXT PRIMARY KEY NOT NULL,
                kind TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                number TEXT NOT NULL,
                category TEXT NOT NULL,
                body TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL,
                backed_up INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX entries_kind_timestamp ON entries(kind, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN backed_up INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Synchronized
    fun addIfNew(entry: BackupEntry): Boolean {
        val values = ContentValues().apply {
            put("hash", entry.stableHash())
            put("kind", entry.kind.databaseValue)
            put("timestamp", entry.timestamp)
            put("number", entry.number)
            put("category", entry.category)
            put("body", entry.body)
            put("duration_seconds", entry.durationSeconds)
        }
        return writableDatabase.insertWithOnConflict(
            "entries",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    @Synchronized
    fun entries(kind: BackupKind): List<BackupEntry> {
        val result = mutableListOf<BackupEntry>()
        readableDatabase.query(
            "entries",
            arrayOf("timestamp", "number", "category", "body", "duration_seconds"),
            "kind = ?",
            arrayOf(kind.databaseValue),
            null,
            null,
            "timestamp ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupEntry(
                    kind = kind,
                    timestamp = cursor.getLong(0),
                    number = cursor.getString(1),
                    category = cursor.getString(2),
                    body = cursor.getString(3),
                    durationSeconds = cursor.getLong(4)
                )
            }
        }
        return result
    }

    @Synchronized
    fun count(kind: BackupKind): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM entries WHERE kind = ? AND backed_up = 1",
            arrayOf(kind.databaseValue)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun markBackedUp(kind: BackupKind) {
        writableDatabase.execSQL(
            "UPDATE entries SET backed_up = 1 WHERE kind = ?",
            arrayOf(kind.databaseValue)
        )
    }
}
