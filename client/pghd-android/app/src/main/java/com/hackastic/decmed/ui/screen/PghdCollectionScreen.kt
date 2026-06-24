package com.hackastic.decmed.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.repository.StoredPghdAccessGrant
import com.hackastic.decmed.data.pghd.PghdInputSanitizer
import com.hackastic.decmed.ui.components.PghdDateRangeFilter
import com.hackastic.decmed.ui.components.toPghdSourceDisplayLabel
import com.hackastic.decmed.viewmodel.PatientGrantAccessKind
import com.hackastic.decmed.viewmodel.PghdCollectionViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    var showRevokeAccessDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSummaryType by rememberSaveable { mutableStateOf<String?>(null) }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HealthConnectSummaryCard(
                    totalCount = uiState.totalCount,
                    available = uiState.isHealthConnectAvailable,
                    hasPermissions = uiState.hasHealthConnectPermissions,
                    hasHistoryPermission = uiState.hasHealthConnectHistoryPermission,
                    isRefreshingHealthConnectState = uiState.isRefreshingHealthConnectState,
                    isSyncing = uiState.isSyncing,
                    isSavingManualRecord = uiState.isSavingManualRecord,
                    isSubmitting = uiState.isSubmitting,
                    isGrantingAccess = uiState.isGrantingAccess,
                    isRevokingAccess = uiState.isRevokingAccess,
                    healthConnectStatusMessage = uiState.healthConnectStatusMessage,
                    message = uiState.lastSyncMessage,
                    error = uiState.errorMessage,
                    sourcePackages = uiState.healthConnectSourcePackages,
                    hasDetectedXiaomiSource = uiState.hasDetectedXiaomiSource,
                    onRequestDataPermission = { permissionLauncher.launch(viewModel.requestedPermissions) },
                    onRequestHistoryPermission = { permissionLauncher.launch(viewModel.requestedHistoryPermissions) },
                    onSync = viewModel::syncFromHealthConnect,
                    onSubmit = viewModel::submitDisplayedPghd,
                    onGrantAccess = { showGrantAccessDialog = true },
                    onRevokeAccess = { showRevokeAccessDialog = true }
                )
            }

            item {
                PghdSummaryDashboard(
                    records = uiState.records,
                    onDetail = { selectedSummaryType = it }
                )
            }

            item {
                PghdDateRangeFilter(
                    startDateMillis = uiState.dateFilterStartMillis,
                    endDateMillis = uiState.dateFilterEndMillis,
                    onDateRangeChange = viewModel::setDateFilter
                )
            }

            item {
                SourceFilters(
                    selectedSourceTag = uiState.selectedSourceTag,
                    onSelected = viewModel::selectSourceTag
                )
            }

            item {
                RecordTypeFilter(
                    selectedType = uiState.selectedRecordType,
                    options = uiState.recordTypes,
                    onSelected = viewModel::selectRecordType
                )
            }

            if (uiState.records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No PGHD records match the selected filters",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
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
            onGrant = { personnelAddress, personnelPrePublicKey, accessKind ->
                viewModel.grantAccess(personnelAddress, personnelPrePublicKey, accessKind)
                showGrantAccessDialog = false
            }
        )
    }

    if (showRevokeAccessDialog) {
        RevokePghdAccessDialog(
            isRevoking = uiState.isRevokingAccess,
            activeGrants = uiState.activeAccessGrants,
            dateFormatter = dateFormatter,
            onDismiss = { showRevokeAccessDialog = false },
            onRevoke = { grant ->
                viewModel.revokeAccess(grant)
                showRevokeAccessDialog = false
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

    selectedSummaryType?.let { recordType ->
        PghdSummaryDetailDialog(
            recordType = recordType,
            records = uiState.records.filter { it.recordType == recordType },
            dateFormatter = dateFormatter,
            onDismiss = { selectedSummaryType = null },
            onRecordDetail = { record ->
                selectedSummaryType = null
                selectedDetailRecord = record
            }
        )
    }
}

@Composable
private fun HealthConnectSummaryCard(
    totalCount: Long,
    available: Boolean,
    hasPermissions: Boolean,
    hasHistoryPermission: Boolean,
    isRefreshingHealthConnectState: Boolean,
    isSyncing: Boolean,
    isSavingManualRecord: Boolean,
    isSubmitting: Boolean,
    isGrantingAccess: Boolean,
    isRevokingAccess: Boolean,
    healthConnectStatusMessage: String,
    message: String?,
    error: String?,
    sourcePackages: List<String>,
    hasDetectedXiaomiSource: Boolean,
    onRequestDataPermission: () -> Unit,
    onRequestHistoryPermission: () -> Unit,
    onSync: () -> Unit,
    onSubmit: () -> Unit,
    onGrantAccess: () -> Unit,
    onRevokeAccess: () -> Unit
) {
    val activeOperation = when {
        isRefreshingHealthConnectState -> "Checking Health Connect..."
        isSyncing -> "Syncing Health Connect data..."
        isSavingManualRecord -> "Saving manual PGHD..."
        isSubmitting -> "Submitting encrypted PGHD..."
        isGrantingAccess -> "Granting access..."
        isRevokingAccess -> "Revoking access..."
        else -> null
    }
    val isBusy = activeOperation != null
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
                activeOperation?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                OutlinedButton(
                    enabled = !isBusy && available && (!hasPermissions || !hasHistoryPermission),
                    onClick = if (hasPermissions) onRequestHistoryPermission else onRequestDataPermission
                ) {
                    Text(if (hasPermissions) "Approve Health History" else "Connect Health Connect")
                }
                Button(
                    enabled = available && hasPermissions && !isBusy,
                    onClick = onSync
                ) {
                    if (isSyncing) SmallButtonProgress()
                    Text(if (isSyncing) "Syncing" else "Sync Now")
                }
                Button(
                    enabled = !isBusy && totalCount > 0,
                    onClick = onSubmit
                ) {
                    if (isSubmitting) {
                        SmallButtonProgress()
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                    }
                    Text(if (isSubmitting) "Submitting" else "Submit PGHD")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy,
                        onClick = onGrantAccess
                    ) {
                        if (isGrantingAccess) SmallButtonProgress()
                        Text(if (isGrantingAccess) "Granting" else "Grant")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy,
                        onClick = onRevokeAccess,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (isRevokingAccess) SmallButtonProgress()
                        Text(if (isRevokingAccess) "Revoking" else "Revoke")
                    }
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
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
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
private fun SmallButtonProgress() {
    CircularProgressIndicator(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(16.dp),
        strokeWidth = 2.dp
    )
}

@Composable
private fun GrantPghdAccessDialog(
    isGranting: Boolean,
    onDismiss: () -> Unit,
    onGrant: (String, String, PatientGrantAccessKind) -> Unit
) {
    val context = LocalContext.current
    var personnelAddress by rememberSaveable { mutableStateOf("") }
    var personnelPrePublicKey by rememberSaveable { mutableStateOf("") }
    var selectedAccessKind by rememberSaveable { mutableStateOf(PatientGrantAccessKind.PGHD_READ) }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    var showLiveScanner by rememberSaveable { mutableStateOf(false) }
    val applyQrContent: (String) -> Unit = { content ->
        val decoded = decodeHospitalPersonnelQrPayload(content)
        if (decoded == null) {
            scanError = "Invalid personnel QR. Scan the QR shown on the personnel profile."
        } else {
            personnelAddress = decoded.first
            personnelPrePublicKey = decoded.second
            scanError = null
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showLiveScanner = true
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
                onClick = { onGrant(personnelAddress, personnelPrePublicKey, selectedAccessKind) }
            ) {
                if (isGranting) SmallButtonProgress()
                Text(if (isGranting) "Granting" else "Grant")
            }
        },
        dismissButton = {
            OutlinedButton(enabled = !isGranting, onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Grant Access") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Access type",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedAccessKind == PatientGrantAccessKind.PGHD_READ,
                        onClick = { selectedAccessKind = PatientGrantAccessKind.PGHD_READ },
                        label = { Text("PGHD Read") },
                        enabled = !isGranting
                    )
                    FilterChip(
                        selected = selectedAccessKind == PatientGrantAccessKind.MEDICAL_RECORD_READ_UPDATE,
                        onClick = {
                            selectedAccessKind = PatientGrantAccessKind.MEDICAL_RECORD_READ_UPDATE
                        },
                        label = { Text("Medical Read/Update") },
                        enabled = !isGranting
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !isGranting,
                        onClick = {
                            if (
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                showLiveScanner = true
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

    if (showLiveScanner) {
        QrScannerDialog(
            onDismiss = { showLiveScanner = false },
            onQrDetected = { content ->
                showLiveScanner = false
                applyQrContent(content)
            }
        )
    }
}

@Composable
private fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAllPotentialBarcodes()
                .build()
        )
    }
    val processing = remember { AtomicBoolean(false) }
    val detected = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Scan Personnel QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Point the camera at the personnel QR. The QR can occupy only part of the frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .widthIn(min = 280.dp),
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            val cameraController = LifecycleCameraController(viewContext).apply {
                                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
                                    if (!processing.compareAndSet(false, true)) {
                                        imageProxy.close()
                                        return@setImageAnalysisAnalyzer
                                    }
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        processing.set(false)
                                        imageProxy.close()
                                        return@setImageAnalysisAnalyzer
                                    }
                                    val frame = captureLuminanceFrame(imageProxy)
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(inputImage)
                                        .addOnSuccessListener(mainExecutor) { barcodes ->
                                            val content = findQrContent(barcodes, frame)
                                            if (
                                                content != null &&
                                                detected.compareAndSet(false, true)
                                            ) {
                                                clearImageAnalysisAnalyzer()
                                                onQrDetected(content)
                                            }
                                        }
                                        .addOnCompleteListener(mainExecutor) {
                                            processing.set(false)
                                            imageProxy.close()
                                        }
                                }
                                bindToLifecycle(lifecycleOwner)
                            }
                            controller = cameraController
                        }
                    }
                )
            }
        }
    )
}

internal fun decodeHospitalPersonnelQrPayload(content: String): Pair<String, String>? {
    val normalized = content
        .replace("\uFEFF", "")
        .replace("\u200B", "")
        .trim()
    val candidates = buildList {
        add(normalized)
        decodeQrUriParameters(normalized).forEach(::add)
        if (!normalized.contains("://")) {
            runCatching {
                URLDecoder.decode(normalized, StandardCharsets.UTF_8.name())
            }.getOrNull()?.takeIf { it != normalized }?.let(::add)
        }
    }

    for (candidate in candidates) {
        decodeHospitalPersonnelJson(candidate)?.let { return it }
        val separatorIndex = candidate.indexOf('@')
        if (separatorIndex <= 0 || separatorIndex == candidate.lastIndex) continue
        val address = candidate.substring(0, separatorIndex).filterNot(Char::isWhitespace)
        val prePublicKey = candidate.substring(separatorIndex + 1).filterNot(Char::isWhitespace)
        if (address.isNotBlank() && prePublicKey.isNotBlank()) {
            return address to prePublicKey
        }
    }
    return null
}

private fun decodeQrUriParameters(content: String): List<String> = runCatching {
    val query = URI(content).rawQuery ?: return@runCatching emptyList()
    query.split("&").mapNotNull { parameter ->
        val key = parameter.substringBefore("=", missingDelimiterValue = "")
        if (key !in setOf("data", "payload", "qr")) return@mapNotNull null
        URLDecoder.decode(
            parameter.substringAfter("=", missingDelimiterValue = ""),
            StandardCharsets.UTF_8.name()
        )
    }
}.getOrDefault(emptyList())

private fun decodeHospitalPersonnelJson(content: String): Pair<String, String>? = runCatching {
    val json = JSONObject(content)
    val objects = buildList {
        add(json)
        listOf("data", "payload", "personnel").forEach { key ->
            json.optJSONObject(key)?.let(::add)
        }
    }
    objects.firstNotNullOfOrNull { value ->
        val address = value.optString("iotaAddress")
            .ifBlank { value.optString("iota_address") }
            .ifBlank { value.optString("hospitalPersonnelIotaAddress") }
            .ifBlank { value.optString("hospital_personnel_iota_address") }
            .ifBlank { value.optString("address") }
            .filterNot(Char::isWhitespace)
        val prePublicKey = value.optString("prePublicKey")
            .ifBlank { value.optString("pre_public_key") }
            .ifBlank { value.optString("hospitalPersonnelPrePublicKey") }
            .ifBlank { value.optString("hospital_personnel_pre_public_key") }
            .ifBlank { value.optString("pghdPrePublicKey") }
            .ifBlank { value.optString("pghd_pre_public_key") }
            .filterNot(Char::isWhitespace)
        if (address.isNotBlank() && prePublicKey.isNotBlank()) {
            address to prePublicKey
        } else {
            null
        }
    }
}.getOrNull()

@Composable
private fun RevokePghdAccessDialog(
    isRevoking: Boolean,
    activeGrants: List<StoredPghdAccessGrant>,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit,
    onRevoke: (StoredPghdAccessGrant) -> Unit
) {
    var selectedGrantId by remember(activeGrants) {
        mutableStateOf(activeGrants.firstOrNull()?.id)
    }
    val selectedGrant = activeGrants.firstOrNull { it.id == selectedGrantId }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = !isRevoking && selectedGrant != null,
                onClick = { selectedGrant?.let(onRevoke) }
            ) {
                if (isRevoking) SmallButtonProgress()
                Text(if (isRevoking) "Revoking" else "Revoke")
            }
        },
        dismissButton = {
            OutlinedButton(enabled = !isRevoking, onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Revoke Access") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select an active access grant. The app will use the matching on-chain access log automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeGrants.isEmpty()) {
                    Text(
                        text = "No active access grants saved on this device yet. Grant access from this app first before revoking it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeGrants.forEach { grant ->
                            val selected = selectedGrantId == grant.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        enabled = !isRevoking,
                                        onClick = { selectedGrantId = grant.id }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = grant.accessKind.displayLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Personnel: ${grant.hospitalPersonnelIotaAddress}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Granted: ${formatIsoDate(grant.grantedAt, dateFormatter)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Access entries: ${grant.accessLogIndexes.joinToString { "#$it" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun formatIsoDate(value: String, dateFormatter: SimpleDateFormat): String =
    runCatching { dateFormatter.format(Date(java.time.Instant.parse(value).toEpochMilli())) }
        .getOrDefault(value)

private fun decodeQrBitmap(bitmap: Bitmap): Result<String> = runCatching {
    var lastError: Throwable? = null
    for (candidate in qrBitmapCandidates(bitmap)) {
        val pixels = IntArray(candidate.width * candidate.height)
        candidate.getPixels(pixels, 0, candidate.width, 0, 0, candidate.width, candidate.height)
        val source = RGBLuminanceSource(candidate.width, candidate.height, pixels)
        decodeQrLuminanceSource(source)
            .recoverCatching { decodeQrLuminanceSource(source.invert()).getOrThrow() }
            .fold(
                onSuccess = { return@runCatching it },
                onFailure = { lastError = it }
            )
    }
    throw lastError ?: IllegalArgumentException("No QR code was detected in the selected image.")
}

private fun captureLuminanceFrame(imageProxy: ImageProxy): RotatedLuminance {
    val plane = imageProxy.planes.first()
    val bytes = compactLuminancePlane(
        buffer = plane.buffer,
        width = imageProxy.width,
        height = imageProxy.height,
        rowStride = plane.rowStride,
        pixelStride = plane.pixelStride
    )
    return rotateLuminance(
        source = bytes,
        width = imageProxy.width,
        height = imageProxy.height,
        rotationDegrees = imageProxy.imageInfo.rotationDegrees
    )
}

private fun findQrContent(
    barcodes: List<Barcode>,
    frame: RotatedLuminance
): String? {
    barcodes.firstNotNullOfOrNull { barcode ->
        barcode.rawValue?.trim()?.takeIf(String::isNotBlank)
    }?.let { return it }

    return barcodes.firstNotNullOfOrNull { barcode ->
        barcode.boundingBox?.let { boundingBox ->
            decodeCroppedQr(frame, boundingBox).getOrNull()
        }
    }
}

private fun decodeCroppedQr(
    frame: RotatedLuminance,
    boundingBox: Rect
): Result<String> = runCatching {
    val margin = (maxOf(boundingBox.width(), boundingBox.height()) * 0.15f).toInt()
    val left = (boundingBox.left - margin).coerceIn(0, frame.width - 1)
    val top = (boundingBox.top - margin).coerceIn(0, frame.height - 1)
    val right = (boundingBox.right + margin).coerceIn(left + 1, frame.width)
    val bottom = (boundingBox.bottom + margin).coerceIn(top + 1, frame.height)
    val source = PlanarYUVLuminanceSource(
        frame.bytes,
        frame.width,
        frame.height,
        left,
        top,
        right - left,
        bottom - top,
        false
    )
    decodeQrLuminanceSource(source)
        .recoverCatching { decodeQrLuminanceSource(source.invert()).getOrThrow() }
        .getOrThrow()
}

private fun compactLuminancePlane(
    buffer: java.nio.ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int
): ByteArray {
    val source = buffer.duplicate()
    val start = source.position()
    return ByteArray(width * height).also { compact ->
        var target = 0
        repeat(height) { y ->
            repeat(width) { x ->
                val sourceIndex = start + y * rowStride + x * pixelStride
                if (sourceIndex < source.limit()) {
                    compact[target] = source.get(sourceIndex)
                }
                target++
            }
        }
    }
}

private data class RotatedLuminance(
    val bytes: ByteArray,
    val width: Int,
    val height: Int
)

private fun rotateLuminance(
    source: ByteArray,
    width: Int,
    height: Int,
    rotationDegrees: Int
): RotatedLuminance {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    if (normalizedRotation == 0) return RotatedLuminance(source, width, height)

    val rotatedWidth = if (normalizedRotation == 90 || normalizedRotation == 270) height else width
    val rotatedHeight = if (normalizedRotation == 90 || normalizedRotation == 270) width else height
    val rotated = ByteArray(source.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val targetIndex = when (normalizedRotation) {
                90 -> x * rotatedWidth + (height - y - 1)
                180 -> (height - y - 1) * rotatedWidth + (width - x - 1)
                270 -> (width - x - 1) * rotatedWidth + y
                else -> return RotatedLuminance(source, width, height)
            }
            rotated[targetIndex] = source[y * width + x]
        }
    }
    return RotatedLuminance(rotated, rotatedWidth, rotatedHeight)
}

private fun qrBitmapCandidates(bitmap: Bitmap): List<Bitmap> {
    val candidates = mutableListOf(bitmap)
    if (bitmap.width > 1600 || bitmap.height > 1600) {
        candidates += Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
    }
    val cropRatios = listOf(0.75f, 0.55f, 0.4f)
    for (ratio in cropRatios) {
        val cropWidth = (bitmap.width * ratio).toInt().coerceAtLeast(160)
        val cropHeight = (bitmap.height * ratio).toInt().coerceAtLeast(160)
        if (cropWidth >= bitmap.width || cropHeight >= bitmap.height) continue
        val xPositions = listOf(0, (bitmap.width - cropWidth) / 2, bitmap.width - cropWidth).distinct()
        val yPositions = listOf(0, (bitmap.height - cropHeight) / 2, bitmap.height - cropHeight).distinct()
        for (x in xPositions) {
            for (y in yPositions) {
                candidates += Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
            }
        }
    }
    return candidates
}

private fun decodeQrLuminanceSource(source: LuminanceSource): Result<String> = runCatching {
    val reader = MultiFormatReader().apply {
        setHints(
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.PURE_BARCODE, false)
            }
        )
    }
    try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (err: NotFoundException) {
        throw err
    } finally {
        reader.reset()
    }
}

@Composable
private fun PghdSummaryDashboard(
    records: List<PghdRecordEntity>,
    onDetail: (String) -> Unit
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
                    OutlinedButton(onClick = { onDetail(item.type) }) {
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
            label = { Text(PghdRecordEntity.SOURCE_MANUAL.toPghdSourceDisplayLabel()) }
        )
        FilterChip(
            selected = selectedSourceTag == PghdRecordEntity.SOURCE_PHONE_SENSOR,
            onClick = { onSelected(PghdRecordEntity.SOURCE_PHONE_SENSOR) },
            label = { Text(PghdRecordEntity.SOURCE_PHONE_SENSOR.toPghdSourceDisplayLabel()) }
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
                    label = { Text(record.sourceTag.toPghdSourceDisplayLabel()) }
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
private fun PghdSummaryDetailDialog(
    recordType: String,
    records: List<PghdRecordEntity>,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit,
    onRecordDetail: (PghdRecordEntity) -> Unit
) {
    val title = records.firstOrNull()?.displayName ?: recordType

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text(title) },
        text = {
            if (records.isEmpty()) {
                Text("No local records are currently visible for this PGHD type.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    records
                        .sortedByDescending { it.endTimeEpochMillis }
                        .forEach { record ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
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
                                    Text(
                                        text = record.sourcePackageName ?: record.sourceTag.toPghdSourceDisplayLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(onClick = { onRecordDetail(record) }) {
                                        Text("Open record")
                                    }
                                }
                            }
                        }
                }
            }
        }
    )
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
                DetailLine("Source", record.sourceTag.toPghdSourceDisplayLabel())
                DetailLine("Source package", record.sourcePackageName ?: "-")
                DetailLine("Start", dateFormatter.format(Date(record.startTimeEpochMillis)))
                DetailLine("End", dateFormatter.format(Date(record.endTimeEpochMillis)))
                DetailLine("Sync", record.batchId?.let { "batched: $it" } ?: "local only")
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
    val canSave = remember(recordType, displayName, valueText, unit, notes) {
        runCatching {
            PghdInputSanitizer.sanitizeManualInput(recordType, displayName, valueText, unit, notes)
        }.isSuccess
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = canSave,
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
                    onValueChange = { recordType = it.take(64) },
                    label = { Text("Record type") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = displayName,
                    onValueChange = { displayName = it.take(120) },
                    label = { Text("Display name") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = valueText,
                    onValueChange = { valueText = it.take(2_000) },
                    label = { Text("Value") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = unit,
                    onValueChange = { unit = it.take(32) },
                    label = { Text("Unit") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = notes,
                    onValueChange = { notes = it.take(2_000) },
                    label = { Text("Notes") },
                    minLines = 2
                )
            }
        }
    )
}
