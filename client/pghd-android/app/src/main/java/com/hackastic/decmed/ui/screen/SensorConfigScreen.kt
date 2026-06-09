package com.hackastic.decmed.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.components.SensorCard
import com.hackastic.decmed.viewmodel.SensorViewModel

@Composable
fun SensorConfigScreen(
    viewModel: SensorViewModel,
    onConfigSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val allEnabled = uiState.sensorConfigs.isNotEmpty() && uiState.sensorConfigs.all { it.isApproved }

    LaunchedEffect(uiState.errorMessage, uiState.lastMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
            return@LaunchedEffect
        }
        uiState.lastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

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
                text = "Select sensors to enable for PGHD collection. Default is all enabled. You can set per-sensor collection interval now or later.",
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
                    text = "Enable all sensors",
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

                    Column {
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

            val approvedCount = uiState.sensorConfigs.count { it.isApproved }
            Text(
                text = "$approvedCount of ${uiState.sensorConfigs.size} sensors enabled",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Button(
                onClick = {
                    viewModel.saveConfiguration()
                    Toast.makeText(context, "Saving sensor configuration.", Toast.LENGTH_SHORT).show()
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
