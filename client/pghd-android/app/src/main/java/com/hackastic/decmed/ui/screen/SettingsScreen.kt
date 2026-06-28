package com.hackastic.decmed.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.components.InteractiveProcessToastHost
import com.hackastic.decmed.ui.components.ProcessToastEvent
import com.hackastic.decmed.ui.components.ProcessToastKind
import com.hackastic.decmed.ui.theme.ThemeMode
import com.hackastic.decmed.viewmodel.ThemeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings screen with two sections:
 * 1. Theme selection (Dark / Light / System Default) — persisted in DataStore.
 * 2. Sensor configuration link — navigates to SensorConfigScreen for reconfiguration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel,
    onNavigateToSensorConfig: () -> Unit,
    onLogout: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val currentTheme by themeViewModel.themeMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // --- Theme Section ---
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ThemeOption(
                        icon = Icons.Default.SettingsBrightness,
                        label = "System Default",
                        description = "Follow your device's theme setting",
                        isSelected = currentTheme == ThemeMode.SYSTEM,
                        onClick = {
                            themeViewModel.setThemeMode(ThemeMode.SYSTEM)
                            localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Theme set to system default.")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ThemeOption(
                        icon = Icons.Default.LightMode,
                        label = "Light",
                        description = "Always use light theme",
                        isSelected = currentTheme == ThemeMode.LIGHT,
                        onClick = {
                            themeViewModel.setThemeMode(ThemeMode.LIGHT)
                            localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Light theme enabled.")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ThemeOption(
                        icon = Icons.Default.DarkMode,
                        label = "Dark",
                        description = "Always use dark theme",
                        isSelected = currentTheme == ThemeMode.DARK,
                        onClick = {
                            themeViewModel.setThemeMode(ThemeMode.DARK)
                            localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Dark theme enabled.")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Sensor Configuration Section ---
            Text(
                text = "Data Collection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Opening sensor configuration.")
                        coroutineScope.launch {
                            delay(SETTINGS_NAVIGATION_DELAY_MS)
                            onNavigateToSensorConfig()
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sensor Permissions",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Change which sensors the app can access for health data collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Logging out patient session.")
                    coroutineScope.launch {
                        delay(SETTINGS_NAVIGATION_DELAY_MS)
                        onLogout()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
        }
        InteractiveProcessToastHost(
            event = localToastEvent,
            onEventConsumed = { localToastEvent = null },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private const val SETTINGS_NAVIGATION_DELAY_MS = 800L

@Composable
private fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}
