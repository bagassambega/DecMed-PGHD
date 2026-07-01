#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command python3
require_command cargo

FIXTURE_PATH="${PGHD_PRE_STRESS_FIXTURE:-$ROOT_DIR/tmp/pghd-pre-stress-fixtures.json}"
ACCOUNTS_PATH="${PGHD_PRE_STRESS_ACCOUNTS:-$ROOT_DIR/tmp/pghd-pre-stress-dummy-accounts.txt}"
RESULT_PATH="${PGHD_PRE_STRESS_RESULT:-$ROOT_DIR/tmp/pghd-pre-stress-results.json}"
PATIENTS="${PGHD_PRE_STRESS_PATIENTS:-20}"
BATCHES_PER_PATIENT="${PGHD_PRE_STRESS_BATCHES_PER_PATIENT:-1}"
RECORDS_PER_BATCH="${PGHD_PRE_STRESS_RECORDS_PER_BATCH:-60}"
CONCURRENCY="${PGHD_PRE_STRESS_CONCURRENCY:-20}"
NIK_START="${PGHD_PRE_STRESS_NIK_START:-9000000000000000}"
DUMMY_PIN="${PGHD_PRE_STRESS_PIN:-123456}"
PROGRESS_EVERY="${PGHD_PRE_STRESS_PROGRESS_EVERY:-10}"
REGISTER_KEYS_FLAG=()
REGISTER_E2E_FLAG=()
REUSE_FIXTURE_FLAG=()

if [[ "${PGHD_PRE_STRESS_REGISTER_KEYS:-false}" == "true" ]]; then
  REGISTER_KEYS_FLAG+=(--register-pghd-patient)
fi

if [[ "${PGHD_PRE_STRESS_REGISTER_E2E:-true}" == "true" ]]; then
  REGISTER_E2E_FLAG+=(--register-e2e)
else
  REGISTER_E2E_FLAG+=(--generate-only)
fi

if [[ "${PGHD_PRE_STRESS_REUSE_EXISTING_FIXTURE:-true}" == "true" && -f "$FIXTURE_PATH" ]]; then
  REUSE_FIXTURE_FLAG+=(--reuse-existing-fixture "$FIXTURE_PATH")
fi

IOTA_CLI="$ROOT_DIR/crypto/decmed-iota/target/debug/decmed_iota_cli"
CRYPTO_CLI="$ROOT_DIR/crypto/decmed-crypto/target/debug/decmed_crypto_cli"

info "Building helper CLIs"
cargo build --manifest-path "$ROOT_DIR/crypto/decmed-iota/Cargo.toml" --bin decmed_iota_cli
cargo build --manifest-path "$ROOT_DIR/crypto/decmed-crypto/Cargo.toml" --bin decmed_crypto_cli

info "Generating PRE scalability fixtures"
python3 "$ROOT_DIR/scripts/pghd_generate_pre_stress_fixtures.py" \
  --output "$FIXTURE_PATH" \
  --accounts-output "$ACCOUNTS_PATH" \
  --patients "$PATIENTS" \
  --batches-per-patient "$BATCHES_PER_PATIENT" \
  --records-per-batch "$RECORDS_PER_BATCH" \
  --nik-start "$NIK_START" \
  --dummy-pin "$DUMMY_PIN" \
  --iota-cli "$IOTA_CLI" \
  --crypto-cli "$CRYPTO_CLI" \
  --env-file "${PGHD_PRE_STRESS_ENV_FILE:-$ROOT_DIR/client/pghd-android/.env}" \
  --pre-base-url "$PRE_BASE_URL" \
  --allow-existing \
  "${REUSE_FIXTURE_FLAG[@]}" \
  "${REGISTER_E2E_FLAG[@]}"

info "Running live PRE scalability stress test"
python3 "$ROOT_DIR/scripts/pghd_pre_stress_test.py" \
  --fixture "$FIXTURE_PATH" \
  --pre-base-url "$PRE_BASE_URL" \
  --concurrency "$CONCURRENCY" \
  --progress-every "$PROGRESS_EVERY" \
  --output "$RESULT_PATH" \
  "${REGISTER_KEYS_FLAG[@]}"

pass "PRE scalability stress test finished"
