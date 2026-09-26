package com.example.sms_call_log_local_gh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupModelsTest {
    @Test
    fun stableHashIsDeterministicAndIncludesRecordContent() {
        val original = BackupEntry(
            BackupKind.SMS,
            timestamp = 1_700_000_000_000,
            number = "+15551234567",
            category = "received",
            body = "hello"
        )
        val same = original.copy()
        val changedBody = original.copy(body = "hello!")

        assertEquals(original.stableHash(), same.stableHash())
        assertNotEquals(original.stableHash(), changedBody.stableHash())
    }

    @Test
    fun hashSeparatesDifferentEntryTypes() {
        val sms = BackupEntry(BackupKind.SMS, 100L, "123", "received", body = "2:abc")
        val call = BackupEntry(BackupKind.CALL, 100L, "123", "received", body = "2:abc")

        assertNotEquals(sms.stableHash(), call.stableHash())
    }
}
