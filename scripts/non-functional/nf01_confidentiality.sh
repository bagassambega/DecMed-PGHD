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

if [[ -n "${UNAUTHORIZED_ACCESS_TOKEN_READ_PGHD:-}" ]]; then
  unauthorized_list_status="$(curl -sS -o /tmp/decmed_nf01_unauthorized_list.out -w '%{http_code}' \
    -H "Authorization: Bearer $UNAUTHORIZED_ACCESS_TOKEN_READ_PGHD" \
    "$PRE_BASE_URL/api/v1/pghd?patient_iota_address=$PATIENT_IOTA_ADDRESS" || true)"

  if [[ "$unauthorized_list_status" != "400" && "$unauthorized_list_status" != "401" && "$unauthorized_list_status" != "403" ]]; then
    fail "Expected unauthorized READ_PGHD token to be rejected with HTTP 400/401/403, got HTTP $unauthorized_list_status: $(cat /tmp/decmed_nf01_unauthorized_list.out)"
  fi

  if grep -Eq '"enc_pghd"|"c_frag"|"enc_aes_key_nonce"|"data_pre_secret_key' /tmp/decmed_nf01_unauthorized_list.out; then
    fail "Unauthorized response leaked PGHD decryption material: $(cat /tmp/decmed_nf01_unauthorized_list.out)"
  fi

  pass "PGHD list with token lacking active grant/keys is rejected with HTTP $unauthorized_list_status and leaks no decryption material"

  unauthorized_detail_status="$(curl -sS -o /tmp/decmed_nf01_unauthorized_detail.out -w '%{http_code}' \
    -H "Authorization: Bearer $UNAUTHORIZED_ACCESS_TOKEN_READ_PGHD" \
    "$PRE_BASE_URL/api/v1/pghd/0?patient_iota_address=$PATIENT_IOTA_ADDRESS" || true)"

  if [[ "$unauthorized_detail_status" != "400" && "$unauthorized_detail_status" != "401" && "$unauthorized_detail_status" != "403" ]]; then
    fail "Expected unauthorized PGHD detail request to be rejected with HTTP 400/401/403, got HTTP $unauthorized_detail_status: $(cat /tmp/decmed_nf01_unauthorized_detail.out)"
  fi

  if grep -Eq '"enc_pghd"|"c_frag"|"enc_aes_key_nonce"|"data_pre_secret_key' /tmp/decmed_nf01_unauthorized_detail.out; then
    fail "Unauthorized detail response leaked PGHD decryption material: $(cat /tmp/decmed_nf01_unauthorized_detail.out)"
  fi

  pass "PGHD detail with token lacking active grant/keys is rejected with HTTP $unauthorized_detail_status and leaks no decryption material"
else
  pass "UNAUTHORIZED_ACCESS_TOKEN_READ_PGHD is not set; optional active unauthorized personnel check skipped"
fi

if [[ -n "${AUTHORIZED_ACCESS_TOKEN_READ_PGHD:-}" ]]; then
  authorized_list_status="$(curl -sS -o /tmp/decmed_nf01_authorized_list.out -w '%{http_code}' \
    -H "Authorization: Bearer $AUTHORIZED_ACCESS_TOKEN_READ_PGHD" \
    "$PRE_BASE_URL/api/v1/pghd?patient_iota_address=$PATIENT_IOTA_ADDRESS" || true)"

  if [[ "$authorized_list_status" == "200" ]]; then
    pass "PGHD list with active READ_PGHD token returns HTTP 200"
  else
    fail "Expected authorized READ_PGHD token to read list, got HTTP $authorized_list_status: $(cat /tmp/decmed_nf01_authorized_list.out)"
  fi
else
  pass "AUTHORIZED_ACCESS_TOKEN_READ_PGHD is not set; optional positive authorized personnel check skipped"
fi

pass "NF01 confidentiality evaluation finished"
