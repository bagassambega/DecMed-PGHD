package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import kotlinx.coroutines.flow.Flow

interface PghdRepository {
    fun getRecords(
        sourceTag: String? = null,
        recordType: String? = null,
        limit: Int = 250
    ): Flow<List<PghdRecordEntity>>

    fun getRecordTypes(): Flow<List<String>>
    fun getHealthConnectSourcePackages(): Flow<List<String>>
    suspend fun getTotalCount(): Long
    suspend fun getLatestHealthConnectEndTimeMillis(): Long?
    suspend fun saveManualRecord(
        recordType: String,
        displayName: String,
        valueText: String,
        unit: String,
        numericValue: Double?,
        notes: String?
    )
    suspend fun saveHealthConnectRecords(records: List<PghdRecordEntity>)
    suspend fun getUnbatchedRecords(): List<PghdRecordEntity>
    fun observeUnbatchedRecords(): Flow<List<PghdRecordEntity>>
    suspend fun markRecordsBatched(recordIds: List<String>, batchId: String)
}
