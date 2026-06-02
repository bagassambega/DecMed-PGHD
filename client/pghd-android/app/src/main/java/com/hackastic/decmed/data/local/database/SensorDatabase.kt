package com.hackastic.decmed.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hackastic.decmed.data.local.dao.SensorConfigDao
import com.hackastic.decmed.data.local.dao.PghdRecordDao
import com.hackastic.decmed.data.local.dao.SensorDao
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.local.entity.SensorConfigEntity
import com.hackastic.decmed.data.local.entity.SensorData
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room Database configured with SQLCipher for AES-256 encryption at rest.
 *
 * Version history:
 *   v1 — sensor_data table (initial).
 *   v2 — Added sensor_config table.
 *   v3 — Minor schema tweaks.
 *   v4 — sensor_data updated to Health Connect–style schema:
 *          • recordType  → dataType  (Health Connect data type string)
 *          • Added accuracy   INT    (SensorEvent.accuracy, 0–3)
 *          • Added dataOrigin TEXT   (package-name source identifier)
 *          • Added index on (dataType, endTimeEpochMillis)
 *   v5 — Added pghd_records table for encrypted Health Connect and manual PGHD.
 *
 * Security note:
 *   In production the passphrase MUST be randomly generated, wrapped with an
 *   Android Keystore key, and stored in EncryptedSharedPreferences.
 *   The static string below is a prototype placeholder only.
 */
@Database(
    entities = [SensorData::class, SensorConfigEntity::class, PghdRecordEntity::class],
    version = 5,
    exportSchema = false
)
abstract class SensorDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao
    abstract fun sensorConfigDao(): SensorConfigDao
    abstract fun pghdRecordDao(): PghdRecordDao

    companion object {
        @Volatile
        private var INSTANCE: SensorDatabase? = null

        fun getDatabase(context: Context): SensorDatabase {
            return INSTANCE ?: synchronized(this) {
                System.loadLibrary("sqlcipher")

                val passphrase = "Secure_PGHD_Key_2026".toByteArray()
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SensorDatabase::class.java,
                    "sensor_pghd.db"
                )
                    .openHelperFactory(factory)
                    // Development convenience — replace with proper Migration objects
                    // before shipping to production.
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
