package com.hackastic.decmed.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.hackastic.decmed.data.local.database.SensorDatabase
import com.hackastic.decmed.data.repository.SensorConfigRepositoryImpl
import com.hackastic.decmed.domain.repository.SensorConfigRepository

/**
 * Top-level DataStore delegate.
 *
 * Why top-level: The preferencesDataStore delegate creates a singleton DataStore instance
 * tied to the Context. If defined inside a class, multiple instances could be created,
 * causing file corruption. The Android documentation mandates this be a top-level property.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "decmed_settings")

/**
 * Manual dependency injection container.
 *
 * Design rationale:
 * For a project of this size, a manual service locator avoids the complexity and
 * build-time cost of annotation-processing-based DI frameworks (Hilt, Dagger).
 * If the project grows to include multiple modules or complex scoping, migrate to Hilt.
 *
 * All dependencies are lazily initialized to avoid startup cost.
 */
class AppContainer(context: Context) {

    private val database: SensorDatabase by lazy {
        SensorDatabase.getDatabase(context)
    }

    val sensorConfigRepository: SensorConfigRepository by lazy {
        SensorConfigRepositoryImpl(database.sensorConfigDao())
    }

    val sensorRepository: com.hackastic.decmed.domain.repository.SensorRepository by lazy {
        com.hackastic.decmed.data.repository.SensorRepositoryImpl(database.sensorDao())
    }
}
