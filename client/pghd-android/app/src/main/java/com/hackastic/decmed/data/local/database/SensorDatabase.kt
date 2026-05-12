package com.hackastic.decmed.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hackastic.decmed.data.local.dao.SensorConfigDao
import com.hackastic.decmed.data.local.dao.SensorDao
import com.hackastic.decmed.data.local.entity.SensorConfigEntity
import com.hackastic.decmed.data.local.entity.SensorData
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room Database configured with SQLCipher for AES-256 encryption.
 *
 * Version history:
 * - v1: sensor_data table only.
 * - v2: Added sensor_config table for persisting user sensor approval choices.
 *
 * Security Implication:
 * Data-at-rest encryption is mandatory for health data (PGHD).
 * The passphrase should ideally be backed by the Android Keystore in production.
 */
@Database(
    entities = [SensorData::class, SensorConfigEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SensorDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao
    abstract fun sensorConfigDao(): SensorConfigDao

    companion object {
        @Volatile
        private var INSTANCE: SensorDatabase? = null

        fun getDatabase(context: Context): SensorDatabase {
            return INSTANCE ?: synchronized(this) {
                // Initialize SQLCipher libraries
                System.loadLibrary("sqlcipher")

                // In a real production app, this passphrase MUST be randomly generated, 
                // encrypted with an Android Keystore key, and stored in SharedPreferences.
                // For this prototype, we use a static byte array.
                val passphrase = "Secure_PGHD_Key_2026".toByteArray()
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SensorDatabase::class.java,
                    "sensor_pghd.db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
