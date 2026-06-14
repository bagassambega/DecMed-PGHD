#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command curl

info "NF01 Confidentiality"
info "PRE endpoint: $PRE_BASE_URL"
info "Patient address: $PATIENT_IOTA_ADDRESS"

health_status="$(curl -sS -o /dev/null -w '%{http_code}' "$PRE_BASE_URL/health" || true)"
if [[ "$health_status" != "200" ]]; then
  fail "PRE health check failed. Start PRE or set PRE_BASE_URL. HTTP status: $health_status"
fi
pass "PRE health check returns HTTP 200"

no_auth_status="$(curl -sS -o /tmp/decmed_nf01_no_auth.out -w '%{http_code}' \
  "$PRE_BASE_URL/api/v1/pghd?patient_iota_address=$PATIENT_IOTA_ADDRESS" || true)"

if [[ "$no_auth_status" == "401" || "$no_auth_status" == "403" ]]; then
  pass "PGHD list without bearer token is rejected with HTTP $no_auth_status"
else
  fail "Expected HTTP 401/403 without bearer token, got HTTP $no_auth_status: $(cat /tmp/decmed_nf01_no_auth.out)"
fi

invalid_auth_status="$(curl -sS -o /tmp/decmed_nf01_invalid_auth.out -w '%{http_code}' \
  -H 'Authorization: Bearer invalid-token' \
  "$PRE_BASE_URL/api/v1/pghd?patient_iota_address=$PATIENT_IOTA_ADDRESS" || true)"

if [[ "$invalid_auth_status" == "401" || "$invalid_auth_status" == "403" ]]; then
  pass "PGHD list with invalid bearer token is rejected with HTTP $invalid_auth_status"
else
  fail "Expected HTTP 401/403 with invalid bearer token, got HTTP $invalid_auth_status: $(cat /tmp/decmed_nf01_invalid_auth.out)"
fi

pass "NF01 confidentiality evaluation finished"
