package com.hackastic.decmed.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.components.SensorCard
import com.hackastic.decmed.viewmodel.SensorViewModel

/**
 * Sensor enumeration screen — step 2 of the onboarding flow.
 *
 * Flow:
 * 1. User sees an explanation including the default-all-enabled policy.
 * 2. User taps "Start Enumeration".
 * 3. ViewModel enumerates all known sensor types against the device SensorManager.
 * 4. Results are shown in two sections (Available / Unavailable) with a banner
 *    confirming that all available sensors are enabled by default.
 * 5. User taps "Next" to proceed to the configuration screen where individual
 *    sensors can be toggled and intervals adjusted.
 */
@Composable
fun SensorListScreen(
    viewModel: SensorViewModel,
    onNext: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Device Sensors",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                // ── Pre-enumeration ───────────────────────────────────────────
                !uiState.enumerationComplete && !uiState.isEnumerating -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Sensor Enumeration",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "The app will scan your device to identify all available hardware " +
                                "sensors and determine which health data your device can collect.\n\n" +
                                "All available sensors are enabled by default. You can review and " +
                                "adjust individual sensors and collection intervals on the next screen " +
                                "before starting data collection.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Default-all-enabled callout
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "All available sensors will be enabled by default. " +
                                        "You can disable any sensor on the next screen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { viewModel.enumerateSensors() }) {
                            Text("Start Enumeration")
                        }
                    }
                }

                // ── Enumerating ───────────────────────────────────────────────
                uiState.isEnumerating -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Scanning device sensors…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────────────
                else -> {
                    // "All enabled" banner
                    if (uiState.availableSensors.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "All ${uiState.availableSensors.size} available sensors are " +
                                        "enabled by default. Tap Next to review or adjust before collecting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            // Available sensors
                            if (uiState.availableSensors.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Available Sensors (${uiState.availableSensors.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(uiState.availableSensors) { sensor ->
                                    SensorCard(
                                        sensorName = sensor.name,
                                        isAvailable = true,
                                        healthDataCapabilities = sensor.healthDataCapabilities,
                                        clinicalRelevance = sensor.clinicalRelevance
                                    )
                                }
                            }

                            // Unavailable sensors
                            if (uiState.unavailableSensors.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Unavailable on This Device (${uiState.unavailableSensors.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(uiState.unavailableSensors) { sensor ->
                                    SensorCard(
                                        sensorName = sensor.name,
                                        isAvailable = false,
                                        healthDataCapabilities = sensor.healthDataCapabilities,
                                        clinicalRelevance = sensor.clinicalRelevance
                                    )
                                }
                            }

                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }

                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("Next — Review & Configure Sensors")
                    }
                }
            }
        }
    }
}