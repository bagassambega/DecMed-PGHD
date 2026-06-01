package com.hackastic.decmed.data.patient

import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft
import com.hackastic.decmed.domain.repository.PatientCryptoBridge
import java.security.MessageDigest

class PrototypePatientCryptoBridge : PatientCryptoBridge {
    override suspend fun generateMnemonic(): String {
        return "abandon ability able about above absent absorb abstract absurd abuse access accident"
    }

    override suspend fun deriveRegistrationProfile(draft: PatientRegistrationDraft): PatientProfile {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(draft.nik.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return PatientProfile(
            id = draft.nik,
            idHash = hash,
            iotaAddress = null,
            prePublicKey = null
        )
    }
}
