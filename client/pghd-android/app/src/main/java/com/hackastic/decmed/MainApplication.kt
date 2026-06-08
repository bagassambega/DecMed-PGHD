package com.hackastic.decmed

import android.app.Application
import com.hackastic.decmed.di.AppContainer
import com.hackastic.decmed.iota.DecmedIotaNative
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
        DecmedIotaNative.initialize(this)
        container = AppContainer(this)
        PghdWorkScheduler.scheduleAll(this)
    }
}
