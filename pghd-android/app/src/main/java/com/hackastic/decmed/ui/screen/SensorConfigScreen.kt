package com.hackastic.decmed.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.components.SensorCard
import com.hackastic.decmed.viewmodel.SensorViewModel

/**
 * Sensor configuration screen — step 3 of the onboarding flow.
 * Also accessible from Settings for reconfiguration.
 *
 * Displays only AVAILABLE sensors with toggle switches.
 * Each sensor can be expanded to view health data capabilities and clinical relevance.
 * The "Save Configuration" button persists selections to the Room database.
 */
@Composable
fun SensorConfigScreen(
    viewModel: SensorViewModel,
    onConfigSaved: () -> Unit
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
                text = "Configure Data Collection",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select which sensors the app may use to collect health data. " +
                    "Tap a sensor to see what data it provides. " +
                    "You can change these settings at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Sensor list with toggle switches
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.sensorConfigs,
                    key = { it.sensorType }
                ) { config ->
                    // Look up the full sensor info for health data details
                    val sensorInfo = uiState.availableSensors.find { it.type == config.sensorType }

                    SensorCard(
                        sensorName = config.sensorName,
                        isAvailable = true,
                        healthDataCapabilities = sensorInfo?.healthDataCapabilities ?: emptyList(),
                        clinicalRelevance = sensorInfo?.clinicalRelevance ?: "",
                        showToggle = true,
                        isApproved = config.isApproved,
                        onToggle = { approved ->
                            viewModel.toggleSensorApproval(config.sensorType, approved)
                        }
                    )
                }
            }

            // Summary + Save button
            val approvedCount = uiState.sensorConfigs.count { it.isApproved }
            Text(
                text = "$approvedCount of ${uiState.sensorConfigs.size} sensors approved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = {
                    viewModel.saveConfiguration()
                    onConfigSaved()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Save Configuration")
            }
        }
    }
}
