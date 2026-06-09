package com.hackastic.decmed.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.viewmodel.PghdCollectionViewModel
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PghdCollectionScreen(
    viewModel: PghdCollectionViewModel,
    onNavigateBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var showGrantAccessDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDetailRecord by remember { mutableStateOf<PghdRecordEntity?>(null) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHealthConnectState()
    }

    LaunchedEffect(uiState.errorMessage, uiState.lastSyncMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
            return@LaunchedEffect
        }
        uiState.lastSyncMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PGHD Collection") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add manual PGHD")
                    }
                    IconButton(
                        enabled = !uiState.isSyncing,
                        onClick = {
                            if (uiState.hasHealthConnectPermissions) {
                                viewModel.syncFromHealthConnect()
                            } else {
                                permissionLauncher.launch(viewModel.requestedPermissions)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Health Connect")
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
            HealthConnectSummaryCard(
                totalCount = uiState.totalCount,
                available = uiState.isHealthConnectAvailable,
                hasPermissions = uiState.hasHealthConnectPermissions,
                hasHistoryPermission = uiState.hasHealthConnectHistoryPermission,
                isSyncing = uiState.isSyncing,
                isSubmitting = uiState.isSubmitting,
                isGrantingAccess = uiState.isGrantingAccess,
                healthConnectStatusMessage = uiState.healthConnectStatusMessage,
                message = uiState.lastSyncMessage,
                error = uiState.errorMessage,
                sourcePackages = uiState.healthConnectSourcePackages,
                hasDetectedXiaomiSource = uiState.hasDetectedXiaomiSource,
                onRequestDataPermission = { permissionLauncher.launch(viewModel.requestedPermissions) },
                onRequestHistoryPermission = { permissionLauncher.launch(viewModel.requestedHistoryPermissions) },
                onSync = viewModel::syncFromHealthConnect,
                onSubmit = viewModel::submitDisplayedPghd,
                onGrantAccess = { showGrantAccessDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PghdSummaryDashboard(
                records = uiState.records,
                onSelectType = viewModel::selectRecordType
            )

            Spacer(modifier = Modifier.height(12.dp))

            SourceFilters(
                selectedSourceTag = uiState.selectedSourceTag,
                onSelected = viewModel::selectSourceTag
            )

            Spacer(modifier = Modifier.height(8.dp))

            RecordTypeFilter(
                selectedType = uiState.selectedRecordType,
                options = uiState.recordTypes,
                onSelected = viewModel::selectRecordType
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No PGHD records collected yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.records) { record ->
                        PghdRecordCard(
                            record = record,
                            dateFormatter = dateFormatter,
                            onDetail = { selectedDetailRecord = record }
                        )
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualPghdDialog(
            onDismiss = { showManualDialog = false },
            onSave = { recordType, displayName, valueText, unit, notes ->
                viewModel.addManualRecord(recordType, displayName, valueText, unit, notes)
                showManualDialog = false
            }
        )
    }

    if (showGrantAccessDialog) {
        GrantPghdAccessDialog(
            isGranting = uiState.isGrantingAccess,
            onDismiss = { showGrantAccessDialog = false },
            onGrant = { personnelAddress, personnelPrePublicKey ->
                viewModel.grantPghdAccess(personnelAddress, personnelPrePublicKey)
                showGrantAccessDialog = false
            }
        )
    }

    selectedDetailRecord?.let { record ->
        PghdRecordDetailDialog(
            record = record,
            dateFormatter = dateFormatter,
            onDismiss = { selectedDetailRecord = null }
        )
    }
}

@Composable
private fun HealthConnectSummaryCard(
    totalCount: Long,
    available: Boolean,
    hasPermissions: Boolean,
    hasHistoryPermission: Boolean,
    isSyncing: Boolean,
    isSubmitting: Boolean,
    isGrantingAccess: Boolean,
    healthConnectStatusMessage: String,
    message: String?,
    error: String?,
    sourcePackages: List<String>,
    hasDetectedXiaomiSource: Boolean,
    onRequestDataPermission: () -> Unit,
    onRequestHistoryPermission: () -> Unit,
    onSync: () -> Unit,
    onSubmit: () -> Unit,
    onGrantAccess: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "$totalCount PGHD records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when {
                            !available -> "Health Connect unavailable on this device"
                            hasPermissions && hasHistoryPermission -> "Xiaomi Smart Band sync ready, including health history"
                            hasPermissions -> "Xiaomi Smart Band sync ready for recent Health Connect data"
                            else -> "Connect Health Connect to read Xiaomi Smart Band PGHD"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = available && (!hasPermissions || !hasHistoryPermission),
                    onClick = if (hasPermissions) onRequestHistoryPermission else onRequestDataPermission
                ) {
                    Text(if (hasPermissions) "Approve Health History" else "Connect Health Connect")
                }
                Button(
                    enabled = available && hasPermissions && !isSyncing && !isSubmitting,
                    onClick = onSync
                ) {
                    Text(if (isSyncing) "Syncing" else "Sync Now")
                }
                Button(
                    enabled = !isSyncing && !isSubmitting && !isGrantingAccess && totalCount > 0,
                    onClick = onSubmit
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Text(if (isSubmitting) "Submitting" else "Submit PGHD")
                }
                OutlinedButton(
                    enabled = !isSyncing && !isSubmitting && !isGrantingAccess,
                    onClick = onGrantAccess
                ) {
                    Text(if (isGrantingAccess) "Granting" else "Grant Access")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Mi Fitness -> Health Connect -> DecMed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when {
                        !available -> healthConnectStatusMessage
                        !hasPermissions -> "Grant DecMed read access for steps, heart, sleep, activity, and SpO2."
                        !hasHistoryPermission -> "Recent data sync is enabled. Approve health history to include older Health Connect records."
                        sourcePackages.isEmpty() -> "No Health Connect source packages detected yet. Sync Mi Fitness with Health Connect, then sync here."
                        hasDetectedXiaomiSource -> "Detected Xiaomi/Mi Fitness compatible Health Connect source."
                        else -> "Detected Health Connect sources, but none look like Xiaomi/Mi Fitness yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (sourcePackages.isNotEmpty()) {
                    Text(
                        text = sourcePackages.joinToString(prefix = "Sources: "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            val statusText = error ?: message
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error == null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun GrantPghdAccessDialog(
    isGranting: Boolean,
    onDismiss: () -> Unit,
    onGrant: (String, String) -> Unit
) {
    val context = LocalContext.current
    var personnelAddress by rememberSaveable { mutableStateOf("") }
    var personnelPrePublicKey by rememberSaveable { mutableStateOf("") }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    val applyQrContent: (String) -> Unit = { content ->
        val decoded = decodeHospitalPersonnelQrPayload(content)
        if (decoded == null) {
            scanError = "Invalid personnel QR. Expected iotaAddress@prePublicKey."
        } else {
            personnelAddress = decoded.first
            personnelPrePublicKey = decoded.second
            scanError = null
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            scanError = "No camera image captured."
        } else {
            decodeQrBitmap(bitmap).fold(
                onSuccess = applyQrContent,
                onFailure = { scanError = it.message ?: "Unable to read QR image." }
            )
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            scanError = "Camera permission is required to scan QR directly."
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected image." }
                requireNotNull(BitmapFactory.decodeStream(input)) { "Selected file is not a valid image." }
            }
        }.fold(
            onSuccess = { bitmap ->
                decodeQrBitmap(bitmap).fold(
                    onSuccess = applyQrContent,
                    onFailure = { scanError = it.message ?: "Unable to read QR image." }
                )
            },
            onFailure = { scanError = it.message ?: "Unable to open selected image." }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = !isGranting && personnelAddress.isNotBlank() && personnelPrePublicKey.isNotBlank(),
                onClick = { onGrant(personnelAddress, personnelPrePublicKey) }
            ) {
                Text(if (isGranting) "Granting" else "Grant")
            }
        },
        dismissButton = {
            OutlinedButton(enabled = !isGranting, onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Grant PGHD Access") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !isGranting,
                        onClick = {
                            if (
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                        Text("Scan")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !isGranting,
                        onClick = { imageLauncher.launch("image/*") }
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null)
                        Text("Image")
                    }
                }
                scanError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = personnelAddress,
                    onValueChange = { personnelAddress = it },
                    label = { Text("Personnel IOTA address") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = personnelPrePublicKey,
                    onValueChange = { personnelPrePublicKey = it },
                    label = { Text("Personnel PRE public key") }
                )
            }
        }
    )
}

private fun decodeHospitalPersonnelQrPayload(content: String): Pair<String, String>? {
    val normalized = content.trim()
    runCatching {
        val json = JSONObject(normalized)
        val address = json.optString("iotaAddress")
            .ifBlank { json.optString("iota_address") }
            .ifBlank { json.optString("address") }
            .trim()
        val prePublicKey = json.optString("prePublicKey")
            .ifBlank { json.optString("pre_public_key") }
            .ifBlank { json.optString("pghdPrePublicKey") }
            .ifBlank { json.optString("pghd_pre_public_key") }
            .trim()
        if (address.isNotBlank() && prePublicKey.isNotBlank()) return address to prePublicKey
    }
    val parts = normalized.split("@", limit = 2)
    if (parts.size != 2) return null
    val address = parts[0].trim()
    val prePublicKey = parts[1].trim()
    if (address.isBlank() || prePublicKey.isBlank()) return null
    return address to prePublicKey
}

private fun decodeQrBitmap(bitmap: Bitmap): Result<String> = runCatching {
    val reader = MultiFormatReader().apply {
        setHints(
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.PURE_BARCODE, false)
            }
        )
    }
    val variants = listOf(bitmap) + if (bitmap.width > 1600 || bitmap.height > 1600) {
        listOf(Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true))
    } else {
        emptyList()
    }
    var lastError: Throwable? = null
    for (candidate in variants) {
        val pixels = IntArray(candidate.width * candidate.height)
        candidate.getPixels(pixels, 0, candidate.width, 0, 0, candidate.width, candidate.height)
        val source = RGBLuminanceSource(candidate.width, candidate.height, pixels)
        for (luminance in listOf(source, source.invert())) {
            try {
                return@runCatching reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance))).text
            } catch (err: NotFoundException) {
                lastError = err
            } finally {
                reader.reset()
            }
        }
    }
    throw lastError ?: IllegalArgumentException("No QR code was detected in the selected image.")
}

@Composable
private fun PghdSummaryDashboard(
    records: List<PghdRecordEntity>,
    onSelectType: (String?) -> Unit
) {
    val summaries = remember(records) {
        listOf("steps", "heart_rate", "active_calories", "distance", "sleep_session", "oxygen_saturation")
            .mapNotNull { type ->
                val matching = records.filter { it.recordType == type }
                if (matching.isEmpty()) return@mapNotNull null
                val latest = matching.maxBy { it.endTimeEpochMillis }
                val numericValues = matching.mapNotNull { it.numericValue }
                val value = when {
                    type == "steps" && numericValues.isNotEmpty() -> numericValues.sum()
                    type == "active_calories" && numericValues.isNotEmpty() -> numericValues.sum()
                    type == "distance" && numericValues.isNotEmpty() -> numericValues.sum()
                    else -> latest.numericValue ?: latest.valueText
                }
                SummaryItem(
                    type = type,
                    label = latest.displayName,
                    value = when (value) {
                        is Double -> value.toSmartText()
                        else -> value.toString()
                    },
                    unit = latest.unit,
                    count = matching.size
                )
            }
    }

    if (summaries.isEmpty()) return

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(summaries) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.value} ${item.unit}".trim(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${item.count} local records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    OutlinedButton(onClick = { onSelectType(item.type) }) {
                        Text("Detail")
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilters(
    selectedSourceTag: String?,
    onSelected: (String?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedSourceTag == null,
            onClick = { onSelected(null) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedSourceTag == PghdRecordEntity.SOURCE_HEALTH_CONNECT,
            onClick = { onSelected(PghdRecordEntity.SOURCE_HEALTH_CONNECT) },
            label = { Text("Health Connect") }
        )
        FilterChip(
            selected = selectedSourceTag == PghdRecordEntity.SOURCE_MANUAL,
            onClick = { onSelected(PghdRecordEntity.SOURCE_MANUAL) },
            label = { Text(PghdRecordEntity.SOURCE_MANUAL) }
        )
    }
}

@Composable
private fun PghdRecordCard(
    record: PghdRecordEntity,
    dateFormatter: SimpleDateFormat,
    onDetail: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(
                    onClick = {},
                    label = { Text(record.sourceTag) }
                )
            }
            Text(
                text = "${record.valueText} ${record.unit}".trim(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateFormatter.format(Date(record.endTimeEpochMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!record.notes.isNullOrBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onDetail) {
                Text("Detail")
            }
        }
    }
}

@Composable
private fun PghdRecordDetailDialog(
    record: PghdRecordEntity,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text(record.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Type", record.recordType)
                DetailLine("Value", "${record.valueText} ${record.unit}".trim())
                DetailLine("Source", record.sourceTag)
                DetailLine("Source package", record.sourcePackageName ?: "-")
                DetailLine("Start", dateFormatter.format(Date(record.startTimeEpochMillis)))
                DetailLine("End", dateFormatter.format(Date(record.endTimeEpochMillis)))
                DetailLine("Sync", record.batchId ?: "local only")
                if (!record.notes.isNullOrBlank()) {
                    DetailLine("Notes", record.notes)
                }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class SummaryItem(
    val type: String,
    val label: String,
    val value: String,
    val unit: String,
    val count: Int
)

private fun Double.toSmartText(): String =
    if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        "%.2f".format(this)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTypeFilter(
    selectedType: String?,
    options: List<String>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            value = selectedType ?: "All PGHD types",
            onValueChange = {},
            readOnly = true,
            label = { Text("PGHD type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All PGHD types") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            options.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ManualPghdDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?) -> Unit
) {
    var recordType by rememberSaveable { mutableStateOf("manual_pghd") }
    var displayName by rememberSaveable { mutableStateOf("Manual PGHD") }
    var valueText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = valueText.isNotBlank(),
                onClick = { onSave(recordType, displayName, valueText, unit, notes) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add manual PGHD") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = recordType,
                    onValueChange = { recordType = it },
                    label = { Text("Record type") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2
                )
            }
        }
    )
}
