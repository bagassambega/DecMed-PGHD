#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PRE_BASE_URL="${PRE_BASE_URL:-http://127.0.0.1:4100}"
PATIENT_IOTA_ADDRESS="${PATIENT_IOTA_ADDRESS:-0x0000000000000000000000000000000000000000000000000000000000000000}"

pass() {
  printf '[PASS] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

info() {
  printf '[INFO] %s\n' "$1"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_file_contains() {
  local file="$1"
  local pattern="$2"
  local description="$3"

  if grep -Eq "$pattern" "$file"; then
    pass "$description"
  else
    fail "$description not found in $file"
  fi
}
