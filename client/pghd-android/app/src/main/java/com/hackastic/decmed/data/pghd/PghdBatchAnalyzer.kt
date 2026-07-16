package com.hackastic.decmed.data.pghd

import com.hackastic.decmed.config.Env
import com.hackastic.decmed.domain.model.pghd.PghdAnomalyFlag
import com.hackastic.decmed.domain.model.pghd.PghdClinicalThreshold
import com.hackastic.decmed.domain.model.pghd.PghdDataGroupPayload
import com.hackastic.decmed.domain.model.pghd.PghdMeasurementValue
import com.hackastic.decmed.domain.model.pghd.PghdPercentiles
import com.hackastic.decmed.domain.model.pghd.PghdStatisticsSummary
import kotlin.math.ceil
import kotlin.math.floor

object PghdBatchAnalyzer {
    fun analyze(
        groups: List<PghdDataGroupPayload>,
        thresholds: List<PghdClinicalThreshold> = PghdClinicalThresholdConfig.thresholds
    ): List<PghdDataGroupPayload> =
        groups.map { group ->
            analyzeGroup(
                group = group,
                thresholds = thresholds.filter { it.measurementType == group.measurementType }
            )
        }

    fun analyzeGroup(
        group: PghdDataGroupPayload,
        thresholds: List<PghdClinicalThreshold>
    ): PghdDataGroupPayload {
        val valuesByField = linkedMapOf<String, MutableList<Double>>()
        group.dataPoints.forEach { dataPoint ->
            numericFields(dataPoint.value).forEach { (field, value) ->
                valuesByField.getOrPut(field) { mutableListOf() }.add(value)
            }
        }

        val analyzedPoints = group.dataPoints.map { dataPoint ->
            val anomalies = numericFields(dataPoint.value).mapNotNull { (field, value) ->
                thresholds.firstOrNull { it.field == field }
                    ?.let { threshold -> anomalyFor(field, value, threshold) }
            }
            dataPoint.copy(anomalies = anomalies)
        }

        return group.copy(
            statistics = valuesByField.map { (field, values) ->
                summarize(field = field, values = values, unit = group.dataPoints.firstOrNull()?.unit.orEmpty())
            },
            clinicalThresholds = thresholds,
            anomalyCount = analyzedPoints.sumOf { it.anomalies.size },
            dataPoints = analyzedPoints
        )
    }

    fun summarize(field: String, values: List<Double>, unit: String): PghdStatisticsSummary {
        require(values.isNotEmpty()) { "Statistics require at least one numeric value." }
        val sorted = values.sorted()
        val frequencies = sorted.groupingBy { it }.eachCount()
        val highestFrequency = frequencies.values.maxOrNull() ?: 1
        val modes = if (highestFrequency <= 1) {
            emptyList()
        } else {
            frequencies.filterValues { it == highestFrequency }.keys.sorted()
        }

        return PghdStatisticsSummary(
            field = field,
            count = sorted.size,
            minimum = sorted.first(),
            maximum = sorted.last(),
            mean = sorted.average(),
            median = percentile(sorted, 50.0),
            mode = modes,
            percentiles = PghdPercentiles(
                p5 = percentile(sorted, 5.0),
                p25 = percentile(sorted, 25.0),
                p50 = percentile(sorted, 50.0),
                p75 = percentile(sorted, 75.0),
                p95 = percentile(sorted, 95.0)
            ),
            unit = unit
        )
    }

    private fun numericFields(value: PghdMeasurementValue): Map<String, Double> =
        when (value) {
            is PghdMeasurementValue.NumberValue -> mapOf("value" to value.value)
            is PghdMeasurementValue.ObjectValue -> value.values.mapNotNull { (field, rawValue) ->
                (rawValue as? Number)?.toDouble()?.let { field to it }
            }.toMap()
        }

    private fun percentile(sorted: List<Double>, percentile: Double): Double {
        if (sorted.size == 1) return sorted.first()
        val rank = percentile / 100.0 * (sorted.lastIndex)
        val lowerIndex = floor(rank).toInt()
        val upperIndex = ceil(rank).toInt()
        if (lowerIndex == upperIndex) return sorted[lowerIndex]
        val fraction = rank - lowerIndex
        return sorted[lowerIndex] + (sorted[upperIndex] - sorted[lowerIndex]) * fraction
    }

    private fun anomalyFor(
        field: String,
        value: Double,
        threshold: PghdClinicalThreshold
    ): PghdAnomalyFlag? {
        val below = if (threshold.minimumInclusive) value < threshold.minimum else value <= threshold.minimum
        val above = if (threshold.maximumInclusive) value > threshold.maximum else value >= threshold.maximum
        val direction = when {
            below -> PghdAnomalyFlag.BELOW_RANGE
            above -> PghdAnomalyFlag.ABOVE_RANGE
            else -> return null
        }
        return PghdAnomalyFlag(
            field = field,
            value = value,
            direction = direction,
            normalMinimum = threshold.minimum,
            normalMaximum = threshold.maximum
        )
    }
}

object PghdClinicalThresholdConfig {
    val thresholds: List<PghdClinicalThreshold> by lazy { parse(Env.pghdClinicalThresholds) }

    fun parse(raw: String): List<PghdClinicalThreshold> =
        raw.split(";;")
            .mapNotNull { encoded ->
                val fields = encoded.split("|", limit = 11).map(String::trim)
                if (fields.size != 11) return@mapNotNull null
                val minimum = fields[2].toDoubleOrNull() ?: return@mapNotNull null
                val maximum = fields[3].toDoubleOrNull() ?: return@mapNotNull null
                val minimumInclusive = fields[4].toBooleanStrictOrNull() ?: return@mapNotNull null
                val maximumInclusive = fields[5].toBooleanStrictOrNull() ?: return@mapNotNull null
                if (fields[0].isBlank() || fields[1].isBlank() || minimum > maximum) return@mapNotNull null

                PghdClinicalThreshold(
                    measurementType = fields[0],
                    field = fields[1],
                    minimum = minimum,
                    maximum = maximum,
                    minimumInclusive = minimumInclusive,
                    maximumInclusive = maximumInclusive,
                    unit = fields[6],
                    label = fields[7],
                    reference = fields[8],
                    referenceUrl = fields[9],
                    population = fields[10]
                )
            }
}
