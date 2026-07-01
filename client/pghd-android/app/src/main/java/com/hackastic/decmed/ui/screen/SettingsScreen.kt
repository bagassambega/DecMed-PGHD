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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.viewmodel.PatientAuthViewModel
import com.hackastic.decmed.viewmodel.PghdCollectionViewModel
import com.hackastic.decmed.viewmodel.PghdStressTestProgress
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
    pghdViewModel: PghdCollectionViewModel,
    patientAuthViewModel: PatientAuthViewModel,
    onNavigateToSensorConfig: () -> Unit,
    onLogout: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val currentTheme by themeViewModel.themeMode.collectAsState()
    val pghdUiState by pghdViewModel.uiState.collectAsState()
    val patientAuthUiState by patientAuthViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        patientAuthViewModel.refreshProfile()
    }

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

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Stress Test",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Generate 15 synthetic health streams for 7 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        enabled = pghdUiState.stressTestProgress?.isRunning != true,
                        onClick = {
                            localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Starting PGHD stress test.")
                            pghdViewModel.startStressTest()
                        }
                    ) {
                        Text(if (pghdUiState.stressTestProgress?.isRunning == true) "Running" else "Start")
                    }
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        patientAuthViewModel.refreshProfile()
                        showProfileDialog = true
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
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Patient Profile",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = patientAuthUiState.profile?.name?.takeIf { it.isNotBlank() }
                                ?: "View locally stored patient identity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
        pghdUiState.stressTestProgress?.let { progress ->
            StressTestProgressDialog(
                progress = progress,
                onCancel = pghdViewModel::cancelStressTest,
                onDismiss = pghdViewModel::dismissStressTestProgress
            )
        }
        if (showProfileDialog) {
            PatientProfileDialog(
                profile = patientAuthUiState.profile,
                onDismiss = { showProfileDialog = false }
            )
        }
    }
}

private const val SETTINGS_NAVIGATION_DELAY_MS = 800L

@Composable
private fun PatientProfileDialog(
    profile: PatientProfile?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Patient Profile") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (profile == null) {
                    Text(
                        text = "No local patient profile is available. Sign in again and complete the profile if requested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ProfileLine("Name", profile.name)
                    ProfileLine("NIK", profile.id)
                    ProfileLine("IOTA address", profile.iotaAddress)
                    ProfileLine("Birth place", profile.birthPlace)
                    ProfileLine("Date of birth", profile.dateOfBirth)
                    ProfileLine("Gender", profile.gender)
                    ProfileLine("Religion", profile.religion)
                    ProfileLine("Education", profile.education)
                    ProfileLine("Occupation", profile.occupation)
                    ProfileLine("Marital status", profile.maritalStatus)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun ProfileLine(label: String, value: String?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "-",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StressTestProgressDialog(
    progress: PghdStressTestProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!progress.isRunning) onDismiss()
        },
        title = {
            Text(if (progress.errorMessage == null) "PGHD Stress Test" else "PGHD Stress Test Failed")
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { progress.generationFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Generated: ${progress.generatedRecords} / ${progress.totalRecords} records",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Batches formed: ${progress.formedBatchCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Current next-batch data: ${progress.currentBatchBytes.toReadableBytes()} (${progress.currentBatchRecordCount} records)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Sent: ${progress.sentBatchCount} | Sending: ${progress.pendingBatchCount} | Waiting: ${progress.waitingBatchCount} | Failed: ${progress.failedBatchCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        progress.errorMessage != null -> progress.errorMessage
                        progress.isRunning -> "Generating synthetic PGHD records. Batching and PRE submission use the normal app pipeline."
                        progress.generatedComplete -> "Generation finished. Keep this dialog open to watch batch delivery continue."
                        else -> "Preparing stress test."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (progress.isRunning) onCancel() else onDismiss()
                }
            ) {
                Text(if (progress.isRunning) "Cancel" else "OK")
            }
        }
    )
}

private fun Long.toReadableBytes(): String {
    val mib = this / (1024.0 * 1024.0)
    return if (mib >= 1.0) {
        String.format(java.util.Locale.getDefault(), "%.2f MB", mib)
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f KB", this / 1024.0)
    }
}

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
