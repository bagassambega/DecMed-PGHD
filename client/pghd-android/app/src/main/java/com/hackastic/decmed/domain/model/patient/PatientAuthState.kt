package com.hackastic.decmed.domain.model.patient

sealed interface PatientAuthState {
    data object Loading : PatientAuthState
    data object NeedsSignupOrSignin : PatientAuthState
    data class NeedsProfile(val patientId: String) : PatientAuthState
    data class NeedsPin(val patientId: String) : PatientAuthState
    data class Authenticated(
        val patientId: String,
        val iotaAddress: String?,
        val displayName: String?
    ) : PatientAuthState
}
