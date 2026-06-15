#!/usr/bin/env bash
set -Eeuo pipefail

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

on_error() {
  local exit_code=$?
  local line_no=${BASH_LINENO[0]:-unknown}
  local command=${BASH_COMMAND:-unknown}
  printf '[FAIL] Command failed at line %s with exit code %s: %s\n' "$line_no" "$exit_code" "$command" >&2
  exit "$exit_code"
}

trap on_error ERR

require_command() {
  if command -v "$1" >/dev/null 2>&1; then
    pass "Required command found: $1"
  else
    fail "Required command not found: $1"
  fi
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

require_file_not_contains() {
  local file="$1"
  local pattern="$2"
  local description="$3"

  if grep -Eq "$pattern" "$file"; then
    fail "$description found in $file"
  else
    pass "$description"
  fi
}
