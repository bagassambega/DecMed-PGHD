#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

info "NF05 Provenance"

if [[ -n "${PGHD_JSON:-}" ]]; then
  require_command python3
  info "Checking provenance fields in exported PGHD JSON: $PGHD_JSON"
  python3 - "$PGHD_JSON" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as fh:
    payload = json.load(fh)

text = json.dumps(payload, sort_keys=True)
required = [
    "patient_iota_address",
    "recorded_at",
    "source",
    "device_type",
    "recording_method",
]
missing = [field for field in required if field not in text]
if missing:
    raise SystemExit(f"Missing provenance fields: {', '.join(missing)}")
print("[PASS] Exported PGHD JSON contains provenance fields")
PY
else
  info "PGHD_JSON is not set; running source-level provenance checks."
  require_file_contains \
    "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/pghd/PghdPayloadSerializer.kt" \
    "device_type" \
    "PGHD payload includes device_type"
  require_file_contains \
    "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/pghd/PghdPayloadSerializer.kt" \
    "recording_method" \
    "PGHD payload includes recording_method"
  require_file_contains \
    "$ROOT_DIR/client/pghd-android/app/src/main/java/com/hackastic/decmed/data/pghd/PghdPayloadConverter.kt" \
    "source|sourceTag" \
    "PGHD payload converter preserves source information"
  require_file_contains \
    "$ROOT_DIR/client/client-hospital-tauri/src-tauri/src/medical_personnel.rs" \
    "validation|invalidate_pghd|failure_reason" \
    "Healthcare client/PRE path exposes validation history"
fi

pass "NF05 provenance evaluation finished"
