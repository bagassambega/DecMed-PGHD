package com.hackastic.decmed.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hackastic.decmed.data.local.dao.PghdBatchDao
import com.hackastic.decmed.data.local.dao.SensorConfigDao
import com.hackastic.decmed.data.local.dao.PghdRecordDao
import com.hackastic.decmed.data.local.dao.SensorDao
import com.hackastic.decmed.data.local.entity.PghdBatchDataPointEntity
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.local.entity.SensorConfigEntity
import com.hackastic.decmed.data.local.entity.SensorData

/**
 * Room Database for local PGHD collection and encrypted batch retry state.
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
 *   v7 — PGHD batch table now stores encrypted envelopes and retry state.
 */
@Database(
    entities = [
        SensorData::class,
        SensorConfigEntity::class,
        PghdRecordEntity::class,
        PghdBatchEntity::class,
        PghdBatchDataPointEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class SensorDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao
    abstract fun sensorConfigDao(): SensorConfigDao
    abstract fun pghdRecordDao(): PghdRecordDao
    abstract fun pghdBatchDao(): PghdBatchDao

    companion object {
        @Volatile
        private var INSTANCE: SensorDatabase? = null

        fun getDatabase(context: Context): SensorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SensorDatabase::class.java,
                    "sensor_pghd.db"
                )
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
