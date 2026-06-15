#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

info "NF04 Availability"
info "Checking high-volume time-series batching, network-gated submission, and transaction-spike guards."

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdHealthConnectSyncWorker.kt" \
  "saveHealthConnectRecords\\(records\\)" \
  "Health Connect/wearable time-series records are persisted locally before batching"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdHealthConnectSyncWorker.kt" \
  "PghdSizeThresholdTrigger\\.scheduleBatchIfExceeded" \
  "Health Connect/wearable sync evaluates the 10 MB batch threshold"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/remote/service/SensorCollectionService.kt" \
  "sensorDataBuffer" \
  "Phone sensor time-series collection is buffered before local persistence"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/remote/service/SensorCollectionService.kt" \
  "insertAll\\(batch\\)" \
  "Phone sensor raw rows are batch-inserted locally"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/remote/service/SensorCollectionService.kt" \
  "PghdSizeThresholdTrigger\\.scheduleBatchIfExceeded" \
  "Phone sensor collection evaluates the 10 MB batch threshold"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/local/dao/PghdRecordDao.kt" \
  "WHERE batchId IS NULL" \
  "Unbatched PGHD records are queued locally"

require_file_not_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/local/dao/PghdRecordDao.kt" \
  "submitPghd|IotaPatientGateway|move_call" \
  "PGHD record DAO has no network/IOTA submit path per sensor record"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdBatchWorker.kt" \
  "getUnbatchedRecords\\(\\)" \
  "Batch worker consumes queued unbatched records"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdBatchWorker.kt" \
  "recordsToBatchPayload\\(" \
  "Batch worker converts many records into one PGHD payload"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdBatchWorker.kt" \
  "createEncryptedBatch\\(" \
  "Batch worker creates one encrypted batch envelope"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdBatchWorker.kt" \
  "markRecordsBatched\\(records\\.map" \
  "Batch worker marks source records with one batch ID"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdWorkScheduler.kt" \
  "NetworkType\\.CONNECTED" \
  "PGHD submit worker waits for available network"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdWorkScheduler.kt" \
  "PghdSubmitWorker" \
  "PGHD submit worker is scheduled through WorkManager"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/build.gradle.kts" \
  "PGHD_EARLY_TRIGGER_BYTES.*10485760" \
  "PGHD batching default threshold is 10 MB"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdSizeThresholdTrigger.kt" \
  "estimatedBytes > Env\\.pghdEarlyTriggerBytes" \
  "PGHD collection schedules immediate batching when unbatched data exceeds threshold"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/repository/PghdBatchRepositoryImpl.kt" \
  "submitPendingBatches" \
  "Repository submits queued batches rather than individual records"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/repository/PghdBatchRepositoryImpl.kt" \
  "prePghdClient\\.submitPghd\\(batch\\.toEnvelope\\(\\)\\)" \
  "PRE submit path sends one encrypted envelope per batch"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/worker/PghdSubmitWorker.kt" \
  "submitPendingBatches" \
  "Connectivity worker submits queued batches when network is available"

require_file_contains \
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/local/entity/PghdBatchEntity.kt" \
  "STATUS_WAITING_FOR_TRIGGER|STATUS_PENDING|STATUS_FAILED|STATUS_SENT" \
  "PGHD batches preserve state for offline/manual sending"

info "Running light Android unit stress test with synthetic wearable time-series records."
(
  cd "$ROOT_DIR/client/pghd-android"
  ./gradlew :app:testDebugUnitTest --tests com.hackastic.decmed.pghd.PghdAvailabilityStressTest
)
pass "Light stress test converts high-volume time-series records into few batches"

pass "NF04 availability/DoS evaluation finished"
