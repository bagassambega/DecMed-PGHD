package com.hackastic.decmed.domain.model.pghd

data class PghdSecureEnvelope(
    val batchId: String,
    val patientIdHash: String?,
    val patientIotaAddress: String?,
    val encPghd: String,
    val hCipher: String,
    val encAesKeyNonce: String,
    val capsule: String,
    val signature: String
)

data class PghdSubmitResult(
    val batchId: String,
    val accepted: Boolean,
    val message: String? = null
)
