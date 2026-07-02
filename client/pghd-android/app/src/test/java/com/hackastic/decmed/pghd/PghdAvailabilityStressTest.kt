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
        val simulatedStreamCount = 15
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
        assertTrue("Payloads should preserve grouped PGHD data", dataGroupCount in 15..(simulatedBacklogDays * simulatedStreamCount))
        assertTrue(payloads.all { payload -> payload.triggerReason == PghdBatchPayload.TRIGGER_SIZE_THRESHOLD })
        assertTrue(payloads.all { payload -> payload.dataGroup.all { group -> group.deviceType == "wearable" } })
        assertTrue("Light stress conversion should finish quickly, elapsed=${elapsedMillis}ms", elapsedMillis < 10_000)
    }

    private fun syntheticWearableRecords(count: Int): List<PghdRecordEntity> {
        val baseTime = 1_800_000_000_000L
        return List(count) { index ->
            val type = STRESS_TYPES[index % STRESS_TYPES.size]
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
            "respiratory_rate" -> (12 + (index % 12)).toString()
            "resting_heart_rate" -> (52 + (index % 32)).toString()
            "heart_rate_variability" -> (20 + (index % 100)).toString()
            "active_calories_burned" -> (index % 4).toString()
            "distance" -> (20 + (index % 140)).toString()
            "vo2_max" -> (24 + (index % 34)).toString()
            "body_temperature" -> (36 + (index % 3)).toString()
            "sleep_duration" -> (index % 60).toString()
            "environmental_temperature" -> (24 + (index % 10)).toString()
            "environmental_humidity" -> (35 + (index % 50)).toString()
            "ambient_light" -> (index % 1200).toString()
            "barometric_pressure" -> (990 + (index % 35)).toString()
            else -> (index % 12).toString()
        }

    private companion object {
        val STRESS_TYPES = listOf(
            Triple("steps", "Steps", "count"),
            Triple("heart_rate", "Heart Rate", "bpm"),
            Triple("oxygen_saturation", "Oxygen Saturation", "%"),
            Triple("respiratory_rate", "Respiratory Rate", "breaths/min"),
            Triple("resting_heart_rate", "Resting Heart Rate", "bpm"),
            Triple("heart_rate_variability", "Heart Rate Variability", "ms"),
            Triple("active_calories_burned", "Active Calories Burned", "kcal"),
            Triple("distance", "Distance", "m"),
            Triple("vo2_max", "VO2 Max", "mL/kg/min"),
            Triple("body_temperature", "Body Temperature", "C"),
            Triple("sleep_duration", "Sleep Duration", "min"),
            Triple("environmental_temperature", "Environmental Temperature", "C"),
            Triple("environmental_humidity", "Environmental Humidity", "%"),
            Triple("ambient_light", "Ambient Light", "lux"),
            Triple("barometric_pressure", "Barometric Pressure", "hPa")
        )
    }
}
