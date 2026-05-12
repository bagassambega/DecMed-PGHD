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
        return SensorHealthDataMap.allSensorTypes.map { (sensorType, healthInfo) ->
            val sensor = sensorManager.getDefaultSensor(sensorType)
            SensorInfo(
                type = sensorType,
                name = sensor?.name ?: healthInfo.displayName,
                isAvailable = sensor != null,
                healthDataCapabilities = healthInfo.healthData,
                clinicalRelevance = healthInfo.clinicalRelevance
            )
        }.sortedWith(
            // Available sensors first, then alphabetical by name
            compareByDescending<SensorInfo> { it.isAvailable }
                .thenBy { it.name }
        )
    }
}
