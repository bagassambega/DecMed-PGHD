package com.hackastic.decmed.pghd

import com.hackastic.decmed.domain.model.pghd.PghdEncryptedPlaintext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PghdEncryptedPlaintextSerializerTest {
    @Test
    fun encryptedPlaintext_containsPlainHashAndNoInnerSignature() {
        val pghdData = """{"schema_version":"1","batch_id":"batch-1","data_group":[]}"""
        val plaintext = PghdEncryptedPlaintext(
            pghdData = pghdData,
            hPlain = "abc123"
        )
        val fields = PghdEncryptedPlaintext::class.java.declaredFields.map { it.name }.toSet()

        assertEquals(pghdData, plaintext.pghdData)
        assertEquals("abc123", plaintext.hPlain)
        assertTrue(fields.contains("hPlain"))
        assertFalse(fields.contains("innerSignature"))
    }
}
