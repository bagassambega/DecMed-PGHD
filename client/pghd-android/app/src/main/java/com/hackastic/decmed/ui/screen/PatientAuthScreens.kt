package com.hackastic.decmed.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.ui.components.InteractiveProcessToastHost
import com.hackastic.decmed.ui.components.ProcessToastEvent
import com.hackastic.decmed.ui.components.ProcessToastKind
import com.hackastic.decmed.viewmodel.PatientAuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun PatientAuthChoiceScreen(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit
) {
    PatientAuthScaffold(title = "Patient Identity") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connect a DecMed patient identity before configuring PGHD collection.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This Kotlin flow mirrors the Tauri patient client and is ready for the Rust-compatible crypto bridge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSignUp
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                Text(text = "Create Patient Account", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSignIn
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                Text(text = "Recover Existing Account", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun PatientSignupScreen(
    viewModel: PatientAuthViewModel,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var seedWords by rememberSaveable { mutableStateOf("") }
    var nik by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }

    LaunchedEffect(uiState.generatedSeedWords) {
        if (uiState.generatedSeedWords.isNotBlank()) {
            seedWords = uiState.generatedSeedWords
            clipboardManager.setText(AnnotatedString(uiState.generatedSeedWords))
            localToastEvent = ProcessToastEvent(
                kind = ProcessToastKind.Info,
                detail = "Seed words generated and copied to clipboard."
            )
        }
    }

    PatientAuthFormScaffold(
        title = "Create Patient Account",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = onBack,
        toastEvent = localToastEvent,
        onToastEventConsumed = { localToastEvent = null }
    ) {
        PinFields(
            pin = pin,
            confirmPin = confirmPin,
            onPinChange = { pin = it },
            onConfirmPinChange = { confirmPin = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        SeedWordsField(
            seedWords = seedWords,
            readOnly = true,
            supportingText = "Use Generate Seed Words to create a mnemonic.",
            onSeedWordsChange = {},
            onCopySeedWords = {
                copySeedWordsToClipboard(clipboardManager, seedWords) { localToastEvent = it }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                viewModel.generateMnemonic()
            },
            enabled = !uiState.isBusy
        ) {
            if (uiState.isBusy) SmallButtonProgress()
            Text(if (uiState.isBusy) "Generating..." else "Generate Seed Words")
        }
        Spacer(modifier = Modifier.height(12.dp))
        NikField(value = nik, onValueChange = { nik = it })
        FormErrorText(formError)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy && pin.length == PIN_LENGTH && confirmPin.length == PIN_LENGTH,
            onClick = {
                formError = validateMatchingPin(pin, confirmPin)
                if (formError == null) {
                    viewModel.signUp(pin, seedWords, nik) {
                        localToastEvent = ProcessToastEvent(
                            ProcessToastKind.Success,
                            "Patient registration successful.\n\nThe patient identity has been created and stored for this session."
                        )
                        coroutineScope.launch {
                            delay(AUTH_SUCCESS_NAVIGATION_DELAY_MS)
                            onCompleted()
                        }
                    }
                } else {
                    localToastEvent = ProcessToastEvent(ProcessToastKind.Failure, formError.orEmpty())
                }
            }
        ) {
            if (uiState.isBusy) SmallButtonProgress()
            Text(if (uiState.isBusy) "Creating..." else "Continue")
        }
    }
}

@Composable
fun PatientSigninScreen(
    viewModel: PatientAuthViewModel,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var seedWords by rememberSaveable { mutableStateOf("") }
    var nik by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }

    PatientAuthFormScaffold(
        title = "Recover Patient Account",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = onBack,
        toastEvent = localToastEvent,
        onToastEventConsumed = { localToastEvent = null }
    ) {
        PinFields(
            pin = pin,
            confirmPin = confirmPin,
            onPinChange = { pin = it },
            onConfirmPinChange = { confirmPin = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        SeedWordsField(
            seedWords = seedWords,
            readOnly = false,
            supportingText = null,
            onSeedWordsChange = { seedWords = it },
            onCopySeedWords = {
                copySeedWordsToClipboard(clipboardManager, seedWords) { localToastEvent = it }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        NikField(value = nik, onValueChange = { nik = it })
        FormErrorText(formError)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy && pin.length == PIN_LENGTH && confirmPin.length == PIN_LENGTH,
            onClick = {
                formError = validateMatchingPin(pin, confirmPin)
                if (formError == null) {
                    viewModel.signIn(pin, seedWords, nik) {
                        localToastEvent = ProcessToastEvent(
                            ProcessToastKind.Success,
                            "Patient login successful.\n\nThe patient identity has been recovered and unlocked."
                        )
                        coroutineScope.launch {
                            delay(AUTH_SUCCESS_NAVIGATION_DELAY_MS)
                            onCompleted()
                        }
                    }
                } else {
                    localToastEvent = ProcessToastEvent(ProcessToastKind.Failure, formError.orEmpty())
                }
            }
        ) {
            if (uiState.isBusy) SmallButtonProgress()
            Text(if (uiState.isBusy) "Recovering..." else "Recover")
        }
    }
}

@Composable
fun PatientCompleteProfileScreen(
    patientId: String,
    viewModel: PatientAuthViewModel,
    onCompleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var birthPlace by rememberSaveable { mutableStateOf("") }
    var dateOfBirth by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var religion by rememberSaveable { mutableStateOf("") }
    var education by rememberSaveable { mutableStateOf("") }
    var occupation by rememberSaveable { mutableStateOf("") }
    var maritalStatus by rememberSaveable { mutableStateOf("") }
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }

    PatientAuthFormScaffold(
        title = "Complete Profile",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = null,
        toastEvent = localToastEvent,
        onToastEventConsumed = { localToastEvent = null }
    ) {
        SimpleTextField("Name", name) { name = it }
        SimpleTextField("Birth place", birthPlace) { birthPlace = it }
        DateOfBirthField(dateOfBirth) { dateOfBirth = it }
        SimpleTextField("Gender", gender) { gender = it }
        SimpleTextField("Religion", religion) { religion = it }
        SimpleTextField("Education", education) { education = it }
        SimpleTextField("Occupation", occupation) { occupation = it }
        SimpleTextField("Marital status", maritalStatus) { maritalStatus = it }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy,
            onClick = {
                viewModel.saveProfile(
                    PatientProfile(
                        id = patientId,
                        name = name,
                        birthPlace = birthPlace,
                        dateOfBirth = dateOfBirth,
                        gender = gender,
                        religion = religion,
                        education = education,
                        occupation = occupation,
                        maritalStatus = maritalStatus
                    ),
                ) {
                    localToastEvent = ProcessToastEvent(
                        ProcessToastKind.Success,
                        "Patient profile saved.\n\nProfile metadata has been stored for the active patient session."
                    )
                    coroutineScope.launch {
                        delay(AUTH_SUCCESS_NAVIGATION_DELAY_MS)
                        onCompleted()
                    }
                }
            }
        ) {
            if (uiState.isBusy) SmallButtonProgress()
            Text(if (uiState.isBusy) "Saving..." else "Save Profile")
        }
    }
}

@Composable
fun PatientUnlockScreen(
    viewModel: PatientAuthViewModel,
    onUnlocked: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var pin by rememberSaveable { mutableStateOf("") }
    var localToastEvent by remember { mutableStateOf<ProcessToastEvent?>(null) }

    PatientAuthFormScaffold(
        title = "Unlock Patient Session",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = null,
        toastEvent = localToastEvent,
        onToastEventConsumed = { localToastEvent = null }
    ) {
        SixDigitPinField(
            label = "PIN",
            value = pin,
            onValueChange = { pin = it }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy && pin.length == PIN_LENGTH,
            onClick = {
                viewModel.unlock(pin) {
                    localToastEvent = ProcessToastEvent(
                        ProcessToastKind.Success,
                        "Patient session unlocked.\n\nThe stored patient identity is ready for PGHD collection."
                    )
                    coroutineScope.launch {
                        delay(AUTH_SUCCESS_NAVIGATION_DELAY_MS)
                        onUnlocked()
                    }
                }
            }
        ) {
            if (uiState.isBusy) SmallButtonProgress()
            Text(if (uiState.isBusy) "Unlocking..." else "Unlock")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientAuthScaffold(
    title: String,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientAuthFormScaffold(
    title: String,
    isBusy: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onBack: (() -> Unit)?,
    toastEvent: ProcessToastEvent? = null,
    onToastEventConsumed: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val processToastEvent = errorMessage?.let {
        ProcessToastEvent(ProcessToastKind.Failure, it)
    } ?: toastEvent

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            OutlinedButton(
                                modifier = Modifier.padding(start = 8.dp),
                                onClick = onBack
                            ) {
                                Text("Back")
                            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isBusy) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                content()
            }
        }
        InteractiveProcessToastHost(
            event = processToastEvent,
            onEventConsumed = {
                if (errorMessage != null) {
                    onDismissError()
                }
                onToastEventConsumed()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val selectedMillis = remember(value) {
        runCatching {
            if (value.isBlank()) null else {
                java.time.LocalDate.parse(value)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }
        }.getOrNull()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedMillis
    )

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = {},
        label = { Text("Date of birth") },
        readOnly = true,
        supportingText = { Text("Tap to choose from calendar.") },
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) {
                Text("Choose")
            }
        }
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onValueChange(date.toString())
                        }
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun PinFields(
    pin: String,
    confirmPin: String,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit
) {
    SixDigitPinField(
        label = "PIN",
        value = pin,
        onValueChange = onPinChange
    )
    SixDigitPinField(
        label = "Confirm PIN",
        value = confirmPin,
        onValueChange = onConfirmPinChange
    )
}

@Composable
private fun SixDigitPinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFieldFocused by interactionSource.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = { text ->
                onValueChange(text.filter(Char::isDigit).take(PIN_LENGTH))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(PIN_LENGTH) { index ->
                        PinDigitBox(
                            modifier = Modifier.weight(1f),
                            digit = value.getOrNull(index)?.let { "•" } ?: "",
                            isFocused = isFieldFocused && index == value.length.coerceAtMost(PIN_LENGTH - 1)
                        )
                    }
                }
            }
        )
        Text(
            text = "${value.length}/$PIN_LENGTH digits",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PinDigitBox(
    modifier: Modifier = Modifier,
    digit: String,
    isFocused: Boolean
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SeedWordsField(
    seedWords: String,
    readOnly: Boolean,
    supportingText: String?,
    onSeedWordsChange: (String) -> Unit,
    onCopySeedWords: () -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = seedWords,
        onValueChange = onSeedWordsChange,
        label = { Text("Seed words") },
        minLines = 3,
        readOnly = readOnly,
        trailingIcon = {
            IconButton(
                enabled = seedWords.isNotBlank(),
                onClick = onCopySeedWords
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy seed words")
            }
        },
        supportingText = supportingText?.let { text -> { Text(text) } }
    )
}

private fun copySeedWordsToClipboard(
    clipboardManager: ClipboardManager,
    seedWords: String,
    onToast: (ProcessToastEvent) -> Unit
) {
    if (seedWords.isBlank()) {
        onToast(
            ProcessToastEvent(
                kind = ProcessToastKind.Failure,
                detail = "No seed words are available to copy yet."
            )
        )
        return
    }
    clipboardManager.setText(AnnotatedString(seedWords))
    onToast(
        ProcessToastEvent(
            kind = ProcessToastKind.Success,
            detail = "Seed words copied to clipboard."
        )
    )
}

@Composable
private fun NikField(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(NIK_LENGTH)) },
            label = { Text("NIK (16 digits)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = value.isNotEmpty() && value.length != NIK_LENGTH,
            supportingText = {
                Text("${value.length}/$NIK_LENGTH digits")
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(NIK_GROUP_COUNT) { groupIndex ->
                val start = groupIndex * NIK_GROUP_SIZE
                val groupValue = value.drop(start).take(NIK_GROUP_SIZE)
                val isActive = value.length in start until (start + NIK_GROUP_SIZE) ||
                    (value.length == NIK_LENGTH && groupIndex == NIK_GROUP_COUNT - 1)
                NikDigitGroup(
                    modifier = Modifier.weight(1f),
                    value = groupValue,
                    isActive = isActive
                )
            }
        }
    }
}

@Composable
private fun NikDigitGroup(
    modifier: Modifier = Modifier,
    value: String,
    isActive: Boolean
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.padEnd(NIK_GROUP_SIZE, '-'),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun SimpleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) }
    )
}

@Composable
private fun FormErrorText(message: String?) {
    if (message != null) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun validateMatchingPin(pin: String, confirmPin: String): String? {
    if (pin.length != PIN_LENGTH) return "PIN must contain exactly 6 digits."
    if (pin != confirmPin) return "PIN and confirmation must match."
    return null
}

private const val PIN_LENGTH = 6
private const val NIK_LENGTH = 16
private const val NIK_GROUP_SIZE = 4
private const val NIK_GROUP_COUNT = 4
private const val AUTH_SUCCESS_NAVIGATION_DELAY_MS = 1_200L
