package com.example.sms_call_log_local_gh

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import java.io.IOException

class DeviceDataReader(private val context: Context) {

    fun readSms(): List<BackupEntry> {
        val entries = mutableListOf<BackupEntry>()
        val projection = arrayOf(
            Telephony.Sms.DATE,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.TYPE,
            Telephony.Sms.BODY
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} ASC"
        ) ?: throw IOException("The SMS provider did not return a readable cursor.")
        cursor.use {
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                val messageType = cursor.getInt(type)
                entries += BackupEntry(
                    kind = BackupKind.SMS,
                    timestamp = cursor.getLong(date),
                    number = cursor.getString(address).orEmpty(),
                    category = smsType(messageType),
                    body = cursor.getString(body).orEmpty()
                )
            }
        }
        return entries
    }

    fun readCalls(): List<BackupEntry> {
        val entries = mutableListOf<BackupEntry>()
        val projection = arrayOf(
            CallLog.Calls.DATE,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION
        )
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} ASC"
        ) ?: throw IOException("The Call Log provider did not return a readable cursor.")
        cursor.use {
            val date = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val number = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val type = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val duration = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                entries += BackupEntry(
                    kind = BackupKind.CALL,
                    timestamp = cursor.getLong(date),
                    number = cursor.getString(number).orEmpty(),
                    category = callType(cursor.getInt(type)),
                    durationSeconds = cursor.getLong(duration)
                )
            }
        }
        return entries
    }

    private fun callType(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        CallLog.Calls.BLOCKED_TYPE -> "blocked"
        CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
        else -> "unknown"
    }

    private fun smsType(type: Int): String = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> "received"
        Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "sent"
        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "draft"
        Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
        Telephony.Sms.MESSAGE_TYPE_QUEUED -> "queued"
        else -> "unknown"
    }
}
