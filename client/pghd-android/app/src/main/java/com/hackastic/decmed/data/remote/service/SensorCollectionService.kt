package com.hackastic.decmed.data.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import com.hackastic.decmed.utils.DecmedLog
import androidx.core.app.NotificationCompat
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.data.local.database.SensorDatabase
import com.hackastic.decmed.data.local.entity.SensorData
import com.hackastic.decmed.data.pghd.AndroidSensorPghdMapper
import com.hackastic.decmed.data.pghd.PghdInputSanitizer
import com.hackastic.decmed.domain.model.SensorConfigModel
import com.hackastic.decmed.utils.SensorHealthDataMap
import com.hackastic.decmed.worker.PghdSizeThresholdTrigger
import com.hackastic.decmed.worker.PghdTimeThresholdTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Foreground service that collects data from selected Android sensors and
 * writes batched records to the encrypted Room database.
 *
 * Data model follows the Google Health Connect record schema:
 *   https://developers.google.com/health/reference/rest/v1/users.dataSources.datasets
 *
 * Each [SensorData] row stored here maps 1:1 to a Health Connect DataPoint:
 *   - [SensorData.dataType]  → DataSource.dataType.name
 *   - start/endTimeEpochMillis → DataPoint.startTimeNanos / endTimeNanos (ms precision)
 *   - value / valueX/Y/Z     → DataPoint.value[] fpVal
 *   - [SensorData.accuracy]  → DataPoint accuracy qualifier
 *   - [SensorData.dataOrigin]→ DataSource.application.packageName
 */
class SensorCollectionService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START_COLLECTION = "com.hackastic.decmed.action.START_COLLECTION"
        const val ACTION_STOP_COLLECTION  = "com.hackastic.decmed.action.STOP_COLLECTION"
        const val EXTRA_SENSOR_TYPES      = "extra_sensor_types"
        const val EXTRA_SENSOR_INTERVALS_MS = "extra_sensor_intervals_ms"

        private const val TAG = "SensorCollection"
        private const val CHANNEL_ID = "PGHD_Sensor_Channel"
        private const val NOTIFICATION_ID = 1
        private const val DATA_ORIGIN = "com.hackastic.decmed"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var database: SensorDatabase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicFlushJob: Job? = null

    private val sensorDataBuffer = mutableListOf<SensorData>()
    private val sensorIntervalsMs  = mutableMapOf<Int, Int>()
    private val sensorLastEmitMs   = mutableMapOf<Int, Long>()

    // Track the last-known accuracy per sensor type for annotating records.
    private val sensorAccuracy = mutableMapOf<Int, Int>()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        database = SensorDatabase.getDatabase(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP_COLLECTION -> {
                stopSelf()
                START_NOT_STICKY
            }
            ACTION_START_COLLECTION, null -> {
                startForeground(NOTIFICATION_ID, createNotification())
                val sensorTypes = intent?.getIntArrayExtra(EXTRA_SENSOR_TYPES)?.toList().orEmpty()
                val intervals   = intent?.getIntArrayExtra(EXTRA_SENSOR_INTERVALS_MS)?.toList().orEmpty()
                registerSensors(sensorTypes, intervals)
                START_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        periodicFlushJob?.cancel()
        sensorManager.unregisterListener(this)
        flushBufferToDatabase()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Sensor registration ────────────────────────────────────────────────────

    private fun registerSensors(sensorTypes: List<Int>, intervals: List<Int>) {
        sensorManager.unregisterListener(this)
        periodicFlushJob?.cancel()
        sensorIntervalsMs.clear()
        sensorLastEmitMs.clear()
        sensorAccuracy.clear()

        if (sensorTypes.isEmpty()) {
            DecmedLog.w(TAG, "No sensor types provided. Stopping service.")
            stopSelf()
            return
        }

        sensorTypes.forEachIndexed { index, sensorType ->
            val intervalMs = intervals.getOrNull(index) ?: Env.pghdDefaultSensorIntervalMs
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor == null) {
                DecmedLog.w(TAG, "Sensor type $sensorType unavailable on this device — skipped.")
                return@forEachIndexed
            }

            sensorIntervalsMs[sensorType] = intervalMs
            sensorLastEmitMs[sensorType]  = 0L
            sensorAccuracy[sensorType]    = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

            // Request sampling at the configured interval; clamp to Android minimum (20ms).
            val samplingPeriodUs = (intervalMs * 1000).coerceAtLeast(20_000)
            sensorManager.registerListener(this, sensor, samplingPeriodUs)
        }

        if (sensorIntervalsMs.isEmpty()) {
            DecmedLog.w(TAG, "No sensors were registered successfully. Stopping service.")
            stopSelf()
        } else {
            startPeriodicFlush()
        }
    }

    private fun startPeriodicFlush() {
        val flushIntervalMs = (sensorIntervalsMs.values
            .minOrNull()
            ?.coerceAtLeast(1_000)
            ?: Env.pghdDefaultSensorIntervalMs).toLong()
        periodicFlushJob = serviceScope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flushBufferToDatabase()
                PghdTimeThresholdTrigger.scheduleBatchIfElapsed(
                    context = applicationContext,
                    database = database,
                    sourceLabel = "phone sensor periodic flush"
                )
            }
        }
        DecmedLog.i(TAG, "Started periodic sensor buffer flush every ${flushIntervalMs}ms")
    }

    // ── SensorEventListener ────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        val now = System.currentTimeMillis()
        val sensorType = ev.sensor.type
        val intervalMs = sensorIntervalsMs[sensorType] ?: return
        val lastEmit   = sensorLastEmitMs[sensorType]  ?: 0L

        // Rate-limit: emit at most once per configured interval.
        if (now - lastEmit < intervalMs) return
        sensorLastEmitMs[sensorType] = now

        val (dataType, unit) = dataTypeAndUnit(sensorType)
        val values = ev.values

        // Scalar sensors (heart rate, step counter, proximity, light, pressure…)
        // populate [value]; vector sensors populate [valueX/Y/Z] and also [value]
        // as the primary magnitude for convenience.
        val record = SensorData(
            dataType              = dataType,
            sensorType            = sensorType,
            startTimeEpochMillis  = now,
            endTimeEpochMillis    = now,
            unit                  = unit,
            value                 = values.getOrNull(0),
            valueX                = values.getOrNull(0),
            valueY                = values.getOrNull(1),
            valueZ                = values.getOrNull(2),
            accuracy              = sensorAccuracy[sensorType] ?: 3,
            dataOrigin            = DATA_ORIGIN
        )

        synchronized(sensorDataBuffer) {
            sensorDataBuffer.add(record)
            if (sensorDataBuffer.size >= Env.pghdSensorBatchSize) {
                flushBufferToDatabase()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor?.let { sensorAccuracy[it.type] = accuracy }
    }

    // ── Database flush ─────────────────────────────────────────────────────────

    private fun flushBufferToDatabase() {
        val batch = synchronized(sensorDataBuffer) {
            val copy = sensorDataBuffer.toList()
            sensorDataBuffer.clear()
            copy
        }
        if (batch.isEmpty()) return

        serviceScope.launch {
            try {
                database.sensorDao().insertAll(batch)
                val enabledRecordTypesBySensor = loadEnabledRecordTypesBySensor()
                val pghdRecords = PghdInputSanitizer.sanitizeRecords(
                    AndroidSensorPghdMapper.toPghdRecords(batch, enabledRecordTypesBySensor)
                )
                if (pghdRecords.isNotEmpty()) {
                    database.pghdRecordDao().upsertAll(pghdRecords)
                    PghdSizeThresholdTrigger.scheduleBatchIfExceeded(
                        context = applicationContext,
                        database = database,
                        sourceLabel = "phone sensor flush"
                    )
                    PghdTimeThresholdTrigger.scheduleBatchIfElapsed(
                        context = applicationContext,
                        database = database,
                        sourceLabel = "phone sensor flush"
                    )
                }
                DecmedLog.d(TAG, "Wrote ${batch.size} raw phone sensor rows and ${pghdRecords.size} semantic phone_sensor PGHD records to local DB.")
            } catch (e: Exception) {
                DecmedLog.e(TAG, "DB write error: ${e.message}", e)
            }
        }
    }

    private suspend fun loadEnabledRecordTypesBySensor(): Map<Int, Set<String>> {
        return database.sensorConfigDao().getApprovedConfigs().first().associate { config ->
            val selected = config.healthDataDescription
                .split(SensorConfigModel.SELECTED_RECORD_TYPE_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.contains(" ") }
                .toSet()
            val fallback = SensorHealthDataMap.recordTypesFor(config.sensorType)
            config.sensorType to (selected.ifEmpty { fallback })
        }
    }

    // ── Health Connect data-type mapping ───────────────────────────────────────

    /**
     * Maps Android sensor type constants to Health Connect–style data type strings and units.
     *
     * Naming convention follows com.google.* namespace used by Health Connect:
     *   https://developers.google.com/health/reference/rest/v1/DataType
     */
    private fun dataTypeAndUnit(sensorType: Int): Pair<String, String> = when (sensorType) {

        // ── Vital / clinical ──────────────────────────────────────────────────
        Sensor.TYPE_HEART_RATE ->
            "com.google.heart_rate.bpm"            to "bpm"

        Sensor.TYPE_HEART_BEAT ->
            "com.google.heart_rate.variability"    to "ms"

        // ── Activity / motion ─────────────────────────────────────────────────
        Sensor.TYPE_STEP_COUNTER ->
            "com.google.step_count.cumulative"     to "count"

        Sensor.TYPE_STEP_DETECTOR ->
            "com.google.step_count.delta"          to "count"

        Sensor.TYPE_SIGNIFICANT_MOTION ->
            "com.google.activity.segment"          to "event"

        Sensor.TYPE_STATIONARY_DETECT ->
            "com.google.activity.stationary"       to "event"

        Sensor.TYPE_MOTION_DETECT ->
            "com.google.activity.motion"           to "event"

        // ── Kinematics ────────────────────────────────────────────────────────
        Sensor.TYPE_ACCELEROMETER ->
            "com.google.acceleration.vector"       to "m/s^2"

        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED ->
            "com.google.acceleration.vector.raw"   to "m/s^2"

        Sensor.TYPE_LINEAR_ACCELERATION ->
            "com.google.acceleration.linear"       to "m/s^2"

        Sensor.TYPE_GRAVITY ->
            "com.google.acceleration.gravity"      to "m/s^2"

        Sensor.TYPE_GYROSCOPE ->
            "com.google.gyroscope.vector"          to "rad/s"

        Sensor.TYPE_GYROSCOPE_UNCALIBRATED ->
            "com.google.gyroscope.vector.raw"      to "rad/s"

        Sensor.TYPE_ROTATION_VECTOR ->
            "com.google.rotation.vector"           to "unitless"

        Sensor.TYPE_GAME_ROTATION_VECTOR ->
            "com.google.rotation.game_vector"      to "unitless"

        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ->
            "com.google.rotation.geo_vector"       to "unitless"

        // ── Magnetic field ────────────────────────────────────────────────────
        Sensor.TYPE_MAGNETIC_FIELD ->
            "com.google.magnetic_field.vector"     to "μT"

        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED ->
            "com.google.magnetic_field.vector.raw" to "μT"

        // ── Environmental ─────────────────────────────────────────────────────
        Sensor.TYPE_LIGHT ->
            "com.google.ambient_light.lux"         to "lux"

        Sensor.TYPE_PRESSURE ->
            "com.google.pressure.hPa"              to "hPa"

        Sensor.TYPE_AMBIENT_TEMPERATURE ->
            "com.google.body.temperature.ambient"  to "°C"

        Sensor.TYPE_RELATIVE_HUMIDITY ->
            "com.google.humidity.percent"          to "%"

        // ── Proximity / wear ──────────────────────────────────────────────────
        Sensor.TYPE_PROXIMITY ->
            "com.google.proximity.cm"              to "cm"

        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT ->
            "com.google.device.on_body"            to "boolean"

        // ── Device form factor ────────────────────────────────────────────────
        Sensor.TYPE_HINGE_ANGLE ->
            "com.google.device.hinge_angle"        to "degrees"

        else ->
            "com.google.sensor.raw"                to "raw"
    }

    // ── Notification helpers ───────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PGHD Sensor Collection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing sensor data collection for DecMed PGHD."
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DecMed PGHD Collection Active")
            .setContentText("Collecting approved health sensor data.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
}
