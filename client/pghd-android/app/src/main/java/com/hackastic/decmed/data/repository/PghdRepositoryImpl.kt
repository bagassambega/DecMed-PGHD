package com.hackastic.decmed.data.repository

import com.hackastic.decmed.data.local.dao.PghdRecordDao
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdInputSanitizer
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

    override suspend fun getLatestHealthConnectEndTimeMillis(): Long? =
        pghdRecordDao.getLatestEndTimeMillis()

    override suspend fun saveManualRecord(
        recordType: String,
        displayName: String,
        valueText: String,
        unit: String,
        numericValue: Double?,
        notes: String?
    ) {
        val now = System.currentTimeMillis()
        val input = PghdInputSanitizer.sanitizeManualInput(
            recordType = recordType,
            displayName = displayName,
            valueText = valueText,
            unit = unit,
            notes = notes
        )
        pghdRecordDao.upsert(
            PghdRecordEntity(
                uid = "manual:${UUID.randomUUID()}",
                recordType = input.recordType,
                displayName = input.displayName,
                startTimeEpochMillis = now,
                endTimeEpochMillis = now,
                unit = input.unit,
                valueText = input.valueText,
                numericValue = input.numericValue ?: numericValue?.takeIf { it.isFinite() },
                sourceTag = PghdRecordEntity.SOURCE_MANUAL,
                notes = input.notes,
                syncedAtEpochMillis = now
            )
        )
    }

    override suspend fun saveHealthConnectRecords(records: List<PghdRecordEntity>) {
        pghdRecordDao.insertAllIgnoringConflicts(PghdInputSanitizer.sanitizeRecords(records))
    }

    override suspend fun getUnbatchedRecords(): List<PghdRecordEntity> =
        pghdRecordDao.getUnbatchedRecords()

    override suspend fun getUnbatchedRecordsSince(minEndTimeEpochMillis: Long): List<PghdRecordEntity> =
        pghdRecordDao.getUnbatchedRecordsSince(minEndTimeEpochMillis)

    override fun observeUnbatchedRecords(): Flow<List<PghdRecordEntity>> =
        pghdRecordDao.observeUnbatchedRecords()

    override suspend fun markRecordsBatched(recordIds: List<String>, batchId: String) {
        if (recordIds.isNotEmpty()) {
            pghdRecordDao.markRecordsBatched(recordIds, batchId)
        }
    }

    override suspend fun deleteStressRecordsSince(
        sourcePackagePrefix: String,
        startedAtEpochMillis: Long
    ) {
        pghdRecordDao.deleteStressRecordsSince(sourcePackagePrefix, startedAtEpochMillis)
    }
}
