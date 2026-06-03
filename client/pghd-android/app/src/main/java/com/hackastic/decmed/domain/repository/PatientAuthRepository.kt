package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.domain.model.patient.PatientAuthState
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft
import kotlinx.coroutines.flow.Flow

interface PatientAuthRepository {
    val authState: Flow<PatientAuthState>

    suspend fun generateMnemonic(): String
    suspend fun signUp(draft: PatientRegistrationDraft)
    suspend fun signIn(seedWords: String, nik: String, pin: String)
    suspend fun saveProfile(profile: PatientProfile)
    suspend fun getUnlockedProfile(): PatientProfile
    suspend fun unlock(pin: String)
    suspend fun signOut()
}
