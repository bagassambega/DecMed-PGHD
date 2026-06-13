package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import java.text.Normalizer

object PghdInputSanitizer {
    private const val MAX_ID_LENGTH = 240
    private const val MAX_RECORD_TYPE_LENGTH = 64
    private const val MAX_DISPLAY_LENGTH = 120
    private const val MAX_UNIT_LENGTH = 32
    private const val MAX_VALUE_LENGTH = 2_000
    private const val MAX_NOTES_LENGTH = 2_000
    private const val MAX_PACKAGE_LENGTH = 160

    fun sanitizeManualInput(
        recordType: String,
        displayName: String,
        valueText: String,
        unit: String,
        notes: String?
    ): ManualPghdInput {
        val sanitizedValue = sanitizeFreeText(valueText, MAX_VALUE_LENGTH)
        require(sanitizedValue.isNotBlank()) { "Manual PGHD value is required after sanitization." }

        val numericValue = sanitizedValue.toDoubleOrNull()?.takeIf { it.isFinite() }
        return ManualPghdInput(
            recordType = sanitizeRecordType(recordType, "manual_pghd"),
            displayName = sanitizeDisplayText(displayName, "Manual PGHD"),
            valueText = sanitizedValue,
            unit = sanitizeUnit(unit, "value"),
            numericValue = numericValue,
            notes = notes?.let { sanitizeFreeText(it, MAX_NOTES_LENGTH) }?.takeIf { it.isNotBlank() }
        )
    }

    fun sanitizeRecords(records: List<PghdRecordEntity>): List<PghdRecordEntity> =
        records.mapNotNull(::sanitizeRecordOrNull)

    fun sanitizeRecordOrNull(record: PghdRecordEntity): PghdRecordEntity? {
        val valueText = sanitizeFreeText(record.valueText, MAX_VALUE_LENGTH)
        if (valueText.isBlank()) return null

        val start = record.startTimeEpochMillis.coerceAtLeast(0L)
        val end = record.endTimeEpochMillis.coerceAtLeast(start)

        return record.copy(
            uid = sanitizeIdentifier(record.uid, "pghd:${start}:${end}:${valueText.hashCode()}", MAX_ID_LENGTH),
            recordType = sanitizeRecordType(record.recordType, "pghd_record"),
            displayName = sanitizeDisplayText(record.displayName, "PGHD Record"),
            startTimeEpochMillis = start,
            endTimeEpochMillis = end,
            unit = sanitizeUnit(record.unit, "value"),
            valueText = valueText,
            numericValue = record.numericValue?.takeIf { it.isFinite() },
            sourceTag = sanitizeDisplayText(record.sourceTag, "Unknown Source"),
            sourcePackageName = record.sourcePackageName
                ?.let { sanitizeIdentifier(it, "", MAX_PACKAGE_LENGTH) }
                ?.takeIf { it.isNotBlank() },
            notes = record.notes
                ?.let { sanitizeFreeText(it, MAX_NOTES_LENGTH) }
                ?.takeIf { it.isNotBlank() },
            syncedAtEpochMillis = record.syncedAtEpochMillis.coerceAtLeast(0L),
            batchId = record.batchId?.let { sanitizeIdentifier(it, "", MAX_ID_LENGTH) }?.takeIf { it.isNotBlank() }
        )
    }

    private fun sanitizeRecordType(value: String, fallback: String): String =
        sanitizeIdentifier(value.lowercase().replace(' ', '_'), fallback, MAX_RECORD_TYPE_LENGTH)

    private fun sanitizeDisplayText(value: String, fallback: String): String =
        sanitizeFreeText(value, MAX_DISPLAY_LENGTH).ifBlank { fallback }

    private fun sanitizeUnit(value: String, fallback: String): String {
        val sanitized = sanitizeFreeText(value, MAX_UNIT_LENGTH)
            .filter { it.isLetterOrDigit() || it in setOf('%', '/', '.', '^', '_', '-', ' ') }
            .trim()
        return sanitized.ifBlank { fallback }
    }

    private fun sanitizeIdentifier(value: String, fallback: String, maxLength: Int): String {
        val sanitized = normalize(value)
            .filter { it.isLetterOrDigit() || it in setOf('_', '-', '.', ':', '@') }
            .take(maxLength)
            .trim('.', ':', '@', '_', '-')
        return sanitized.ifBlank { fallback.take(maxLength) }
    }

    private fun sanitizeFreeText(value: String, maxLength: Int): String =
        normalize(value)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .filterNot { it.isISOControl() || it.isSurrogate() }
}

data class ManualPghdInput(
    val recordType: String,
    val displayName: String,
    val valueText: String,
    val unit: String,
    val numericValue: Double?,
    val notes: String?
)
