package com.hackastic.decmed.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PlannedExerciseSessionRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.utils.DecmedLog
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

data class HealthConnectAvailabilityState(
    val statusCode: Int,
    val isAvailable: Boolean,
    val message: String
)

data class HealthConnectPermissionState(
    val grantedPermissions: Set<String>,
    val hasRequiredDataPermissions: Boolean,
    val hasHistoryPermission: Boolean
)

class HealthConnectPghdClient(
    private val context: Context
) {
    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    fun getAvailabilityState(): HealthConnectAvailabilityState {
        val status = HealthConnectClient.getSdkStatus(context)
        val state = when (status) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailabilityState(
                statusCode = status,
                isAvailable = true,
                message = "Health Connect is available."
            )
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailabilityState(
                statusCode = status,
                isAvailable = false,
                message = "Health Connect is installed but needs an update before DecMed can request permissions."
            )
            HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailabilityState(
                statusCode = status,
                isAvailable = false,
                message = "Health Connect is not available on this device or profile."
            )
            else -> HealthConnectAvailabilityState(
                statusCode = status,
                isAvailable = false,
                message = "Health Connect returned unknown SDK status $status."
            )
        }
        DecmedLog.i(TAG, "Health Connect availability: $state")
        return state
    }

    suspend fun isAvailable(): Boolean =
        getAvailabilityState().isAvailable

    suspend fun getGrantedPermissions(): Set<String> =
        healthConnectClient.permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean =
        getPermissionState().let { it.hasRequiredDataPermissions && it.hasHistoryPermission }

    suspend fun getPermissionState(): HealthConnectPermissionState {
        val grantedPermissions = getGrantedPermissions()
        return HealthConnectPermissionState(
            grantedPermissions = grantedPermissions,
            hasRequiredDataPermissions = grantedPermissions.containsAll(XIAOMI_BAND_READ_PERMISSIONS),
            hasHistoryPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedPermissions
        )
    }

    suspend fun readRecentPghd(daysBack: Long = 30): List<PghdRecordEntity> {
        val end = Instant.now()
        val start = end.minus(daysBack, ChronoUnit.DAYS)
        return DESCRIPTORS.flatMap { descriptor ->
            readRecordsForDescriptor(descriptor, start, end)
        }
    }

    suspend fun readXiaomiBandPghd(daysBack: Long): List<PghdRecordEntity> {
        val end = Instant.now()
        val start = end.minus(daysBack, ChronoUnit.DAYS)
        return XIAOMI_BAND_DESCRIPTORS.flatMap { descriptor ->
            readRecordsForDescriptor(descriptor, start, end)
        }
    }

    private suspend fun <T : Record> readRecordsForDescriptor(
        descriptor: HealthRecordDescriptor<T>,
        start: Instant,
        end: Instant
    ): List<PghdRecordEntity> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = descriptor.recordClass,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.flatMap(descriptor.toEntities)
    }

    private data class HealthRecordDescriptor<T : Record>(
        val recordClass: KClass<T>,
        val toEntities: (T) -> List<PghdRecordEntity>
    )

    companion object {
        val DEFAULT_SYNC_DAYS: Long get() = Env.pghdDefaultSyncDays
        val HISTORY_SYNC_DAYS: Long get() = Env.pghdHistorySyncDays

        val XIAOMI_BAND_READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(CyclingPedalingCadenceRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SkinTemperatureRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(SpeedRecord::class),
            HealthPermission.getReadPermission(StepsCadenceRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class)
        )

        val READ_DATA_PERMISSIONS: Set<String> = XIAOMI_BAND_READ_PERMISSIONS

        val READ_HISTORY_PERMISSIONS: Set<String> = setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)

        val READ_PERMISSIONS: Set<String> = READ_DATA_PERMISSIONS + READ_HISTORY_PERMISSIONS

        private const val TAG = "HealthConnectPghdClient"

        private val XIAOMI_BAND_DESCRIPTORS: List<HealthRecordDescriptor<out Record>> = listOf(
            HealthRecordDescriptor(ActiveCaloriesBurnedRecord::class) { record ->
                listOf(record.toPghd("active_calories", "Active calories burned", record.energy.inKilocalories, "kcal", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(CyclingPedalingCadenceRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("cycling_pedaling_cadence", "Cycling cadence", sample.revolutionsPerMinute, "rpm", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(DistanceRecord::class) { record ->
                listOf(record.toPghd("distance", "Distance", record.distance.inMeters, "m", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(ExerciseSessionRecord::class) { record ->
                listOf(record.toPghd("exercise_session", "Exercise session", "Exercise type ${record.exerciseType}", "session", null, record.startTime, record.endTime))
            },
            HealthRecordDescriptor(HeartRateRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("heart_rate", "Heart rate", sample.beatsPerMinute.toDouble(), "bpm", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(HeartRateVariabilityRmssdRecord::class) { record ->
                listOf(record.toPghd("heart_rate_variability_rmssd", "Heart rate variability", record.heartRateVariabilityMillis, "ms", record.time, record.time))
            },
            HealthRecordDescriptor(OxygenSaturationRecord::class) { record ->
                listOf(record.toPghd("oxygen_saturation", "Oxygen saturation", record.percentage.value, "%", record.time, record.time))
            },
            HealthRecordDescriptor(RespiratoryRateRecord::class) { record ->
                listOf(record.toPghd("respiratory_rate", "Respiratory rate", record.rate, "breaths/min", record.time, record.time))
            },
            HealthRecordDescriptor(RestingHeartRateRecord::class) { record ->
                listOf(record.toPghd("resting_heart_rate", "Resting heart rate", record.beatsPerMinute.toDouble(), "bpm", record.time, record.time))
            },
            summaryDescriptor(SkinTemperatureRecord::class, "skin_temperature", "Skin temperature") { record ->
                record.startTime to record.endTime
            },
            HealthRecordDescriptor(SleepSessionRecord::class) { record ->
                listOf(record.toPghd("sleep_session", "Sleep session", "Sleep session", "session", null, record.startTime, record.endTime))
            },
            HealthRecordDescriptor(SpeedRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("speed", "Speed", sample.speed.inMetersPerSecond, "m/s", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(StepsCadenceRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("steps_cadence", "Steps cadence", sample.rate, "steps/min", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(StepsRecord::class) { record ->
                listOf(record.toPghd("steps", "Steps", record.count.toDouble(), "count", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(TotalCaloriesBurnedRecord::class) { record ->
                listOf(record.toPghd("total_calories", "Total calories burned", record.energy.inKilocalories, "kcal", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(Vo2MaxRecord::class) { record ->
                listOf(record.toPghd("vo2_max", "VO2 max", record.vo2MillilitersPerMinuteKilogram, "mL/min/kg", record.time, record.time))
            }
        )

        private val DESCRIPTORS: List<HealthRecordDescriptor<out Record>> = listOf(
            HealthRecordDescriptor(ActiveCaloriesBurnedRecord::class) { record ->
                listOf(record.toPghd("active_calories", "Active calories burned", record.energy.inKilocalories, "kcal", record.startTime, record.endTime))
            },
            summaryDescriptor(BasalBodyTemperatureRecord::class, "basal_body_temperature", "Basal body temperature") { record ->
                record.time to record.time
            },
            HealthRecordDescriptor(BasalMetabolicRateRecord::class) { record ->
                listOf(record.toPghd("basal_metabolic_rate", "Basal metabolic rate", record.basalMetabolicRate.inKilocaloriesPerDay, "kcal/day", record.time, record.time))
            },
            HealthRecordDescriptor(BloodGlucoseRecord::class) { record ->
                listOf(record.toPghd("blood_glucose", "Blood glucose", record.level.inMillimolesPerLiter, "mmol/L", record.time, record.time))
            },
            HealthRecordDescriptor(BloodPressureRecord::class) { record ->
                listOf(record.toPghd("blood_pressure", "Blood pressure", "${record.systolic.inMillimetersOfMercury}/${record.diastolic.inMillimetersOfMercury}", "mmHg", null, record.time, record.time))
            },
            HealthRecordDescriptor(BodyFatRecord::class) { record ->
                listOf(record.toPghd("body_fat", "Body fat", record.percentage.value, "%", record.time, record.time))
            },
            HealthRecordDescriptor(BodyTemperatureRecord::class) { record ->
                listOf(record.toPghd("body_temperature", "Body temperature", record.temperature.inCelsius, "C", record.time, record.time))
            },
            summaryDescriptor(BodyWaterMassRecord::class, "body_water_mass", "Body water mass") { record ->
                record.time to record.time
            },
            summaryDescriptor(BoneMassRecord::class, "bone_mass", "Bone mass") { record ->
                record.time to record.time
            },
            summaryDescriptor(CervicalMucusRecord::class, "cervical_mucus", "Cervical mucus") { record ->
                record.time to record.time
            },
            summaryDescriptor(CyclingPedalingCadenceRecord::class, "cycling_pedaling_cadence", "Cycling pedaling cadence") { record ->
                record.startTime to record.endTime
            },
            HealthRecordDescriptor(DistanceRecord::class) { record ->
                listOf(record.toPghd("distance", "Distance", record.distance.inMeters, "m", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(ElevationGainedRecord::class) { record ->
                listOf(record.toPghd("elevation_gained", "Elevation gained", record.elevation.inMeters, "m", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(ExerciseSessionRecord::class) { record ->
                listOf(record.toPghd("exercise_session", "Exercise session", "Exercise type ${record.exerciseType}", "session", null, record.startTime, record.endTime))
            },
            HealthRecordDescriptor(FloorsClimbedRecord::class) { record ->
                listOf(record.toPghd("floors_climbed", "Floors climbed", record.floors, "floors", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(HeartRateRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("heart_rate", "Heart rate", sample.beatsPerMinute.toDouble(), "bpm", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(HeartRateVariabilityRmssdRecord::class) { record ->
                listOf(record.toPghd("heart_rate_variability_rmssd", "Heart rate variability", record.heartRateVariabilityMillis, "ms", record.time, record.time))
            },
            HealthRecordDescriptor(HeightRecord::class) { record ->
                listOf(record.toPghd("height", "Height", record.height.inMeters, "m", record.time, record.time))
            },
            HealthRecordDescriptor(HydrationRecord::class) { record ->
                listOf(record.toPghd("hydration", "Hydration", record.volume.inLiters, "L", record.startTime, record.endTime))
            },
            summaryDescriptor(IntermenstrualBleedingRecord::class, "intermenstrual_bleeding", "Intermenstrual bleeding") { record ->
                record.time to record.time
            },
            HealthRecordDescriptor(LeanBodyMassRecord::class) { record ->
                listOf(record.toPghd("lean_body_mass", "Lean body mass", record.mass.inKilograms, "kg", record.time, record.time))
            },
            summaryDescriptor(MenstruationFlowRecord::class, "menstruation_flow", "Menstruation flow") { record ->
                record.time to record.time
            },
            summaryDescriptor(MenstruationPeriodRecord::class, "menstruation_period", "Menstruation period") { record ->
                record.startTime to record.endTime
            },
            HealthRecordDescriptor(NutritionRecord::class) { record ->
                listOf(record.toPghd("nutrition", "Nutrition", record.energy?.inKilocalories ?: 0.0, "kcal", record.startTime, record.endTime))
            },
            summaryDescriptor(OvulationTestRecord::class, "ovulation_test", "Ovulation test") { record ->
                record.time to record.time
            },
            HealthRecordDescriptor(OxygenSaturationRecord::class) { record ->
                listOf(record.toPghd("oxygen_saturation", "Oxygen saturation", record.percentage.value, "%", record.time, record.time))
            },
            summaryDescriptor(PlannedExerciseSessionRecord::class, "planned_exercise_session", "Planned exercise session") { record ->
                record.startTime to record.endTime
            },
            HealthRecordDescriptor(PowerRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("power", "Power", sample.power.inWatts, "W", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(RespiratoryRateRecord::class) { record ->
                listOf(record.toPghd("respiratory_rate", "Respiratory rate", record.rate, "breaths/min", record.time, record.time))
            },
            HealthRecordDescriptor(RestingHeartRateRecord::class) { record ->
                listOf(record.toPghd("resting_heart_rate", "Resting heart rate", record.beatsPerMinute.toDouble(), "bpm", record.time, record.time))
            },
            summaryDescriptor(SexualActivityRecord::class, "sexual_activity", "Sexual activity") { record ->
                record.time to record.time
            },
            summaryDescriptor(SkinTemperatureRecord::class, "skin_temperature", "Skin temperature") { record ->
                record.startTime to record.endTime
            },
            HealthRecordDescriptor(SleepSessionRecord::class) { record ->
                listOf(record.toPghd("sleep_session", "Sleep session", "Sleep session", "session", null, record.startTime, record.endTime))
            },
            HealthRecordDescriptor(SpeedRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("speed", "Speed", sample.speed.inMetersPerSecond, "m/s", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(StepsCadenceRecord::class) { record ->
                record.samples.mapIndexed { index, sample ->
                    record.toPghd("steps_cadence", "Steps cadence", sample.rate, "steps/min", sample.time, sample.time, suffix = "sample:$index")
                }
            },
            HealthRecordDescriptor(StepsRecord::class) { record ->
                listOf(record.toPghd("steps", "Steps", record.count.toDouble(), "count", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(TotalCaloriesBurnedRecord::class) { record ->
                listOf(record.toPghd("total_calories", "Total calories burned", record.energy.inKilocalories, "kcal", record.startTime, record.endTime))
            },
            HealthRecordDescriptor(Vo2MaxRecord::class) { record ->
                listOf(record.toPghd("vo2_max", "VO2 max", record.vo2MillilitersPerMinuteKilogram, "mL/min/kg", record.time, record.time))
            },
            HealthRecordDescriptor(WeightRecord::class) { record ->
                listOf(record.toPghd("weight", "Weight", record.weight.inKilograms, "kg", record.time, record.time))
            },
            HealthRecordDescriptor(WheelchairPushesRecord::class) { record ->
                listOf(record.toPghd("wheelchair_pushes", "Wheelchair pushes", record.count.toDouble(), "count", record.startTime, record.endTime))
            }
        )

        private fun <T : Record> summaryDescriptor(
            recordClass: KClass<T>,
            recordType: String,
            displayName: String,
            timeRange: (T) -> Pair<Instant, Instant>
        ): HealthRecordDescriptor<T> =
            HealthRecordDescriptor(recordClass) { record ->
                val (startTime, endTime) = timeRange(record)
                listOf(record.toSummaryPghd(recordType, displayName, startTime, endTime))
            }

        private fun Record.toPghd(
            recordType: String,
            displayName: String,
            value: Double,
            unit: String,
            startTime: Instant,
            endTime: Instant,
            suffix: String? = null
        ): PghdRecordEntity =
            toPghd(recordType, displayName, value.toCompactText(), unit, value, startTime, endTime, suffix)

        private fun Record.toPghd(
            recordType: String,
            displayName: String,
            valueText: String,
            unit: String,
            numericValue: Double?,
            startTime: Instant,
            endTime: Instant,
            suffix: String? = null
        ): PghdRecordEntity {
            val baseUid = metadata.id.ifBlank {
                "${metadata.dataOrigin.packageName}:$recordType:${startTime.toEpochMilli()}:${endTime.toEpochMilli()}"
            }
            return PghdRecordEntity(
                uid = listOfNotNull("hc:$baseUid", suffix).joinToString(":"),
                recordType = recordType,
                displayName = displayName,
                startTimeEpochMillis = startTime.toEpochMilli(),
                endTimeEpochMillis = endTime.toEpochMilli(),
                unit = unit,
                valueText = valueText,
                numericValue = numericValue,
                sourceTag = PghdRecordEntity.SOURCE_HEALTH_CONNECT,
                sourcePackageName = metadata.dataOrigin.packageName
            )
        }

        private fun Record.toSummaryPghd(
            recordType: String,
            displayName: String,
            startTime: Instant,
            endTime: Instant
        ): PghdRecordEntity {
            return toPghd(
                recordType = recordType,
                displayName = displayName,
                valueText = toString(),
                unit = "record",
                numericValue = null,
                startTime = startTime,
                endTime = endTime
            )
        }

        private fun Double.toCompactText(): String =
            if (this % 1.0 == 0.0) this.toLong().toString() else String.format("%.2f", this)
    }
}
