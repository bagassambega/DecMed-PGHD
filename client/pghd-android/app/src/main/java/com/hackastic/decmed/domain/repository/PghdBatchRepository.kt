package com.hackastic.decmed.domain.repository

import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import kotlinx.coroutines.flow.Flow

interface PghdBatchRepository {
    fun getBatches(): Flow<List<PghdBatchEntity>>
    suspend fun createAndSaveBatch(records: List<PghdRecordEntity>, patientId: String): PghdBatchPayload
    suspend fun savePayload(payload: PghdBatchPayload)
    suspend fun getPayloadJson(batchId: String): String?
    suspend fun deleteBatch(batchId: String)
}
