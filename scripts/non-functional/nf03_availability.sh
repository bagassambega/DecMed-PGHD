#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

info "NF03 Availability"
info "Checking batching, network-gated submission, and non-transactional collection guards."

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
  "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/local/entity/PghdBatchEntity.kt" \
  "STATUS_WAITING_FOR_TRIGGER|STATUS_PENDING|STATUS_FAILED|STATUS_SENT" \
  "PGHD batches preserve state for retry/manual sending"

pass "NF03 availability evaluation finished"
