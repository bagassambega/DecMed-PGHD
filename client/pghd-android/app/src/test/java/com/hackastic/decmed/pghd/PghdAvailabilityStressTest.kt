package com.hackastic.decmed.pghd

import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdSourceDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class PghdAvailabilityStressTest {
    @Test
    fun highVolumeTimeSeries_isConvertedIntoFewBatchesInsteadOfPerRecordPayloads() {
        val recordsPerStreamPerDay = 24 * 60
        val simulatedBacklogDays = 7
        val simulatedStreamCount = 4
        val recordCount = recordsPerStreamPerDay * simulatedBacklogDays * simulatedStreamCount
        val batchSize = recordsPerStreamPerDay * simulatedStreamCount
        val records = syntheticWearableRecords(recordCount)
        val sourceDevice = PghdSourceDevice(
            type = "wearable",
            platform = "android",
            appVersion = "test",
            deviceManufacturer = "Xiaomi",
            deviceModel = "Smart Band"
        )

        val payloads = mutableListOf<PghdBatchPayload>()
        val elapsedMillis = measureTimeMillis {
            records.chunked(batchSize).forEachIndexed { index, chunk ->
                payloads += PghdPayloadConverter.recordsToBatchPayload(
                    records = chunk,
                    patientId = "0xpatient",
                    batchId = "stress-batch-$index",
                    sourceDevice = sourceDevice,
                    triggerReason = PghdBatchPayload.TRIGGER_SIZE_THRESHOLD
                )
            }
        }

        val dataPointCount = payloads.sumOf { payload ->
            payload.dataGroup.sumOf { group -> group.dataPoints.size }
        }
        val dataGroupCount = payloads.sumOf { payload -> payload.dataGroup.size }

        assertEquals(recordCount, dataPointCount)
        assertEquals(simulatedBacklogDays, payloads.size)
        assertTrue("Stress conversion should not generate one batch per record", payloads.size < recordCount / 100)
        assertTrue("Payloads should preserve grouped PGHD data", dataGroupCount in 4..(simulatedBacklogDays * simulatedStreamCount))
        assertTrue(payloads.all { payload -> payload.triggerReason == PghdBatchPayload.TRIGGER_SIZE_THRESHOLD })
        assertTrue(payloads.all { payload -> payload.dataGroup.all { group -> group.deviceType == "wearable" } })
        assertTrue("Light stress conversion should finish quickly, elapsed=${elapsedMillis}ms", elapsedMillis < 10_000)
    }

    private fun syntheticWearableRecords(count: Int): List<PghdRecordEntity> {
        val baseTime = 1_800_000_000_000L
        return List(count) { index ->
            val type = when (index % 4) {
                0 -> Triple("steps", "Steps", "count")
                1 -> Triple("heart_rate", "Heart Rate", "bpm")
                2 -> Triple("oxygen_saturation", "Oxygen Saturation", "%")
                else -> Triple("distance", "Distance", "m")
            }
            val endTime = baseTime + (index * 60_000L)
            PghdRecordEntity(
                uid = "stress-record-$index",
                recordType = type.first,
                displayName = type.second,
                startTimeEpochMillis = endTime - 60_000L,
                endTimeEpochMillis = endTime,
                unit = type.third,
                valueText = syntheticValue(type.first, index),
                numericValue = syntheticValue(type.first, index).toDouble(),
                sourceTag = PghdRecordEntity.SOURCE_HEALTH_CONNECT,
                sourcePackageName = "com.xiaomi.hm.health",
                notes = null,
                syncedAtEpochMillis = endTime
            )
        }
    }

    private fun syntheticValue(recordType: String, index: Int): String =
        when (recordType) {
            "steps" -> (80 + (index % 200)).toString()
            "heart_rate" -> (62 + (index % 45)).toString()
            "oxygen_saturation" -> (95 + (index % 5)).toString()
            else -> (20 + (index % 50)).toString()
        }
}
