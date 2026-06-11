package com.hackastic.decmed.ui.components

import com.hackastic.decmed.data.local.entity.PghdRecordEntity

fun String.toPghdSourceDisplayLabel(): String =
    when (this) {
        PghdRecordEntity.SOURCE_HEALTH_CONNECT -> "Health Connect"
        PghdRecordEntity.SOURCE_MANUAL -> "Manual Input"
        PghdRecordEntity.SOURCE_PHONE_SENSOR -> "Phone Sensor"
        else -> this
    }
