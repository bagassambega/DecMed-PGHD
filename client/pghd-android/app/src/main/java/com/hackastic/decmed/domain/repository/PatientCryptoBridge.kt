package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft

interface PatientCryptoBridge {
    suspend fun generateMnemonic(): String
    suspend fun deriveRegistrationProfile(draft: PatientRegistrationDraft): PatientProfile
}
