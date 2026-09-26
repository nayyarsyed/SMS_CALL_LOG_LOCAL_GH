package com.example.sms_call_log_local_gh

import java.security.MessageDigest

enum class BackupKind(val databaseValue: String) {
    SMS("sms"),
    CALL("call")
}

enum class BackupFormat {
    JSON,
    XML,
    CSV
}

data class BackupEntry(
    val kind: BackupKind,
    val timestamp: Long,
    val number: String,
    val category: String,
    val body: String = "",
    val durationSeconds: Long = 0L
) {
    fun stableHash(): String {
        val canonical = listOf(
            kind.databaseValue,
            timestamp.toString(),
            number,
            category,
            body,
            durationSeconds.toString()
        ).joinToString(separator = "") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class BackupRunResult(
    val smsCount: Int,
    val callCount: Int,
    val failures: List<String>
) {
    val succeeded: Boolean get() = failures.isEmpty()
}
