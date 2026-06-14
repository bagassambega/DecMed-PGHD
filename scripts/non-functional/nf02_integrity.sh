#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command cargo

info "NF02 Integrity"
info "Running PRE signature verification tests for valid and tampered PGHD payloads."

(
  cd "$ROOT_DIR/proxy-reencryption"
  cargo test verifies_android_style_pghd_outer_signature
  cargo test rejects_tampered_pghd_outer_signature_payload
)

pass "PRE rejects tampered PGHD outer signature payload"

(
  cd "$ROOT_DIR/move/decmed"
  iota move test --skip-fetch-latest-git-deps test_sc_pghd_06_read_invalidated_entry
)

pass "Smart contract rejects access to invalidated PGHD metadata"
pass "NF02 integrity evaluation finished"
