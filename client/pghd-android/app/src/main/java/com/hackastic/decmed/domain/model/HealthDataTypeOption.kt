package com.hackastic.decmed.domain.model

data class HealthDataTypeOption(
    val recordType: String,
    val displayName: String,
    val unit: String,
    val isEstimated: Boolean
) {
    val label: String
        get() = "$displayName ($unit)"
}
