package com.hackastic.decmed.domain.model.patient

data class PatientProfile(
    val id: String,
    val idHash: String? = null,
    val iotaAddress: String? = null,
    val iotaKeyPair: String? = null,
    val medicalPrePublicKey: String? = null,
    val medicalPreSecretKey: String? = null,
    val pghdPrePublicKey: String? = null,
    val pghdPreSecretKey: String? = null,
    // Legacy aliases kept for normal DecMed medical-record integration.
    val prePublicKey: String? = null,
    val preSecretKey: String? = null,
    val pghdPublicKey: String? = null,
    val pghdSecretKey: String? = null,
    val name: String? = null,
    val birthPlace: String? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val religion: String? = null,
    val education: String? = null,
    val occupation: String? = null,
    val maritalStatus: String? = null
)
