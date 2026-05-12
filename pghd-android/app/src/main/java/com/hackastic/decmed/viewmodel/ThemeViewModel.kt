package com.hackastic.decmed.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.di.dataStore
import com.hackastic.decmed.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the user's theme preference.
 *
 * Reads/writes to DataStore Preferences. Exposes a StateFlow<ThemeMode>
 * so the root composable (DecMedTheme) can reactively switch themes
 * without restarting the Activity.
 *
 * The stateIn operator with WhileSubscribed(5000) keeps the flow active
 * for 5 seconds after the last subscriber disconnects, avoiding
 * unnecessary re-reads during configuration changes (e.g., screen rotation).
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }

    private val store = application.dataStore

    val themeMode: StateFlow<ThemeMode> = store.data
        .map { prefs ->
            when (prefs[THEME_KEY]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            store.edit { prefs ->
                prefs[THEME_KEY] = mode.name
            }
        }
    }
}
