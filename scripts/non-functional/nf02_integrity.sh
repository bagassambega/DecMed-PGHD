#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command cargo
require_command iota

info "NF02 Integrity"
info "Running PRE signature verification tests for valid and tampered PGHD ciphertext."

(
  cd "$ROOT_DIR/proxy-reencryption"
  cargo test verifies_android_style_pghd_signature
  cargo test rejects_tampered_pghd_signature_payload
)

pass "PRE rejects tampered PGHD signature payload"

info "Running hospital client plaintext hash verification tests."
(
  cd "$ROOT_DIR/client/client-hospital-tauri/src-tauri"
  cargo test rejects_tampered_pghd_plain_hash
)

pass "Hospital client rejects tampered decrypted PGHD plaintext hash"

if [[ -n "${PGHD_VALID_SUBMIT_PAYLOAD:-}" ]]; then
  require_command python3
  info "Posting tampered PGHD submit payload variants to PRE."
  "$ROOT_DIR/scripts/pghd_make_tampered_payloads.py" "$PGHD_VALID_SUBMIT_PAYLOAD" --pre-base-url "$PRE_BASE_URL" --post
  pass "PRE rejects tampered PGHD submit payload variants"

  info "Creating tampered IPFS object fixture from valid PGHD payload."
  "$ROOT_DIR/scripts/pghd_make_tampered_ipfs_object.py" "$PGHD_VALID_SUBMIT_PAYLOAD"
  pass "Tampered IPFS object fixture created for access-time integrity scenario"
else
  info "PGHD_VALID_SUBMIT_PAYLOAD is not set; skipping optional live tampered submit/IPFS fixture scenario."
fi

(
  cd "$ROOT_DIR/move/decmed"
  iota move test --skip-fetch-latest-git-deps test_sc_pghd_06_read_invalidated_entry
)

pass "Smart contract rejects access to invalidated PGHD metadata"
pass "NF02 integrity evaluation finished"
