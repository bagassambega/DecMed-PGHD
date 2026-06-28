package com.hackastic.decmed.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.components.InteractiveProcessToastHost
import com.hackastic.decmed.ui.components.ProcessToastEvent
import com.hackastic.decmed.ui.components.ProcessToastKind
import com.hackastic.decmed.viewmodel.SensorViewModel

@Composable
fun SensorConfigScreen(
    viewModel: SensorViewModel,
    onConfigSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val allEnabled = uiState.sensorConfigs.isNotEmpty() && uiState.sensorConfigs.all { it.isApproved }
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }
    val processToastEvent = uiState.errorMessage?.let {
        ProcessToastEvent(ProcessToastKind.Failure, it)
    } ?: uiState.lastMessage?.let {
        val kind = if (it.contains("saved", ignoreCase = true)) ProcessToastKind.Success else ProcessToastKind.Info
        ProcessToastEvent(kind, it)
    } ?: localToastEvent

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
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
                text = "Select the health data types to collect. Sensors are approved only when at least one mapped health data type is selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable all mapped health data",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Checkbox(
                    checked = allEnabled,
                    onCheckedChange = { checked -> viewModel.setAllSensorApproval(checked) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.sensorConfigs,
                    key = { it.sensorType }
                ) { config ->
                    val sensorInfo = uiState.availableSensors.find { it.type == config.sensorType }
                    val selectedRecordTypes = viewModel.selectedHealthRecordTypes(config.sensorType)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = config.sensorName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (config.isApproved) "Sensor access approved" else "Sensor access disabled",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = config.isApproved,
                                    onCheckedChange = { approved ->
                                        viewModel.toggleSensorApproval(config.sensorType, approved)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            sensorInfo?.healthDataTypes.orEmpty().forEach { healthData ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = healthData.recordType in selectedRecordTypes,
                                        onCheckedChange = { selected ->
                                            viewModel.toggleHealthDataType(config.sensorType, healthData.recordType, selected)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = healthData.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${healthData.recordType} - ${healthData.unit} - ${if (healthData.isEstimated) "derived" else "direct"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (!sensorInfo?.clinicalRelevance.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = sensorInfo?.clinicalRelevance.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                        Spacer(modifier = Modifier.height(6.dp))

                        IntervalDropdown(
                            label = "Collection interval",
                            selectedIntervalMs = config.collectionIntervalMs,
                            options = SensorViewModel.INTERVAL_OPTIONS_MS,
                            onIntervalSelected = { interval ->
                                viewModel.updateSensorInterval(config.sensorType, interval)
                            }
                        )
                        }
                    }
                }

                if (uiState.unavailableSensors.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sensors without enabled PGHD conversion",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(
                        items = uiState.unavailableSensors,
                        key = { it.type }
                    ) { sensor ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = sensor.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (sensor.isAvailable) {
                                        "No supported PGHD conversion is available for this sensor."
                                    } else {
                                        "Sensor is not available on this device."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val approvedCount = uiState.sensorConfigs.count { it.isApproved }
            Text(
                text = "$approvedCount of ${uiState.sensorConfigs.size} mapped sensors enabled",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Button(
                onClick = {
                    localToastEvent = ProcessToastEvent(ProcessToastKind.Info, "Saving sensor configuration.")
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
        InteractiveProcessToastHost(
            event = processToastEvent,
            onEventConsumed = {
                localToastEvent = null
                viewModel.clearMessages()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalDropdown(
    label: String,
    selectedIntervalMs: Int,
    options: List<Int>,
    onIntervalSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            value = "${selectedIntervalMs / 1000}s",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { interval ->
                DropdownMenuItem(
                    text = { Text("${interval / 1000}s") },
                    onClick = {
                        onIntervalSelected(interval)
                        expanded = false
                    }
                )
            }
        }
    }
}
