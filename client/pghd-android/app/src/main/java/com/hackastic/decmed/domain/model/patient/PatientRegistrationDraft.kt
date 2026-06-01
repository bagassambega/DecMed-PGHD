package com.hackastic.decmed.domain.model.patient

data class PatientRegistrationDraft(
    val pin: String,
    val seedWords: String,
    val nik: String
)
