package com.hackastic.decmed.data.repository

import com.hackastic.decmed.data.local.dao.PghdBatchDao
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.repository.PghdBatchRepository
import kotlinx.coroutines.flow.Flow

class PghdBatchRepositoryImpl(
    private val pghdBatchDao: PghdBatchDao
) : PghdBatchRepository {
    override fun getBatches(): Flow<List<PghdBatchEntity>> =
        pghdBatchDao.getBatches()

    override suspend fun createAndSaveBatch(
        records: List<PghdRecordEntity>,
        patientId: String
    ): PghdBatchPayload {
        val payload = PghdPayloadConverter.recordsToBatchPayload(records, patientId)
        savePayload(payload)
        return payload
    }

    override suspend fun savePayload(payload: PghdBatchPayload) {
        pghdBatchDao.savePayload(payload)
    }

    override suspend fun getPayloadJson(batchId: String): String? =
        pghdBatchDao.getPayloadJson(batchId)

    override suspend fun deleteBatch(batchId: String) {
        pghdBatchDao.deleteBatch(batchId)
    }
}
