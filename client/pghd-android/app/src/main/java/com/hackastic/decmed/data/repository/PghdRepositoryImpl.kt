package com.hackastic.decmed.data.repository

import com.hackastic.decmed.data.local.dao.PghdRecordDao
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.domain.repository.PghdRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PghdRepositoryImpl(
    private val pghdRecordDao: PghdRecordDao
) : PghdRepository {
    override fun getRecords(
        sourceTag: String?,
        recordType: String?,
        limit: Int
    ): Flow<List<PghdRecordEntity>> =
        pghdRecordDao.getFiltered(sourceTag, recordType, limit)

    override fun getRecordTypes(): Flow<List<String>> =
        pghdRecordDao.getRecordTypes()

    override fun getHealthConnectSourcePackages(): Flow<List<String>> =
        pghdRecordDao.getSourcePackages()

    override suspend fun getTotalCount(): Long =
        pghdRecordDao.getTotalCount()

    override suspend fun saveManualRecord(
        recordType: String,
        displayName: String,
        valueText: String,
        unit: String,
        numericValue: Double?,
        notes: String?
    ) {
        val now = System.currentTimeMillis()
        pghdRecordDao.upsert(
            PghdRecordEntity(
                uid = "manual:${UUID.randomUUID()}",
                recordType = recordType.trim(),
                displayName = displayName.trim(),
                startTimeEpochMillis = now,
                endTimeEpochMillis = now,
                unit = unit.trim(),
                valueText = valueText.trim(),
                numericValue = numericValue,
                sourceTag = PghdRecordEntity.SOURCE_MANUAL,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                syncedAtEpochMillis = now
            )
        )
    }

    override suspend fun saveHealthConnectRecords(records: List<PghdRecordEntity>) {
        pghdRecordDao.upsertAll(records)
    }

    override suspend fun getUnbatchedRecords(): List<PghdRecordEntity> =
        pghdRecordDao.getUnbatchedRecords()

    override suspend fun markRecordsBatched(recordIds: List<String>, batchId: String) {
        if (recordIds.isNotEmpty()) {
            pghdRecordDao.markRecordsBatched(recordIds, batchId)
        }
    }
}
