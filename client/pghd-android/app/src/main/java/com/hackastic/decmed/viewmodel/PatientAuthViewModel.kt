package com.hackastic.decmed.viewmodel

import android.app.Application
import com.hackastic.decmed.utils.DecmedLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackastic.decmed.MainApplication
import com.hackastic.decmed.domain.model.patient.PatientAuthState
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientAuthUiState(
    val generatedSeedWords: String = "",
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

class PatientAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MainApplication).container.patientAuthRepository

    val authState: StateFlow<PatientAuthState> = repository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PatientAuthState.Loading
    )

    private val _uiState = MutableStateFlow(PatientAuthUiState())
    val uiState: StateFlow<PatientAuthUiState> = _uiState.asStateFlow()

    fun generateMnemonic() {
        viewModelScope.launch {
            runBusy("generate mnemonic") {
                _uiState.update { it.copy(generatedSeedWords = repository.generateMnemonic()) }
            }
        }
    }

    fun signUp(pin: String, seedWords: String, nik: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("register patient") {
                repository.signUp(
                    PatientRegistrationDraft(
                        pin = pin,
                        seedWords = seedWords,
                        nik = nik
                    )
                )
                onDone()
            }
        }
    }

    fun signIn(pin: String, seedWords: String, nik: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("login patient") {
                repository.signIn(seedWords = seedWords, nik = nik, pin = pin)
                onDone()
            }
        }
    }

    fun saveProfile(profile: PatientProfile, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("complete patient profile") {
                repository.saveProfile(profile)
                onDone()
            }
        }
    }

    fun unlock(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("unlock patient session") {
                repository.unlock(pin)
                onDone()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            DecmedLog.i(TAG, "Signing out patient")
            repository.signOut()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun runBusy(operation: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        try {
            DecmedLog.i(TAG, "Starting operation: $operation")
            block()
            DecmedLog.i(TAG, "Operation succeeded: $operation")
        } catch (err: IllegalArgumentException) {
            DecmedLog.e(TAG, "Operation failed: $operation", err)
            _uiState.update { it.copy(errorMessage = err.message ?: "Invalid patient data.") }
        } catch (err: Exception) {
            DecmedLog.e(TAG, "Operation failed: $operation", err)
            _uiState.update { it.copy(errorMessage = err.message ?: "Patient operation failed.") }
        } finally {
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    companion object {
        private const val TAG = "PatientAuthViewModel"
    }
}
