package com.hackastic.decmed.pghd

import com.hackastic.decmed.data.pghd.PghdBatchAnalyzer
import com.hackastic.decmed.data.pghd.PghdClinicalThresholdConfig
import com.hackastic.decmed.domain.model.pghd.PghdAnomalyFlag
import com.hackastic.decmed.domain.model.pghd.PghdClinicalThreshold
import com.hackastic.decmed.domain.model.pghd.PghdDataGroupPayload
import com.hackastic.decmed.domain.model.pghd.PghdDataPointPayload
import com.hackastic.decmed.domain.model.pghd.PghdMeasurementValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PghdBatchAnalyzerTest {
    @Test
    fun calculatesStatisticsAndMultimodalValues() {
        val summary = PghdBatchAnalyzer.summarize(
            field = "value",
            values = listOf(1.0, 2.0, 2.0, 3.0, 3.0, 10.0),
            unit = "unit"
        )

        assertEquals(1.0, summary.minimum, 0.0)
        assertEquals(10.0, summary.maximum, 0.0)
        assertEquals(3.5, summary.mean, 0.0)
        assertEquals(2.5, summary.median, 0.0)
		assertEquals(listOf(2.0, 3.0), summary.mode)
		assertEquals(1.25, summary.percentiles.p5, 0.0)
		assertEquals(2.0, summary.percentiles.p25, 0.0)
		assertEquals(2.5, summary.percentiles.p50, 0.0)
		assertEquals(3.0, summary.percentiles.p75, 0.0)
		assertEquals(8.25, summary.percentiles.p95, 0.0)
    }

    @Test
    fun respectsInclusiveAndExclusiveClinicalBoundaries() {
        val threshold = threshold(
            measurementType = "blood_pressure",
            field = "systolic",
            minimum = 90.0,
            maximum = 120.0,
            minimumInclusive = true,
            maximumInclusive = false
        )
        val group = group(
            measurementType = "blood_pressure",
            values = listOf(89.0, 90.0, 119.0, 120.0).map { value ->
                PghdMeasurementValue.ObjectValue(mapOf("systolic" to value, "diastolic" to 70.0))
            }
        )

        val analyzed = PghdBatchAnalyzer.analyzeGroup(group, listOf(threshold))

        assertEquals(2, analyzed.anomalyCount)
        assertEquals(PghdAnomalyFlag.BELOW_RANGE, analyzed.dataPoints[0].anomalies.single().direction)
        assertTrue(analyzed.dataPoints[1].anomalies.isEmpty())
        assertTrue(analyzed.dataPoints[2].anomalies.isEmpty())
        assertEquals(PghdAnomalyFlag.ABOVE_RANGE, analyzed.dataPoints[3].anomalies.single().direction)
        assertEquals(setOf("systolic", "diastolic"), analyzed.statistics.map { it.field }.toSet())
    }

    @Test
    fun parsesThresholdConfigurationAndIgnoresMalformedEntries() {
        val valid = "heart_rate|value|60|100|true|true|beats/min|Resting heart rate|AHA|https://example.test|adult at rest"
        val parsed = PghdClinicalThresholdConfig.parse("invalid;;$valid")

        assertEquals(1, parsed.size)
        assertEquals("heart_rate", parsed.single().measurementType)
        assertEquals(60.0, parsed.single().minimum, 0.0)
        assertEquals(100.0, parsed.single().maximum, 0.0)
        assertTrue(parsed.single().maximumInclusive)
    }

    private fun group(
        measurementType: String,
        values: List<PghdMeasurementValue>
    ) = PghdDataGroupPayload(
        measurementType = measurementType,
        deviceType = "wearable",
        source = "test",
        dataPoints = values.mapIndexed { index, value ->
            PghdDataPointPayload(
                timestamp = index.toLong(),
                value = value,
                unit = "mm[Hg]"
            )
        }
    )

    private fun threshold(
        measurementType: String,
        field: String,
        minimum: Double,
        maximum: Double,
        minimumInclusive: Boolean,
        maximumInclusive: Boolean
    ) = PghdClinicalThreshold(
        measurementType = measurementType,
        field = field,
        minimum = minimum,
        maximum = maximum,
        minimumInclusive = minimumInclusive,
        maximumInclusive = maximumInclusive,
        unit = "mm[Hg]",
        label = "Test threshold",
        reference = "Test reference",
        referenceUrl = "https://example.test",
        population = "test population"
    )
}
