package com.hackastic.decmed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackastic.decmed.ui.navigation.AppNavigation
import com.hackastic.decmed.ui.theme.DecMedTheme
import com.hackastic.decmed.viewmodel.SensorViewModel
import com.hackastic.decmed.viewmodel.ThemeViewModel

/**
 * Single-Activity entry point for the app.
 *
 * Change from previous version:
 * - Removed inline PGHDAppScreen composable. All UI is now in dedicated screen composables.
 * - Removed direct service start/stop. Service integration with sensor config is deferred.
 * - Removed permission requests from onCreate. Will be handled contextually per screen.
 * - Delegates to AppNavigation for all routing and screen composition.
 * - ViewModels are created here (activity-scoped) and passed to AppNavigation.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val sensorViewModel: SensorViewModel = viewModel()
            val dataViewModel: com.hackastic.decmed.viewmodel.DataViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()

            DecMedTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        sensorViewModel = sensorViewModel,
                        themeViewModel = themeViewModel,
                        dataViewModel = dataViewModel
                    )
                }
            }
        }
    }
}