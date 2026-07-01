package com.hackastic.decmed.ui.screen
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.config.Env
import com.hackastic.decmed.ui.components.InteractiveProcessToastHost
import com.hackastic.decmed.ui.components.PghdDateRangeFilter
import com.hackastic.decmed.ui.components.ProcessToastEvent
import com.hackastic.decmed.ui.components.ProcessToastKind
import com.hackastic.decmed.viewmodel.ActivePghdCollectionWindow
import com.hackastic.decmed.viewmodel.PghdCollectionViewModel
import com.hackastic.decmed.worker.PghdWorkScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PghdBatchScreen(
    viewModel: PghdCollectionViewModel,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val processToastEvent = uiState.errorMessage?.let {
        ProcessToastEvent(ProcessToastKind.Failure, it)
    } ?: uiState.lastSyncMessage?.let {
        ProcessToastEvent(ProcessToastKind.Success, it)
    }
    val retryableBatchKey = uiState.batches
        .filter {
            it.status == PghdBatchEntity.STATUS_WAITING_FOR_TRIGGER
        }
        .joinToString("|") { it.batchId }

    LaunchedEffect(retryableBatchKey) {
        if (retryableBatchKey.isNotBlank()) {
            PghdWorkScheduler.scheduleSubmitWhenConnected(context.applicationContext)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PGHD Batches") },
                actions = {
                    IconButton(
                        enabled = !uiState.isSubmitting && uiState.records.isNotEmpty(),
                        onClick = viewModel::submitDisplayedPghd
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Send current PGHD")
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
            if (uiState.lastSyncMessage != null) {
                Text(
                    text = uiState.lastSyncMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            PghdDateRangeFilter(
                startDateMillis = uiState.dateFilterStartMillis,
                endDateMillis = uiState.dateFilterEndMillis,
                onDateRangeChange = viewModel::setDateFilter
            )
            Spacer(modifier = Modifier.height(8.dp))

            val activeWindow = uiState.activeCollectionWindow
            if (uiState.visibleBatches.isEmpty() && activeWindow == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.batches.isEmpty()) {
                            "No PGHD batches created yet"
                        } else {
                            "No PGHD batches match the selected date filter"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeWindow?.let { window ->
                        item {
                            ActiveCollectionWindowCard(
                                window = window,
                                dateFormatter = dateFormatter,
                                isSubmitting = uiState.isSubmittingActiveCollection,
                                onSubmit = viewModel::submitActiveCollection
                            )
                        }
                    }
                    items(uiState.visibleBatches) { batch ->
                        PghdBatchCard(
                            batch = batch,
                            dateFormatter = dateFormatter,
                            isSubmitting = uiState.submittingBatchId == batch.batchId,
                            onSubmit = { viewModel.submitBatch(batch.batchId) }
                        )
                    }
                }
            }
        }
        }
        InteractiveProcessToastHost(
            event = processToastEvent,
            onEventConsumed = viewModel::clearMessages,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ActiveCollectionWindowCard(
    window: ActivePghdCollectionWindow,
    dateFormatter: SimpleDateFormat,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (window.isCollecting) "Collecting data" else "Collected data waiting for trigger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (window.isCollecting) {
                            "Current data is still local and has not been encrypted into a batch yet."
                        } else {
                            "These local records are not encrypted into a batch yet. Send manually or start collection again."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (window.isCollecting) "Active" else "Waiting") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
            Text(
                text = "Collection window: ${
                    formatDateRange(
                        dateFormatter,
                        window.startedAtEpochMillis,
                        window.endedAtEpochMillis
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Data collected period: ${
                    formatDateRange(
                        dateFormatter,
                        window.dataStartEpochMillis,
                        window.dataEndEpochMillis
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Records collected: ${window.recordCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Estimated batch size: ${window.estimatedBytes.toReadableBytes()} / ${Env.pghdEarlyTriggerBytes.toReadableBytes()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(
                modifier = Modifier.align(Alignment.End),
                enabled = !isSubmitting && window.recordCount > 0,
                onClick = onSubmit
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Sending...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Send current collection")
                }
            }
        }
    }
}

@Composable
private fun PghdBatchCard(
    batch: PghdBatchEntity,
    dateFormatter: SimpleDateFormat,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val canSubmit = batch.status != PghdBatchEntity.STATUS_SENT &&
        batch.status != PghdBatchEntity.STATUS_PENDING &&
        !isSubmitting

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Batch ID: ${batch.batchId}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Created: ${dateFormatter.format(Date(batch.createdAtEpochMillis))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Batch actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isSubmitting) "Sending..." else "Send now") },
                            leadingIcon = {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                }
                            },
                            enabled = canSubmit,
                            onClick = {
                                menuExpanded = false
                                onSubmit()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatchStatusChip(status = batch.status)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Created trigger: ${batch.triggerReason.toTriggerLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Send trigger: ${(batch.lastSubmitTriggerReason ?: batch.triggerReason).toTriggerLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Attempt count: ${batch.retryCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Collection window: ${
                    formatDateRange(
                        dateFormatter,
                        batch.collectionStartedAtEpochMillis,
                        batch.collectionEndedAtEpochMillis
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Data collected period: ${
                    formatDateRange(
                        dateFormatter,
                        batch.startTimestamp.toEpochMillisForDisplay(),
                        batch.endTimestamp.toEpochMillisForDisplay()
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Patient address: ${batch.patientId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (batch.lastAttemptEpochMillis != null) {
                Text(
                    text = "Last attempt: ${dateFormatter.format(Date(batch.lastAttemptEpochMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun String.toTriggerLabel(): String =
    when (this) {
        PghdBatchEntity.TRIGGER_MANUAL_SUBMIT -> "manual submit"
        PghdBatchEntity.TRIGGER_SIZE_THRESHOLD -> "size threshold"
        PghdBatchEntity.TRIGGER_NETWORK_AVAILABLE -> "network available"
        else -> "15-minute schedule"
    }

private fun Long.toEpochMillisForDisplay(): Long =
    if (this < 10_000_000_000L) this * 1000L else this

private fun formatDateRange(
    formatter: SimpleDateFormat,
    startEpochMillis: Long?,
    endEpochMillis: Long?
): String {
    val start = startEpochMillis?.let { formatter.format(Date(it)) } ?: "Not recorded"
    val end = endEpochMillis?.let { formatter.format(Date(it)) } ?: "now"
    return "$start - $end"
}

private fun Long.toReadableBytes(): String {
    val mib = this / (1024.0 * 1024.0)
    return if (mib >= 1.0) {
        String.format(Locale.getDefault(), "%.2f MB", mib)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", this / 1024.0)
    }
}

@Composable
private fun BatchStatusChip(status: String) {
    val (label, icon, color) = when (status) {
        PghdBatchEntity.STATUS_WAITING_FOR_TRIGGER -> Triple("Waiting for network", Icons.Default.CloudUpload, MaterialTheme.colorScheme.secondary)
        PghdBatchEntity.STATUS_PENDING -> Triple("Sending", Icons.Default.CloudUpload, MaterialTheme.colorScheme.tertiary)
        PghdBatchEntity.STATUS_SENT -> Triple("Sent", Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
        PghdBatchEntity.STATUS_FAILED -> Triple("Failed", Icons.Default.Error, MaterialTheme.colorScheme.error)
        else -> Triple(status.replace('_', ' '), Icons.Default.CloudUpload, MaterialTheme.colorScheme.tertiary)
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color
            )
        }
    )
}
