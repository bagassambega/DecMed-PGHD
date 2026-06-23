package com.hackastic.decmed.domain.usecase

import android.hardware.Sensor
import android.hardware.SensorManager
import com.hackastic.decmed.domain.model.SensorInfo
import com.hackastic.decmed.utils.SensorHealthDataMap

/**
 * Enumerates all known sensor types on the device and maps them to health data capabilities.
 *
 * Design rationale:
 * - We iterate over a predefined list of sensor type constants rather than using
 *   SensorManager.getSensorList(Sensor.TYPE_ALL), because getSensorList returns
 *   Sensor objects only for available sensors. We need to show unavailable sensors too
 *   (greyed out in the UI).
 * - The health data mapping is pulled from [SensorHealthDataMap], a static lookup table
 *   maintained in the utils layer.
 */
class GetAvailableSensorsUseCase {

    operator fun invoke(sensorManager: SensorManager): List<SensorInfo> {
        val mappedSensors = SensorHealthDataMap.allSensorTypes.map { (sensorType, healthInfo) ->
            val sensor = sensorManager.getDefaultSensor(sensorType)
            SensorInfo(
                type = sensorType,
                name = sensor?.name ?: healthInfo.displayName,
                isAvailable = sensor != null,
                healthDataCapabilities = healthInfo.healthData,
                healthDataTypes = healthInfo.healthDataTypes,
                clinicalRelevance = healthInfo.clinicalRelevance
            )
        }

        val mappedTypes = SensorHealthDataMap.allSensorTypes.keys
        val unmappedAvailableSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            .filter { sensor -> sensor.type !in mappedTypes }
            .distinctBy { it.type }
            .map { sensor ->
                SensorInfo(
                    type = sensor.type,
                    name = sensor.name,
                    isAvailable = true,
                    healthDataCapabilities = emptyList(),
                    healthDataTypes = emptyList(),
                    clinicalRelevance = "No PGHD health-data conversion is defined for this sensor."
                )
            }

        return (mappedSensors + unmappedAvailableSensors).sortedWith(
            compareByDescending<SensorInfo> { it.isAvailable }
                .thenBy { it.healthDataTypes.isEmpty() }
                .thenBy { it.name }
        )
    }
}
