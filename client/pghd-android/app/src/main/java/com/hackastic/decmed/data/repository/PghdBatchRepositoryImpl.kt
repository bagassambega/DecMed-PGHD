package com.hackastic.decmed.data.repository

import com.hackastic.decmed.data.local.dao.PghdBatchDao
import com.hackastic.decmed.data.local.entity.PghdBatchEntity
import com.hackastic.decmed.data.local.entity.PghdRecordEntity
import com.hackastic.decmed.data.pghd.PghdPayloadConverter
import com.hackastic.decmed.data.pghd.PghdSecureEnvelopeBuilder
import com.hackastic.decmed.data.remote.PrePghdClient
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.pghd.PghdBatchPayload
import com.hackastic.decmed.domain.model.pghd.PghdSubmitResult
import com.hackastic.decmed.domain.repository.PghdBatchRepository
import kotlinx.coroutines.flow.Flow

class PghdBatchRepositoryImpl(
    private val pghdBatchDao: PghdBatchDao,
    private val prePghdClient: PrePghdClient
) : PghdBatchRepository {
    override fun getBatches(): Flow<List<PghdBatchEntity>> =
        pghdBatchDao.getBatches()

    override suspend fun createAndSaveBatch(
        records: List<PghdRecordEntity>,
        patientId: String
    ): PghdBatchPayload {
        val payload = PghdPayloadConverter.recordsToBatchPayload(records, patientId)
        return payload
    }

    override suspend fun createEncryptedBatch(
        records: List<PghdRecordEntity>,
        patientProfile: PatientProfile,
        triggerReason: String
    ): PghdBatchEntity {
        val patientId = patientProfile.iotaAddress ?: patientProfile.idHash ?: patientProfile.id
        val payload = PghdPayloadConverter.recordsToBatchPayload(
            records = records,
            patientId = patientId,
            triggerReason = triggerReason
        )
        val envelope = PghdSecureEnvelopeBuilder.build(payload, patientProfile)
        pghdBatchDao.saveEnvelope(payload, envelope)
        return PghdPayloadConverter.payloadToBatchEntity(payload).copy(
            encPghd = envelope.encPghd,
            encAesKeyNonce = envelope.encAesKeyNonce,
            capsule = envelope.capsule,
            hCipher = envelope.hCipher,
            pghdOuterSignature = envelope.pghdOuterSignature
        )
    }

    override suspend fun createEncryptAndSubmitBatch(
        records: List<PghdRecordEntity>,
        patientProfile: PatientProfile
    ): PghdSubmitResult {
        prePghdClient.pushRegistration(patientProfile)
        val batch = createEncryptedBatch(
            records = records,
            patientProfile = patientProfile,
            triggerReason = PghdBatchPayload.TRIGGER_MANUAL_SUBMIT
        )
        return submitBatch(batch)
    }

    override suspend fun submitBatch(batchId: String): PghdSubmitResult {
        val batch = pghdBatchDao.getBatch(batchId)
            ?: return PghdSubmitResult(
                batchId = batchId,
                accepted = false,
                message = "PGHD batch was not found."
            )
        return submitBatch(batch)
    }

    override suspend fun submitPendingBatches(maxRetryCount: Int): List<PghdSubmitResult> {
        normalizeStalePendingBatches()
        val batches = pghdBatchDao.getBatchesByStatus(
            listOf(PghdBatchEntity.STATUS_WAITING_FOR_TRIGGER, PghdBatchEntity.STATUS_FAILED)
        )
        return batches.map { submitBatch(it, maxRetryCount) }
    }

    override suspend fun deleteBatch(batchId: String) {
        pghdBatchDao.deleteBatch(batchId)
    }

    override suspend fun normalizeBatchStatuses() {
        normalizeStalePendingBatches()
    }

    private suspend fun submitBatch(
        batch: PghdBatchEntity,
        maxRetryCount: Int = 3
    ): PghdSubmitResult {
        normalizeStalePendingBatches()
        val attemptAt = System.currentTimeMillis()
        pghdBatchDao.markDeliveryInProgress(
            batchId = batch.batchId,
            status = PghdBatchEntity.STATUS_PENDING,
            lastAttemptEpochMillis = attemptAt
        )
        return try {
            val result = prePghdClient.submitPghd(batch.toEnvelope())
            pghdBatchDao.updateDeliveryState(
                batchId = batch.batchId,
                status = PghdBatchEntity.STATUS_SENT,
                retryCount = batch.retryCount,
                lastAttemptEpochMillis = attemptAt
            )
            result
        } catch (err: Exception) {
            val retryCount = batch.retryCount + 1
            val status = if (retryCount >= maxRetryCount) {
                PghdBatchEntity.STATUS_PERMANENT_FAILURE
            } else {
                PghdBatchEntity.STATUS_FAILED
            }
            pghdBatchDao.updateDeliveryState(
                batchId = batch.batchId,
                status = status,
                retryCount = retryCount,
                lastAttemptEpochMillis = attemptAt
            )
            PghdSubmitResult(
                batchId = batch.batchId,
                accepted = false,
                message = err.message ?: "Unable to submit PGHD batch."
            )
        }
    }

    private fun PghdBatchEntity.toEnvelope() =
        com.hackastic.decmed.domain.model.pghd.PghdSecureEnvelope(
            batchId = batchId,
            patientIdHash = null,
            patientIotaAddress = patientId,
            encPghd = encPghd,
            hCipher = hCipher,
            encAesKeyNonce = encAesKeyNonce,
            capsule = capsule,
            pghdOuterSignature = pghdOuterSignature
        )

    private suspend fun normalizeStalePendingBatches() {
        pghdBatchDao.resetStalePendingBatches(
            pendingStatus = PghdBatchEntity.STATUS_PENDING,
            newStatus = PghdBatchEntity.STATUS_WAITING_FOR_TRIGGER,
            staleBeforeEpochMillis = System.currentTimeMillis() - STALE_PENDING_TIMEOUT_MILLIS
        )
    }

    private companion object {
        const val STALE_PENDING_TIMEOUT_MILLIS = 10 * 60 * 1000L
    }
}
