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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackastic.decmed.data.local.entity.SensorData
import com.hackastic.decmed.data.local.database.SensorDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground Service responsible for continuous PGHD collection.
 * Architecture Note:
 * This service runs decoupled from the UI layer to survive activity destruction.
 */
class SensorCollectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: SensorDatabase
    private val sensorDataBuffer = mutableListOf<SensorData>()
    
    private val CHANNEL_ID = "PGHD_Sensor_Channel"
    private val BATCH_SIZE = 100 // Memory implication: flush to disk after 100 events

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        database = SensorDatabase.getDatabase(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        // Start Foreground Service with 'health' type to comply with Android 14+ rules
        startForeground(1, notification)
        
        registerSensors()
        
        // Sticky means if the system kills the service, it will recreate it automatically.
        return START_STICKY 
    }

    private fun registerSensors() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Hardware batching consideration: 
        // 50000 microseconds (50ms) = 20Hz.
        // maxReportLatencyUs = 10000000 (10 seconds). The AP will sleep and wake every 10s to process 200 events.
        accelerometer?.let {
            sensorManager.registerListener(this, it, 50000, 10000000)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, 50000, 10000000)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val data = SensorData(
                sensorType = it.sensor.type,
                timestamp = System.currentTimeMillis(),
                value0 = it.values[0],
                value1 = it.values[1],
                value2 = it.values[2]
            )
            
            synchronized(sensorDataBuffer) {
                sensorDataBuffer.add(data)
                if (sensorDataBuffer.size >= BATCH_SIZE) {
                    flushBufferToDatabase()
                }
            }
        }
    }

    private fun flushBufferToDatabase() {
        val batchToSave = synchronized(sensorDataBuffer) {
            val copy = sensorDataBuffer.toList()
            sensorDataBuffer.clear()
            copy
        }
        
        serviceScope.launch {
            try {
                database.sensorDao().insertAll(batchToSave)
                Log.d("SensorCollection", "Inserted ${batchToSave.size} records to encrypted DB")
            } catch (e: Exception) {
                Log.e("SensorCollection", "Error inserting records: ${e.message}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Unused but required by interface
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PGHD Sensor Collection",
            NotificationManager.IMPORTANCE_LOW // Low priority to avoid ringing/vibrating
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DecMed PGHD Collection")
            .setContentText("Actively collecting health sensor data securely.")
            // Requires an icon in a real app, using system default here for compilation
            .setSmallIcon(android.R.drawable.ic_menu_compass) 
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        flushBufferToDatabase() // flush any remaining data
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't support binding in this service, only start/stop
    }
}
