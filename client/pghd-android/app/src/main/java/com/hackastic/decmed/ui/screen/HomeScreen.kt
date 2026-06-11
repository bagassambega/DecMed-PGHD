package com.hackastic.decmed.ui.screen

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.remote.service.SensorCollectionService
import com.hackastic.decmed.utils.DecmedLog
import com.hackastic.decmed.viewmodel.PghdCollectionViewModel
import com.hackastic.decmed.viewmodel.SensorViewModel
import com.hackastic.decmed.worker.PghdWorkScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SensorViewModel,
    pghdViewModel: PghdCollectionViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToPghdCollection: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pghdUiState by pghdViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val approvedSensorCount = uiState.sensorConfigs.count { it.isApproved }
    val metrics = remember(pghdUiState.homeRecords) {
        pghdUiState.homeRecords.toHomeMetrics()
    }

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
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CollectionStatusCard(
                    isCollecting = uiState.isCollecting,
                    approvedSensorCount = approvedSensorCount,
                    totalRecordCount = pghdUiState.totalCount,
                    pendingBatchCount = pghdUiState.batches.count { it.status != PghdBatchEntity.STATUS_SENT },
                    onConfigureSensors = onNavigateToSettings
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = approvedSensorCount > 0 && !uiState.isCollecting,
                        onClick = {
                            val selectedConfig = viewModel.getApprovedCollectionConfig()
                            if (selectedConfig.isNotEmpty()) {
                                startCollection(context, selectedConfig)
                                viewModel.markCollectionRunning(true)
                                PghdWorkScheduler.scheduleCollectionWork(context)
                                PghdWorkScheduler.scheduleHealthConnectSyncNow(context)
                                Toast.makeText(context, "PGHD collection started.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isCollecting,
                        onClick = {
                            stopCollection(context)
                            viewModel.markCollectionRunning(false)
                            PghdWorkScheduler.cancelCollectionWork(context)
                            Toast.makeText(context, "PGHD collection stopped.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }

            item {
                Text(
                    text = "Your PGHD",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (metrics.isEmpty()) {
                item {
                    EmptyPghdHomeCard(
                        isCollecting = uiState.isCollecting,
                        hasSensors = approvedSensorCount > 0,
                        onConfigureSensors = onNavigateToSettings
                    )
                }
            } else {
                items(metrics, key = { it.recordType }) { metric ->
                    PghdHomeMetricCard(
                        metric = metric,
                        dateFormatter = dateFormatter
                    )
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToPghdCollection
                ) {
                    Text("Open detailed PGHD records")
                }
            }
        }
    }
}

@Composable
private fun CollectionStatusCard(
    isCollecting: Boolean,
    approvedSensorCount: Int,
    totalRecordCount: Long,
    pendingBatchCount: Int,
    onConfigureSensors: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCollecting) Icons.Default.HealthAndSafety else Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCollecting) "PGHD collection running" else "PGHD collection ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$totalRecordCount local PGHD records • $pendingBatchCount pending batches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$approvedSensorCount phone sensors enabled in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                OutlinedButton(onClick = onConfigureSensors) {
                    Text("Configure")
                }
            }
        }
    }
}

@Composable
private fun EmptyPghdHomeCard(
    isCollecting: Boolean,
    hasSensors: Boolean,
    onConfigureSensors: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        !hasSensors -> "No phone sensors are enabled yet."
                        isCollecting -> "Collecting PGHD. Your first records will appear here soon."
                        else -> "No PGHD records collected yet."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!hasSensors) {
                    OutlinedButton(onClick = onConfigureSensors) {
                        Text("Configure sensors")
                    }
                }
            }
        }
    }
}

@Composable
private fun PghdHomeMetricCard(
    metric: PghdHomeMetric,
    dateFormatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metric.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${metric.valueText} ${metric.unit}".trim(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    AssistChip(
                        onClick = {},
                        label = { Text(metric.sourceLabel) }
                    )
                    if (metric.isEstimated) {
                        AssistChip(
                            onClick = {},
                            label = { Text("estimated") }
                        )
                    }
                }
            }
            Text(
                text = "Updated ${dateFormatter.format(Date(metric.updatedAtEpochMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (metric.summaryText.isNotBlank()) {
                Text(
                    text = metric.summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class PghdHomeMetric(
    val recordType: String,
    val displayName: String,
    val valueText: String,
    val unit: String,
    val sourceLabel: String,
    val updatedAtEpochMillis: Long,
    val isEstimated: Boolean,
    val summaryText: String
)

private fun List<PghdRecordEntity>.toHomeMetrics(): List<PghdHomeMetric> {
    val priority = listOf(
        "steps",
        "heart_rate",
        "resting_heart_rate",
        "heart_rate_variability_rmssd",
        "oxygen_saturation",
        "respiratory_rate",
        "body_temperature",
        "skin_temperature",
        "sleep_session",
        "active_calories",
        "total_calories",
        "distance",
        "speed",
        "steps_cadence",
        "vo2_max",
        "floors_climbed",
        "elevation_gained",
        "movement_intensity",
        "rotation_intensity",
        "tilt_angle",
        "barometric_pressure",
        "environmental_temperature",
        "environmental_humidity",
        "ambient_light",
        "wear_status"
    )

    val latestByType = groupBy { it.recordType }
        .mapValues { (_, records) -> records.maxBy { it.endTimeEpochMillis } }

    val known = priority.mapNotNull { latestByType[it] }
    val other = latestByType
        .filterKeys { it !in priority }
        .values
        .sortedByDescending { it.endTimeEpochMillis }

    return (known + other)
        .take(24)
        .map { record ->
            PghdHomeMetric(
                recordType = record.recordType,
                displayName = record.recordType.toFriendlyMetricName(record.displayName),
                valueText = record.valueText,
                unit = record.unit.takeUnless { it == "record" } ?: "",
                sourceLabel = record.sourceTag.toFriendlySourceLabel(),
                updatedAtEpochMillis = record.endTimeEpochMillis,
                isEstimated = record.notes?.contains("estimated=true", ignoreCase = true) == true,
                summaryText = record.toMetricSummary()
            )
        }
}

private fun PghdRecordEntity.toMetricSummary(): String {
    val origin = when {
        sourcePackageName.isNullOrBlank() -> sourceTag.toFriendlySourceLabel()
        sourceTag == PghdRecordEntity.SOURCE_HEALTH_CONNECT -> "from ${sourcePackageName}"
        sourceTag == PghdRecordEntity.SOURCE_PHONE_SENSOR -> "from phone sensor"
        else -> sourcePackageName
    }
    val method = when {
        notes?.contains("method=derived", ignoreCase = true) == true -> "Derived measurement"
        notes?.contains("method=direct", ignoreCase = true) == true -> "Direct measurement"
        else -> "Collected measurement"
    }
    return "$method $origin"
}

private fun String.toFriendlySourceLabel(): String =
    when (this) {
        PghdRecordEntity.SOURCE_HEALTH_CONNECT -> "Health Connect"
        PghdRecordEntity.SOURCE_MANUAL -> "Manual"
        PghdRecordEntity.SOURCE_PHONE_SENSOR -> "phone_sensor"
        else -> this
    }

private fun String.toFriendlyMetricName(fallback: String): String =
    when (this) {
        "steps" -> "Steps"
        "heart_rate" -> "Heart Rate"
        "resting_heart_rate" -> "Resting Heart Rate"
        "heart_rate_variability_rmssd" -> "Heart Rate Variability"
        "oxygen_saturation" -> "Oxygen Saturation"
        "respiratory_rate" -> "Respiratory Rate"
        "body_temperature" -> "Body Temperature"
        "skin_temperature" -> "Skin Temperature"
        "sleep_session" -> "Sleep"
        "active_calories" -> "Active Calories"
        "total_calories" -> "Total Calories"
        "distance" -> "Distance"
        "speed" -> "Speed"
        "steps_cadence" -> "Walking Cadence"
        "vo2_max" -> "VO2 Max"
        "floors_climbed" -> "Floors Climbed"
        "elevation_gained" -> "Elevation Gained"
        "elevation_estimate" -> "Elevation"
        "movement_intensity" -> "Movement Intensity"
        "rotation_intensity" -> "Motion Rotation"
        "orientation_change" -> "Orientation Change"
        "tilt_angle" -> "Tilt Angle"
        "magnetic_field_strength" -> "Magnetic Field"
        "barometric_pressure" -> "Barometric Pressure"
        "environmental_temperature" -> "Environment Temperature"
        "environmental_humidity" -> "Environment Humidity"
        "ambient_light" -> "Light Exposure"
        "proximity" -> "Proximity"
        "wear_status" -> "Wear Status"
        "activity_event" -> "Activity Event"
        else -> fallback.ifBlank {
            replace('_', ' ')
                .split(' ')
                .joinToString(" ") { token ->
                    token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
        }
    }

private fun startCollection(context: Context, sensorConfigs: List<Pair<Int, Int>>) {
    DecmedLog.i(HOME_TAG, "Starting sensor collection with config=$sensorConfigs")
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
    DecmedLog.i(HOME_TAG, "Stopping sensor collection")
    val stopIntent = Intent(context, SensorCollectionService::class.java).apply {
        action = SensorCollectionService.ACTION_STOP_COLLECTION
    }
    context.startService(stopIntent)
}

private const val HOME_TAG = "HomeScreen"
