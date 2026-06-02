package com.hackastic.decmed.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import com.hackastic.decmed.data.remote.service.SensorCollectionService
import com.hackastic.decmed.ui.theme.AvailableGreen
import com.hackastic.decmed.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SensorViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToPghdCollection: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val approvedSensors = uiState.sensorConfigs.filter { it.isApproved }
    val selectedCount = approvedSensors.count { uiState.collectionSelection[it.sensorType] == true }
    val allSelected = approvedSensors.isNotEmpty() && selectedCount == approvedSensors.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DecMed PGHD") },
                actions = {
                    IconButton(onClick = onNavigateToPghdCollection) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = "PGHD Collection"
                        )
                    }
                    IconButton(onClick = onNavigateToData) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "View Data"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (uiState.isCollecting) "Collection Running" else "Collection Ready",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$selectedCount of ${approvedSensors.size} enabled sensors selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select all enabled sensors",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { checked -> viewModel.setAllCollectionSensorsSelected(checked) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (approvedSensors.isEmpty()) {
                Text(
                    text = "No sensors enabled. Open Settings to configure sensors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(approvedSensors) { config ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.collectionSelection[config.sensorType] == true,
                                        onCheckedChange = { selected ->
                                            viewModel.toggleCollectionSensorSelection(config.sensorType, selected)
                                        }
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = AvailableGreen
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = config.sensorName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = config.healthDataDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                IntervalDropdown(
                                    selectedIntervalMs = uiState.collectionIntervals[config.sensorType]
                                        ?: config.collectionIntervalMs,
                                    options = SensorViewModel.INTERVAL_OPTIONS_MS,
                                    onIntervalSelected = { interval ->
                                        viewModel.setCollectionInterval(config.sensorType, interval)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = selectedCount > 0 && !uiState.isCollecting,
                    onClick = {
                        val selectedConfig = viewModel.getActiveCollectionConfig()
                        if (selectedConfig.isNotEmpty()) {
                            startCollection(context, selectedConfig)
                            viewModel.markCollectionRunning(true)
                        }
                    }
                ) {
                    Text("Start Collection")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isCollecting,
                    onClick = {
                        stopCollection(context)
                        viewModel.markCollectionRunning(false)
                    }
                ) {
                    Text("Stop Collection")
                }
            }
        }
    }
}

private fun startCollection(context: Context, sensorConfigs: List<Pair<Int, Int>>) {
    val sensorTypes = sensorConfigs.map { it.first }.toIntArray()
    val intervals = sensorConfigs.map { it.second }.toIntArray()

    val intent = Intent(context, SensorCollectionService::class.java).apply {
        action = SensorCollectionService.ACTION_START_COLLECTION
        putExtra(SensorCollectionService.EXTRA_SENSOR_TYPES, sensorTypes)
        putExtra(SensorCollectionService.EXTRA_SENSOR_INTERVALS_MS, intervals)
    }

    ContextCompat.startForegroundService(context, intent)
}

private fun stopCollection(context: Context) {
    val stopIntent = Intent(context, SensorCollectionService::class.java).apply {
        action = SensorCollectionService.ACTION_STOP_COLLECTION
    }
    context.startService(stopIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalDropdown(
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
            value = "Interval: ${selectedIntervalMs / 1000}s",
            onValueChange = {},
            readOnly = true,
            label = { Text("Collection interval") },
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
