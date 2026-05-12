package com.hackastic.decmed.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hackastic.decmed.di.dataStore
import com.hackastic.decmed.ui.screen.DataScreen
import com.hackastic.decmed.ui.screen.HomeScreen
import com.hackastic.decmed.ui.screen.SensorConfigScreen
import com.hackastic.decmed.ui.screen.SensorListScreen
import com.hackastic.decmed.ui.screen.SettingsScreen
import com.hackastic.decmed.ui.screen.TermsOfServiceScreen
import com.hackastic.decmed.viewmodel.DataViewModel
import com.hackastic.decmed.viewmodel.SensorViewModel
import com.hackastic.decmed.viewmodel.ThemeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Root navigation composable. Determines the start destination based on
 * persisted DataStore state:
 *
 * 1. ToS not accepted → TermsOfServiceScreen
 * 2. ToS accepted, setup not complete → SensorListScreen
 * 3. Both complete → HomeScreen
 *
 * While reading DataStore (async), a loading indicator is shown to avoid
 * a flash of the wrong screen.
 */
@Composable
fun AppNavigation(
    sensorViewModel: SensorViewModel,
    themeViewModel: ThemeViewModel,
    dataViewModel: DataViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Determine start destination asynchronously
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val tosAccepted = prefs[booleanPreferencesKey("tos_accepted")] ?: false
        val setupComplete = prefs[booleanPreferencesKey("setup_complete")] ?: false

        startDestination = when {
            !tosAccepted -> Screen.TermsOfService.route
            !setupComplete -> Screen.SensorList.route
            else -> Screen.Home.route
        }
    }

    if (startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        composable(Screen.TermsOfService.route) {
            val activity = LocalContext.current as Activity
            TermsOfServiceScreen(
                onAccept = {
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("tos_accepted")] = true
                        }
                    }
                    navController.navigate(Screen.SensorList.route) {
                        popUpTo(Screen.TermsOfService.route) { inclusive = true }
                    }
                },
                onDecline = {
                    activity.finishAffinity()
                }
            )
        }

        composable(Screen.SensorList.route) {
            SensorListScreen(
                viewModel = sensorViewModel,
                onNext = {
                    navController.navigate(Screen.SensorConfig.route)
                }
            )
        }

        composable(Screen.SensorConfig.route) {
            SensorConfigScreen(
                viewModel = sensorViewModel,
                onConfigSaved = {
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("setup_complete")] = true
                        }
                    }
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = sensorViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToData = {
                    navController.navigate(Screen.Data.route)
                }
            )
        }

        composable(Screen.Data.route) {
            DataScreen(
                viewModel = dataViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                themeViewModel = themeViewModel,
                onNavigateToSensorConfig = {
                    sensorViewModel.prepareForReconfiguration()
                    navController.navigate(Screen.SensorConfig.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
