#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command cargo

info "NF05 Input Sanitization"
info "Running Android PGHD sanitizer unit tests and hospital sanitizer compile checks."

(
  cd "$ROOT_DIR/client/pghd-android"
  ./gradlew :app:testDebugUnitTest --tests com.hackastic.decmed.pghd.PghdInputSanitizerTest
)

pass "Android manual/Health Connect/phone sensor sanitizer tests passed"

(
  cd "$ROOT_DIR/client/client-hospital-tauri"
  npm run check
)

pass "Hospital frontend sanitization remains type-safe"

(
  cd "$ROOT_DIR/client/client-hospital-tauri/src-tauri"
  cargo check
)

pass "Hospital backend sanitization compiles"
pass "NF05 input sanitization evaluation finished"
