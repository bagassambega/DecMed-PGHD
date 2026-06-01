package com.hackastic.decmed.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.viewmodel.PatientAuthViewModel

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
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var seedWords by rememberSaveable { mutableStateOf("") }
    var nik by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.generatedSeedWords) {
        if (uiState.generatedSeedWords.isNotBlank()) {
            seedWords = uiState.generatedSeedWords
        }
    }

    PatientAuthFormScaffold(
        title = "Create Patient Account",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = onBack
    ) {
        PinFields(
            pin = pin,
            confirmPin = confirmPin,
            onPinChange = { pin = it },
            onConfirmPinChange = { confirmPin = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = seedWords,
            onValueChange = { seedWords = it },
            label = { Text("Seed words") },
            minLines = 3
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                viewModel.generateMnemonic()
            },
            enabled = !uiState.isBusy
        ) {
            Text("Generate Seed Words")
        }
        Spacer(modifier = Modifier.height(12.dp))
        NikField(value = nik, onValueChange = { nik = it })
        FormErrorText(formError)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy,
            onClick = {
                formError = validateMatchingPin(pin, confirmPin)
                if (formError == null) {
                    viewModel.signUp(pin, seedWords, nik, onCompleted)
                }
            }
        ) {
            Text("Continue")
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
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var seedWords by rememberSaveable { mutableStateOf("") }
    var nik by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }

    PatientAuthFormScaffold(
        title = "Recover Patient Account",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = onBack
    ) {
        PinFields(
            pin = pin,
            confirmPin = confirmPin,
            onPinChange = { pin = it },
            onConfirmPinChange = { confirmPin = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = seedWords,
            onValueChange = { seedWords = it },
            label = { Text("Seed words") },
            minLines = 3
        )
        Spacer(modifier = Modifier.height(12.dp))
        NikField(value = nik, onValueChange = { nik = it })
        FormErrorText(formError)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy,
            onClick = {
                formError = validateMatchingPin(pin, confirmPin)
                if (formError == null) {
                    viewModel.signIn(pin, seedWords, nik, onCompleted)
                }
            }
        ) {
            Text("Recover")
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
    var name by rememberSaveable { mutableStateOf("") }
    var birthPlace by rememberSaveable { mutableStateOf("") }
    var dateOfBirth by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var religion by rememberSaveable { mutableStateOf("") }
    var education by rememberSaveable { mutableStateOf("") }
    var occupation by rememberSaveable { mutableStateOf("") }
    var maritalStatus by rememberSaveable { mutableStateOf("") }

    PatientAuthFormScaffold(
        title = "Complete Profile",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = null
    ) {
        SimpleTextField("Name", name) { name = it }
        SimpleTextField("Birth place", birthPlace) { birthPlace = it }
        SimpleTextField("Date of birth (YYYY-MM-DD)", dateOfBirth) { dateOfBirth = it }
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
                    onCompleted
                )
            }
        ) {
            Text("Save Profile")
        }
    }
}

@Composable
fun PatientUnlockScreen(
    viewModel: PatientAuthViewModel,
    onUnlocked: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by rememberSaveable { mutableStateOf("") }

    PatientAuthFormScaffold(
        title = "Unlock Patient Session",
        isBusy = uiState.isBusy,
        errorMessage = uiState.errorMessage,
        onDismissError = viewModel::clearError,
        onBack = null
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isBusy,
            onClick = { viewModel.unlock(pin, onUnlocked) }
        ) {
            Text("Unlock")
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
    content: @Composable ColumnScope.() -> Unit
) {
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

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = {
                Button(onClick = onDismissError) {
                    Text("OK")
                }
            },
            title = { Text("Patient setup") },
            text = { Text(errorMessage) }
        )
    }
}

@Composable
private fun PinFields(
    pin: String,
    confirmPin: String,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = pin,
        onValueChange = { if (it.length <= 6) onPinChange(it.filter(Char::isDigit)) },
        label = { Text("PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = confirmPin,
        onValueChange = { if (it.length <= 6) onConfirmPinChange(it.filter(Char::isDigit)) },
        label = { Text("Confirm PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
private fun NikField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = { if (it.length <= 16) onValueChange(it.filter(Char::isDigit)) },
        label = { Text("NIK") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
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
    if (pin.length != 6) return "PIN must contain exactly 6 digits."
    if (pin != confirmPin) return "PIN and confirmation must match."
    return null
}
