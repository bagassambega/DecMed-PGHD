package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdSubmitResult
import kotlinx.coroutines.flow.Flow

interface PghdBatchRepository {
    fun getBatches(): Flow<List<PghdBatchEntity>>
    suspend fun createAndSaveBatch(records: List<PghdRecordEntity>, patientId: String): PghdBatchPayload
    suspend fun createEncryptedBatch(
        records: List<PghdRecordEntity>,
        patientProfile: PatientProfile,
        triggerReason: String
    ): PghdBatchEntity
    suspend fun createEncryptAndSubmitBatch(
        records: List<PghdRecordEntity>,
        patientProfile: PatientProfile
    ): PghdSubmitResult
    suspend fun submitBatch(batchId: String): PghdSubmitResult
    suspend fun submitPendingBatches(maxRetryCount: Int): List<PghdSubmitResult>
    suspend fun normalizeBatchStatuses()
    suspend fun deleteBatch(batchId: String)
}
