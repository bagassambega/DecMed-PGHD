package com.hackastic.decmed.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.hackastic.decmed.BuildConfig
import com.hackastic.decmed.data.health.HealthConnectPghdClient
import com.hackastic.decmed.data.local.security.PatientSecureStorage
import com.hackastic.decmed.data.local.database.SensorDatabase
import com.hackastic.decmed.data.patient.DeterministicPatientCryptoBridge
import com.hackastic.decmed.data.remote.IotaPatientGateway
import com.hackastic.decmed.data.remote.PrePghdClient
import com.hackastic.decmed.data.repository.PatientAuthRepositoryImpl
import com.hackastic.decmed.data.repository.PghdBatchRepositoryImpl
import com.hackastic.decmed.data.repository.PghdRepositoryImpl
import com.hackastic.decmed.data.repository.SensorConfigRepositoryImpl
import com.hackastic.decmed.domain.repository.PatientAuthRepository
import com.hackastic.decmed.domain.repository.PatientCryptoBridge
import com.hackastic.decmed.domain.repository.PghdBatchRepository
import com.hackastic.decmed.domain.repository.PghdRepository
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
    private val appContext = context.applicationContext

    private val database: SensorDatabase by lazy {
        SensorDatabase.getDatabase(appContext)
    }

    private val patientCryptoBridge: PatientCryptoBridge by lazy {
        DeterministicPatientCryptoBridge()
    }

    private val patientSecureStorage: PatientSecureStorage by lazy {
        PatientSecureStorage()
    }

    private val prePghdClient: PrePghdClient by lazy {
        PrePghdClient(BuildConfig.PRE_BASE_URL)
    }

    private val iotaPatientGateway: IotaPatientGateway by lazy {
        IotaPatientGateway(BuildConfig.IOTA_RPC_URL)
    }

    val patientAuthRepository: PatientAuthRepository by lazy {
        PatientAuthRepositoryImpl(
            appContext.dataStore,
            patientCryptoBridge,
            patientSecureStorage,
            prePghdClient,
            iotaPatientGateway
        )
    }

    val sensorConfigRepository: SensorConfigRepository by lazy {
        SensorConfigRepositoryImpl(database.sensorConfigDao())
    }

    val sensorRepository: com.hackastic.decmed.domain.repository.SensorRepository by lazy {
        com.hackastic.decmed.data.repository.SensorRepositoryImpl(database.sensorDao())
    }

    val pghdRepository: PghdRepository by lazy {
        PghdRepositoryImpl(database.pghdRecordDao())
    }

    val pghdBatchRepository: PghdBatchRepository by lazy {
        PghdBatchRepositoryImpl(database.pghdBatchDao(), prePghdClient)
    }

    val healthConnectPghdClient: HealthConnectPghdClient by lazy {
        HealthConnectPghdClient(appContext)
    }
}
