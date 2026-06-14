package com.hackastic.decmed.pghd

import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdInputSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PghdInputSanitizerTest {
    @Test
    fun manualInput_removesControlCharactersAndNormalizesWhitespace() {
        val input = PghdInputSanitizer.sanitizeManualInput(
            recordType = "  Heart Rate\u0000 ",
            displayName = " Heart\u0007 Rate ",
            valueText = "  72\u0000  bpm  ",
            unit = " bpm<script> ",
            notes = "  measured\u0008 manually  "
        )

        assertEquals("heart_rate", input.recordType)
        assertEquals("Heart Rate", input.displayName)
        assertEquals("72 bpm", input.valueText)
        assertEquals("bpmscript", input.unit)
        assertEquals("measured manually", input.notes)
    }

    @Test
    fun manualInput_rejectsBlankValueAfterSanitization() {
        assertThrows(IllegalArgumentException::class.java) {
            PghdInputSanitizer.sanitizeManualInput(
                recordType = "steps",
                displayName = "Steps",
                valueText = "\u0000 \u0007",
                unit = "count",
                notes = null
            )
        }
    }

    @Test
    fun recordInput_coercesTimeAndDropsInvalidNumbers() {
        val record = PghdRecordEntity(
            uid = " uid with spaces! ",
            recordType = "Phone Sensor",
            displayName = " Acceleration ",
            startTimeEpochMillis = -100,
            endTimeEpochMillis = -50,
            unit = " m/s^2 ",
            valueText = " 1.0 ",
            numericValue = Double.NaN,
            sourceTag = " Phone Sensor ",
            sourcePackageName = "com.example.app<script>",
            notes = " note\u0000value ",
            syncedAtEpochMillis = -1,
            batchId = " batch id! "
        )

        val sanitized = PghdInputSanitizer.sanitizeRecordOrNull(record)

        assertTrue(sanitized != null)
        sanitized!!
        assertFalse(sanitized.uid.contains(" "))
        assertEquals("phone_sensor", sanitized.recordType)
        assertEquals(0L, sanitized.startTimeEpochMillis)
        assertEquals(0L, sanitized.endTimeEpochMillis)
        assertNull(sanitized.numericValue)
        assertEquals("com.example.appscript", sanitized.sourcePackageName)
        assertEquals("notevalue", sanitized.notes)
        assertEquals(0L, sanitized.syncedAtEpochMillis)
    }

    @Test
    fun recordInput_dropsBlankValueRecord() {
        val record = PghdRecordEntity(
            uid = "uid",
            recordType = "steps",
            displayName = "Steps",
            startTimeEpochMillis = 1,
            endTimeEpochMillis = 2,
            unit = "count",
            valueText = "\u0000",
            sourceTag = "Health Connect"
        )

        assertNull(PghdInputSanitizer.sanitizeRecordOrNull(record))
    }
}
