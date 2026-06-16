package com.hackastic.decmed.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class PghdCollectionState(
    val enabled: Boolean = false,
    val startedAtEpochMillis: Long? = null,
    val stoppedAtEpochMillis: Long? = null
)

class PghdCollectionStateRepository(
    private val dataStore: DataStore<Preferences>
) {
    val state: Flow<PghdCollectionState> = dataStore.data.map { prefs ->
        PghdCollectionState(
            enabled = prefs[Keys.enabled] ?: false,
            startedAtEpochMillis = prefs[Keys.startedAt],
            stoppedAtEpochMillis = prefs[Keys.stoppedAt]
        )
    }

    suspend fun isEnabled(): Boolean =
        state.first().enabled

    suspend fun start(nowEpochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val alreadyEnabled = prefs[Keys.enabled] ?: false
            prefs[Keys.enabled] = true
            if (!alreadyEnabled || prefs[Keys.startedAt] == null) {
                prefs[Keys.startedAt] = nowEpochMillis
            }
            prefs.remove(Keys.stoppedAt)
        }
    }

    suspend fun restartWindow(nowEpochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.enabled] = true
            prefs[Keys.startedAt] = nowEpochMillis
            prefs.remove(Keys.stoppedAt)
        }
    }

    suspend fun stop(nowEpochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.enabled] = false
            prefs[Keys.stoppedAt] = nowEpochMillis
        }
    }

    private object Keys {
        val enabled = booleanPreferencesKey("pghd_collection_enabled")
        val startedAt = longPreferencesKey("pghd_collection_started_at")
        val stoppedAt = longPreferencesKey("pghd_collection_stopped_at")
    }
}
