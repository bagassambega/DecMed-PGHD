#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command python3

info "Scalability / multi-patient correctness simulation"
info "This scenario checks one concurrent synthetic PGHD submit from each test patient."
info "It is not a production backend benchmark; it verifies state partitioning, retry, and idempotency behavior."

PATIENTS="${SCALABILITY_PATIENTS:-20}"
BATCHES_PER_PATIENT="${SCALABILITY_BATCHES_PER_PATIENT:-1}"
RECORDS_PER_BATCH="${SCALABILITY_RECORDS_PER_BATCH:-60}"
CONCURRENCY="${SCALABILITY_CONCURRENCY:-20}"
TRANSIENT_FAILURE_RATE="${SCALABILITY_TRANSIENT_FAILURE_RATE:-0.05}"
EXTRA_ARGS=()
if [[ "${SCALABILITY_INCLUDE_DUPLICATE_RETRIES:-false}" == "true" ]]; then
  EXTRA_ARGS+=(--include-duplicate-retries)
fi

python3 "$ROOT_DIR/scripts/pghd_scalability_simulation.py" \
  --patients "$PATIENTS" \
  --batches-per-patient "$BATCHES_PER_PATIENT" \
  --records-per-batch "$RECORDS_PER_BATCH" \
  --concurrency "$CONCURRENCY" \
  --transient-failure-rate "$TRANSIENT_FAILURE_RATE" \
  "${EXTRA_ARGS[@]}"

pass "Synthetic scalability scenario preserves per-patient PGHD state under concurrent submits"

if [[ -n "${PGHD_SCALABILITY_PAYLOAD_DIR:-}" ]]; then
  info "Running optional live PRE replay from PGHD_SCALABILITY_PAYLOAD_DIR=$PGHD_SCALABILITY_PAYLOAD_DIR"
  python3 "$ROOT_DIR/scripts/pghd_scalability_simulation.py" \
    --live-payload-dir "$PGHD_SCALABILITY_PAYLOAD_DIR" \
    --pre-base-url "$PRE_BASE_URL" \
    --concurrency "$CONCURRENCY"
  pass "Live PRE scalability replay accepted all configured valid payloads"
else
  info "PGHD_SCALABILITY_PAYLOAD_DIR is not set; skipping optional live PRE replay."
fi

pass "Scalability simulation finished"
