package com.hackastic.decmed

import android.app.Application
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.di.AppContainer
import com.hackastic.decmed.iota.DecmedIotaNative
import com.hackastic.decmed.utils.DecmedLog
import com.hackastic.decmed.worker.PghdWorkScheduler

/**
 * Custom Application subclass for app-wide initialization.
 *
 * Registered in AndroidManifest.xml via android:name=".MainApplication".
 * Initializes the AppContainer (manual DI) on startup so that all components
 * (ViewModels, Services) can access shared dependencies.
 */
class MainApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        logRuntimeEnvironment()
        DecmedIotaNative.initialize(this)
        container = AppContainer(this)
        PghdWorkScheduler.scheduleAll(this)
    }

    private fun logRuntimeEnvironment() {
        DecmedLog.i(
            TAG,
            "Runtime env initialized: " +
                "PRE_BASE_URL=${Env.preBaseUrl} " +
                "IOTA_RPC_URL=${Env.iotaRpcUrl} " +
                "GAS_STATION_BASE_URL=${Env.gasStationBaseUrl} " +
                "DECMED_PACKAGE_ID=${Env.decmedPackageId} " +
                "ADDRESS_ID=${Env.decmedAddressIdObjectId}@${Env.decmedAddressIdObjectVersion} " +
                "PATIENT_ID_ACCOUNT=${Env.decmedPatientIdAccountObjectId}@${Env.decmedPatientIdAccountObjectVersion} " +
                "HOSPITAL_ID_METADATA=${Env.decmedHospitalIdMetadataObjectId}@${Env.decmedHospitalIdMetadataObjectVersion} " +
                "HOSPITAL_PERSONNEL_ID_ACCOUNT=${Env.decmedHospitalPersonnelIdAccountObjectId}@${Env.decmedHospitalPersonnelIdAccountObjectVersion} " +
                "PGHD_EARLY_TRIGGER_BYTES=${Env.pghdEarlyTriggerBytes} " +
                "hasGasStationToken=${Env.gasStationToken.isNotBlank()}"
        )
    }

    private companion object {
        const val TAG = "MainApplication"
    }
}
