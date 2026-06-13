use std::str::FromStr;

use aes_gcm::{aead::Aead, Aes256Gcm, KeyInit, Nonce};
use anyhow::{anyhow, Context};
use iota_types::base_types::IotaAddress;
use p256::ecdsa::{signature::hazmat::PrehashVerifier, Signature as P256Signature, VerifyingKey};
use p256::pkcs8::DecodePublicKey;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use tauri::{async_runtime::Mutex, http::StatusCode, State};
use tauri_plugin_http::reqwest;
use umbral_pre::{decrypt_original, decrypt_reencrypted, encrypt, Capsule, CapsuleFrag, PublicKey};

use crate::{
    constants::PROXY_BASE_URL,
    current_fn,
    hospital_error::HospitalError,
    types::{
        AccessData, AccessMetadata, AccessMetadataEncrypted, AppState,
        CommandNewMedicalRecordPayload, CommandUpdateMedicalRecordPayload, KeyNonce, MedicalData,
        MedicalMetadata, PatientPrivateAdministrativeData, ProxyReencryptionErrorResponse,
        ProxyReencryptionGetMedicalRecordResponseData,
        ProxyReencryptionGetMedicalRecordUpdateResponseData, ProxyReencryptionGetPghdResponseData,
        ProxyReencryptionPghdListItem, ProxyReencryptionSuccessResponse, ResponseStatus,
        SuccessResponse,
    },
    utils::{
        aes_decrypt, aes_encrypt, compute_android_pghd_pre_keys, compute_pre_keys,
        do_http_get_request_json, do_http_post_request_json, do_http_put_request_json,
        encode_activation_key_from_keys_entry, get_iota_address_from_keys_entry,
        get_iota_key_pair_from_keys_entry, get_pre_keys_from_keys_entry, parse_keys_entry,
        sanitize_clinical_text, sanitize_identifier, sanitize_input_text,
        serde_deserialize_from_base64, serde_serialize_to_base64,
    },
};
use base64::{engine::general_purpose::STANDARD, Engine as _};

#[tauri::command]
pub async fn new_medical_record(
    _state: State<'_, Mutex<AppState>>,
    access_token: String,
    data: CommandNewMedicalRecordPayload,
    patient_iota_address: String,
    patient_pre_public_key: String,
) -> Result<SuccessResponse<()>, HospitalError> {
    let req_client = reqwest::Client::new();

    let (medical_metadata, patient_iota_address) = {
        let patient_iota_address =
            IotaAddress::from_str(&patient_iota_address).context(current_fn!())?;
        let patient_pre_public_key: PublicKey =
            serde_deserialize_from_base64(patient_pre_public_key).context(current_fn!())?;

        let medical_data = sanitize_new_medical_record_payload(data).context(current_fn!())?;
        let (enc_medical_data, medical_data_key, medical_data_nonce) =
            aes_encrypt(&serde_json::to_vec(&medical_data).context(current_fn!())?)
                .context(current_fn!())?;

        let medical_data_key_nonce = KeyNonce {
            key: STANDARD.encode(medical_data_key),
            nonce: STANDARD.encode(medical_data_nonce),
        };
        let (medical_data_key_nonce_capsule, enc_medical_data_key_nonce) = encrypt(
            &patient_pre_public_key,
            &serde_json::to_vec(&medical_data_key_nonce).context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;

        let medical_metadata = MedicalMetadata {
            capsule: serde_serialize_to_base64(&medical_data_key_nonce_capsule)
                .context(current_fn!())?,
            enc_data: STANDARD.encode(enc_medical_data),
            enc_key_and_nonce: STANDARD.encode(enc_medical_data_key_nonce),
        };

        (medical_metadata, patient_iota_address)
    };

    let _ = do_http_post_request_json::<
        _,
        ProxyReencryptionSuccessResponse<()>,
        ProxyReencryptionErrorResponse,
    >(
        Some(access_token),
        &format!("{}/medical-record", PROXY_BASE_URL),
        &json!({
            "medical_metadata": serde_serialize_to_base64(&medical_metadata).context(current_fn!())?,
            "patient_iota_address": patient_iota_address.to_string(),
        }),
        &req_client,
        StatusCode::OK,
    )
    .await
    .context(current_fn!())?;

    Ok(SuccessResponse {
        status: ResponseStatus::Success,
        data: (),
    })
}

#[tauri::command]
pub async fn get_medical_record(
    state: State<'_, Mutex<AppState>>,
    access_token: String,
    index: Option<u64>,
    patient_iota_address: String,
) -> Result<SuccessResponse<Value>, HospitalError> {
    let state = state.lock().await;
    let keys_entry = parse_keys_entry(&state.keys_entry.get_secret().context(current_fn!())?)
        .context(current_fn!())?;
    let req_client = reqwest::Client::new();

    let hospital_personnel_pre_secret_key = {
        let pin = state
            .auth_state
            .session_pin
            .clone()
            .ok_or(anyhow!("Session PIN not found"))?;
        let (hospital_personnel_pre_secret_key, _) =
            get_pre_keys_from_keys_entry(&keys_entry, pin).context(current_fn!())?;

        hospital_personnel_pre_secret_key
    };

    let res = do_http_get_request_json::<
        ProxyReencryptionSuccessResponse<ProxyReencryptionGetMedicalRecordResponseData>,
        ProxyReencryptionErrorResponse,
        _,
    >(
        Some(access_token),
        &req_client,
        StatusCode::OK,
        format!(
            "{}/medical-record?index={}&patient_iota_address={}",
            PROXY_BASE_URL,
            index.unwrap_or(0),
            patient_iota_address
        ),
    )
    .await
    .context(current_fn!())?;

    let (medical_data, administrative_data) = {
        let patient_pre_public_key: PublicKey =
            serde_deserialize_from_base64(res.data.patient_pre_public_key)
                .context(current_fn!())?;
        let medical_record_pre_secret_key_seed_capsule: Capsule =
            serde_deserialize_from_base64(res.data.data_pre_secret_key_seed_capsule)
                .context(current_fn!())?;
        let medical_record_pre_secret_key_seed = decrypt_original(
            &hospital_personnel_pre_secret_key,
            &medical_record_pre_secret_key_seed_capsule,
            STANDARD
                .decode(res.data.enc_data_pre_secret_key_seed)
                .context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
        let (medical_record_pre_secret_key, medical_record_pre_public_key) =
            compute_pre_keys(&medical_record_pre_secret_key_seed).context(current_fn!())?;
        let signer_pre_public_key: PublicKey =
            serde_deserialize_from_base64(res.data.signer_pre_public_key).context(current_fn!())?;
        let c_frag_medical: CapsuleFrag =
            serde_deserialize_from_base64(res.data.c_frag_medical).context(current_fn!())?;
        let medical_data_capsule: Capsule =
            serde_deserialize_from_base64(res.data.medical_data_capsule).context(current_fn!())?;
        let verified_cfrag_medical = c_frag_medical
            .verify(
                &medical_data_capsule,
                &signer_pre_public_key,
                &patient_pre_public_key,
                &medical_record_pre_public_key,
            )
            .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
        let medical_data_key_nonce = decrypt_reencrypted(
            &medical_record_pre_secret_key,
            &patient_pre_public_key,
            &medical_data_capsule,
            [verified_cfrag_medical],
            STANDARD
                .decode(res.data.enc_medical_data_key_nonce)
                .context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
        let medical_data_key_nonce: KeyNonce =
            serde_json::from_slice(&medical_data_key_nonce).context(current_fn!())?;
        let medical_data = aes_decrypt(
            &STANDARD
                .decode(res.data.enc_medical_data)
                .context(current_fn!())?,
            &STANDARD
                .decode(medical_data_key_nonce.key)
                .context(current_fn!())?,
            &STANDARD
                .decode(medical_data_key_nonce.nonce)
                .context(current_fn!())?,
        )
        .context(current_fn!())?;
        let medical_data: MedicalData =
            serde_json::from_slice(&medical_data).context(current_fn!())?;

        let c_frag_administrative: CapsuleFrag =
            serde_deserialize_from_base64(res.data.c_frag_administrative).context(current_fn!())?;
        let administrative_data_capsule: Capsule =
            serde_deserialize_from_base64(res.data.administrative_data_capsule)
                .context(current_fn!())?;
        let verified_cfrag_administrative = c_frag_administrative
            .verify(
                &administrative_data_capsule,
                &signer_pre_public_key,
                &patient_pre_public_key,
                &medical_record_pre_public_key,
            )
            .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
        let administrative_data_key_nonce = decrypt_reencrypted(
            &medical_record_pre_secret_key,
            &patient_pre_public_key,
            &administrative_data_capsule,
            [verified_cfrag_administrative],
            STANDARD
                .decode(res.data.enc_administrative_data_key_nonce)
                .context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
        let administrative_data_key_nonce: KeyNonce =
            serde_json::from_slice(&administrative_data_key_nonce).context(current_fn!())?;
        let administrative_data = aes_decrypt(
            &STANDARD
                .decode(res.data.enc_administrative_data)
                .context(current_fn!())?,
            &STANDARD
                .decode(administrative_data_key_nonce.key)
                .context(current_fn!())?,
            &STANDARD
                .decode(administrative_data_key_nonce.nonce)
                .context(current_fn!())?,
        )
        .context(current_fn!())?;
        let administrative_data: PatientPrivateAdministrativeData =
            serde_json::from_slice(&administrative_data).context(current_fn!())?;

        (medical_data, administrative_data)
    };

    let res_data = json!({
        "administrativeData": administrative_data,
        "createdAt": res.data.medical_data_created_at,
        "currentIndex": res.data.current_index,
        "medicalData": medical_data,
        "nextIndex": res.data.next_index,
        "prevIndex": res.data.prev_index,
    });

    Ok(SuccessResponse {
        data: res_data,
        status: ResponseStatus::Success,
    })
}

#[tauri::command]
pub async fn get_pghd_list(
    access_token: String,
    patient_iota_address: String,
) -> Result<SuccessResponse<Vec<ProxyReencryptionPghdListItem>>, HospitalError> {
    let req_client = reqwest::Client::new();
    let res = do_http_get_request_json::<
        ProxyReencryptionSuccessResponse<Vec<ProxyReencryptionPghdListItem>>,
        ProxyReencryptionErrorResponse,
        _,
    >(
        Some(access_token),
        &req_client,
        StatusCode::OK,
        format!(
            "{}/pghd?patient_iota_address={}",
            PROXY_BASE_URL, patient_iota_address
        ),
    )
    .await
    .context(current_fn!())?;

    Ok(SuccessResponse {
        status: ResponseStatus::Success,
        data: res.data,
    })
}

#[tauri::command]
pub async fn get_pghd(
    state: State<'_, Mutex<AppState>>,
    access_token: String,
    index: u64,
    patient_iota_address: String,
) -> Result<SuccessResponse<Value>, HospitalError> {
    let state = state.lock().await;
    let keys_entry = parse_keys_entry(&state.keys_entry.get_secret().context(current_fn!())?)
        .context(current_fn!())?;
    let req_client = reqwest::Client::new();
    let hospital_personnel_pre_secret_key = {
        let pin = state
            .auth_state
            .session_pin
            .clone()
            .ok_or(anyhow!("Session PIN not found"))?;
        let (hospital_personnel_pre_secret_key, _) =
            get_pre_keys_from_keys_entry(&keys_entry, pin).context(current_fn!())?;

        hospital_personnel_pre_secret_key
    };

    let res = do_http_get_request_json::<
        ProxyReencryptionSuccessResponse<ProxyReencryptionGetPghdResponseData>,
        ProxyReencryptionErrorResponse,
        _,
    >(
        Some(access_token.clone()),
        &req_client,
        StatusCode::OK,
        format!(
            "{}/pghd/{}?patient_iota_address={}",
            PROXY_BASE_URL, index, patient_iota_address
        ),
    )
    .await
    .context(current_fn!())?;

    let metadata = res.data.metadata.clone();
    let batch_id = metadata
        .get("batch_id")
        .and_then(Value::as_str)
        .ok_or(anyhow!("PGHD metadata missing batch_id").context(current_fn!()))?;
    let cid = metadata
        .get("cid")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string();
    let h_cipher = metadata
        .get("h_cipher")
        .and_then(Value::as_str)
        .ok_or(anyhow!("PGHD metadata missing h_cipher").context(current_fn!()))?;
    let enc_pghd_bytes = STANDARD.decode(&res.data.enc_pghd).context(current_fn!())?;
    let h_download = hex::encode(Sha256::digest(&enc_pghd_bytes));
    if h_download != h_cipher.to_lowercase() {
        request_pghd_invalidation(
            &req_client,
            &access_token,
            &cid,
            "OUTER_HASH_MISMATCH",
            &patient_iota_address,
        )
        .await?;
        return Err(anyhow!("OUTER_SIGNATURE_MISMATCH")
            .context(current_fn!())
            .into());
    }

    let patient_pre_public_key: PublicKey =
        serde_deserialize_from_base64(res.data.patient_pre_public_key.clone())
            .context(current_fn!())?;
    let data_pre_secret_key_seed_capsule: Capsule =
        serde_deserialize_from_base64(res.data.data_pre_secret_key_seed_capsule.clone())
            .context(current_fn!())?;
    let data_pre_secret_key_seed = decrypt_original(
        &hospital_personnel_pre_secret_key,
        &data_pre_secret_key_seed_capsule,
        STANDARD
            .decode(res.data.enc_data_pre_secret_key_seed.clone())
            .context(current_fn!())?,
    )
    .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
    let (data_pre_secret_key, data_pre_public_key) =
        compute_android_pghd_pre_keys(&data_pre_secret_key_seed).context(current_fn!())?;

    let signer_pre_public_key: PublicKey =
        serde_deserialize_from_base64(res.data.signer_pre_public_key.clone())
            .context(current_fn!())?;
    let c_frag: CapsuleFrag =
        serde_deserialize_from_base64(res.data.c_frag.clone()).context(current_fn!())?;
    let pghd_capsule: Capsule = serde_deserialize_from_base64(
        metadata
            .get("capsule")
            .and_then(Value::as_str)
            .ok_or(anyhow!("PGHD metadata missing capsule").context(current_fn!()))?
            .to_string(),
    )
    .context(current_fn!())?;
    let verified_cfrag = c_frag
        .verify(
            &pghd_capsule,
            &signer_pre_public_key,
            &patient_pre_public_key,
            &data_pre_public_key,
        )
        .map_err(|e| {
            anyhow!(
                "{}. This usually means the READ_PGHD access token was generated with PRE keys that do not match this PGHD batch. Re-grant PGHD access from the Android patient app using the current hospital personnel QR, then reopen the newest PGHD batch. If this batch was submitted before patient re-registration or key rotation, submit a new PGHD batch.",
                e.0
            )
            .context(current_fn!())
        })?;
    let aes_key_nonce = decrypt_reencrypted(
        &data_pre_secret_key,
        &patient_pre_public_key,
        &pghd_capsule,
        [verified_cfrag],
        STANDARD
            .decode(res.data.enc_aes_key_nonce.clone())
            .context(current_fn!())?,
    )
    .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
    if aes_key_nonce.len() != 44 {
        return Err(anyhow!("Invalid PGHD AES key/nonce material")
            .context(current_fn!())
            .into());
    }
    let pghd_plaintext = aes_decrypt_with_aad(
        &enc_pghd_bytes,
        &aes_key_nonce[0..32],
        &aes_key_nonce[32..44],
        batch_id.as_bytes(),
    )?;
    let inner: Value = serde_json::from_slice(&pghd_plaintext).context(current_fn!())?;
    let pghd_data = inner
        .get("pghd_data")
        .ok_or(anyhow!("PGHD plaintext missing pghd_data").context(current_fn!()))?;
    let inner_signature = inner
        .get("inner_signature")
        .and_then(Value::as_str)
        .ok_or(anyhow!("PGHD plaintext missing inner_signature").context(current_fn!()))?;
    let signed_pghd_data = inner
        .get("pghd_data_json")
        .and_then(Value::as_str)
        .map(str::to_owned)
        .unwrap_or(serde_json::to_string(pghd_data).context(current_fn!())?);
    if let Err(err) = verify_pghd_inner_signature(
        &res.data.pghd_public_key,
        inner_signature,
        signed_pghd_data.as_bytes(),
    ) {
        request_pghd_invalidation(
            &req_client,
            &access_token,
            &cid,
            "INNER_SIGNATURE_INVALID",
            &patient_iota_address,
        )
        .await?;
        return Err(err);
    }

    Ok(SuccessResponse {
        status: ResponseStatus::Success,
        data: json!({
            "current_index": res.data.current_index,
            "metadata": metadata,
            "next_index": res.data.next_index,
            "pghd_data": pghd_data,
            "prev_index": res.data.prev_index,
            "verified": true,
        }),
    })
}

async fn request_pghd_invalidation(
    req_client: &reqwest::Client,
    access_token: &str,
    cid: &str,
    failure_reason: &str,
    patient_iota_address: &str,
) -> Result<(), HospitalError> {
    if cid.is_empty() {
        return Ok(());
    }

    let _ = do_http_post_request_json::<
        _,
        ProxyReencryptionSuccessResponse<()>,
        ProxyReencryptionErrorResponse,
    >(
        Some(access_token.to_string()),
        &format!("{}/pghd/invalidate", PROXY_BASE_URL),
        &json!({
            "cid": cid,
            "failure_reason": failure_reason,
            "patient_iota_address": patient_iota_address,
        }),
        req_client,
        StatusCode::OK,
    )
    .await
    .context(current_fn!())?;

    Ok(())
}

fn aes_decrypt_with_aad(
    ciphertext: &[u8],
    key: &[u8],
    nonce: &[u8],
    aad: &[u8],
) -> Result<Vec<u8>, HospitalError> {
    let cipher = Aes256Gcm::new(key.into());
    cipher
        .decrypt(
            Nonce::from_slice(nonce),
            aes_gcm::aead::Payload {
                msg: ciphertext,
                aad,
            },
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()).into())
}

fn verify_pghd_inner_signature(
    public_key_base64: &str,
    signature_base64: &str,
    signed_pghd_data: &[u8],
) -> Result<(), HospitalError> {
    let public_key_der = STANDARD.decode(public_key_base64).context(current_fn!())?;
    let verifying_key = VerifyingKey::from_public_key_der(&public_key_der)
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
    let signature_der = STANDARD.decode(signature_base64).context(current_fn!())?;
    let signature = P256Signature::from_der(&signature_der)
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
    let digest = Sha256::digest(signed_pghd_data);
    verifying_key
        .verify_prehash(&digest, &signature)
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()).into())
}

#[tauri::command]
pub async fn invalidate_pghd(
    access_token: String,
    cid: String,
    failure_reason: String,
    patient_iota_address: String,
) -> Result<SuccessResponse<()>, HospitalError> {
    let req_client = reqwest::Client::new();
    let cid = sanitize_identifier(&cid, 128);
    if cid.is_empty() {
        return Err(HospitalError::Anyhow(anyhow!("Invalid args: cid is invalid")));
    }
    let failure_reason = sanitize_input_text(&failure_reason, 256);
    if failure_reason.is_empty() {
        return Err(HospitalError::Anyhow(anyhow!(
            "Invalid args: failure_reason is invalid"
        )));
    }
    let patient_iota_address =
        IotaAddress::from_str(&patient_iota_address).context(current_fn!())?;
    let _ = do_http_post_request_json::<
        _,
        ProxyReencryptionSuccessResponse<()>,
        ProxyReencryptionErrorResponse,
    >(
        Some(access_token),
        &format!("{}/pghd/invalidate", PROXY_BASE_URL),
        &json!({
            "cid": cid,
            "failure_reason": failure_reason,
            "patient_iota_address": patient_iota_address.to_string(),
        }),
        &req_client,
        StatusCode::OK,
    )
    .await
    .context(current_fn!())?;

    Ok(SuccessResponse {
        status: ResponseStatus::Success,
        data: (),
    })
}

#[tauri::command]
pub async fn get_medical_record_update(
    state: State<'_, Mutex<AppState>>,
    access_token: String,
    index: u64,
    patient_iota_address: String,
) -> Result<SuccessResponse<Value>, HospitalError> {
    let state = state.lock().await;
    let keys_entry = parse_keys_entry(&state.keys_entry.get_secret().context(current_fn!())?)
        .context(current_fn!())?;
    let req_client = reqwest::Client::new();

    let hospital_personnel_pre_secret_key = {
        let pin = state
            .auth_state
            .session_pin
            .clone()
            .ok_or(anyhow!("Session PIN not found"))?;
        let (hospital_personnel_pre_secret_key, _) =
            get_pre_keys_from_keys_entry(&keys_entry, pin).context(current_fn!())?;

        hospital_personnel_pre_secret_key
    };

    let res = do_http_get_request_json::<
        ProxyReencryptionSuccessResponse<ProxyReencryptionGetMedicalRecordUpdateResponseData>,
        ProxyReencryptionErrorResponse,
        _,
    >(
        Some(access_token),
        &req_client,
        StatusCode::OK,
        format!(
            "{}/medical-record-update?index={}&patient_iota_address={}",
            PROXY_BASE_URL, index, patient_iota_address
        ),
    )
    .await
    .context(current_fn!())?;

    let (medical_data, administrative_data) = {
        let patient_pre_public_key: PublicKey =
            serde_deserialize_from_base64(res.data.patient_pre_public_key)
                .context(current_fn!())?;
        let medical_record_pre_secret_key_seed_capsule: Capsule =
            serde_deserialize_from_base64(res.data.data_pre_secret_key_seed_capsule)
                .context(current_fn!())?;
        let medical_record_pre_secret_key_seed = decrypt_original(
            &hospital_personnel_pre_secret_key,
            &medical_record_pre_secret_key_seed_capsule,
            STANDARD
                .decode(res.data.enc_data_pre_secret_key_seed)
                .context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
        let (medical_record_pre_secret_key, medical_record_pre_public_key) =
            compute_pre_keys(&medical_record_pre_secret_key_seed).context(current_fn!())?;
        let signer_pre_public_key: PublicKey =
            serde_deserialize_from_base64(res.data.signer_pre_public_key).context(current_fn!())?;
        let c_frag_medical: CapsuleFrag =
            serde_deserialize_from_base64(res.data.c_frag_medical).context(current_fn!())?;
        let medical_data_capsule: Capsule =
            serde_deserialize_from_base64(res.data.medical_data_capsule).context(current_fn!())?;
        let verified_cfrag_medical = c_frag_medical
            .verify(
                &medical_data_capsule,
                &signer_pre_public_key,
                &patient_pre_public_key,
                &medical_record_pre_public_key,
            )
            .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
        let medical_data_key_nonce = decrypt_reencrypted(
            &medical_record_pre_secret_key,
            &patient_pre_public_key,
            &medical_data_capsule,
            [verified_cfrag_medical],
            STANDARD
                .decode(res.data.enc_medical_data_key_nonce)
                .context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
        let medical_data_key_nonce: KeyNonce =
            serde_json::from_slice(&medical_data_key_nonce).context(current_fn!())?;
        let medical_data = aes_decrypt(
            &STANDARD
                .decode(res.data.enc_medical_data)
                .context(current_fn!())?,
            &STANDARD
                .decode(medical_data_key_nonce.key)
                .context(current_fn!())?,
            &STANDARD
                .decode(medical_data_key_nonce.nonce)
                .context(current_fn!())?,
        )
        .context(current_fn!())?;
        let medical_data: MedicalData =
            serde_json::from_slice(&medical_data).context(current_fn!())?;

        let c_frag_administrative: CapsuleFrag =
            serde_deserialize_from_base64(res.data.c_frag_administrative).context(current_fn!())?;
        let administrative_data_capsule: Capsule =
            serde_deserialize_from_base64(res.data.administrative_data_capsule)
                .context(current_fn!())?;
        let verified_cfrag_administrative = c_frag_administrative
            .verify(
                &administrative_data_capsule,
                &signer_pre_public_key,
                &patient_pre_public_key,
                &medical_record_pre_public_key,
            )
            .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
        let administrative_data_key_nonce = decrypt_reencrypted(
            &medical_record_pre_secret_key,
            &patient_pre_public_key,
            &administrative_data_capsule,
            [verified_cfrag_administrative],
            STANDARD
                .decode(res.data.enc_administrative_data_key_nonce)
                .context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
        let administrative_data_key_nonce: KeyNonce =
            serde_json::from_slice(&administrative_data_key_nonce).context(current_fn!())?;
        let administrative_data = aes_decrypt(
            &STANDARD
                .decode(res.data.enc_administrative_data)
                .context(current_fn!())?,
            &STANDARD
                .decode(administrative_data_key_nonce.key)
                .context(current_fn!())?,
            &STANDARD
                .decode(administrative_data_key_nonce.nonce)
                .context(current_fn!())?,
        )
        .context(current_fn!())?;
        let administrative_data: PatientPrivateAdministrativeData =
            serde_json::from_slice(&administrative_data).context(current_fn!())?;

        (medical_data, administrative_data)
    };

    let res_data = json!({
        "administrativeData": administrative_data,
        "createdAt": res.data.medical_data_created_at,
        "medicalData": medical_data,
    });

    Ok(SuccessResponse {
        data: res_data,
        status: ResponseStatus::Success,
    })
}

#[tauri::command]
pub async fn get_read_access_medical_personnel(
    state: State<'_, Mutex<AppState>>,
) -> Result<SuccessResponse<Vec<AccessData>>, HospitalError> {
    let state = state.lock().await;
    let keys_entry = parse_keys_entry(&state.keys_entry.get_secret().context(current_fn!())?)
        .context(current_fn!())?;

    let (
        activation_key,
        medical_personnel_iota_address,
        medical_personnel_iota_key_pair,
        medical_personnel_pre_secret_key,
    ) = {
        let pin = state
            .auth_state
            .session_pin
            .clone()
            .ok_or(anyhow!("Session PIN not found on auth state").context(current_fn!()))?;
        let activation_key =
            encode_activation_key_from_keys_entry(&keys_entry).context(current_fn!())?;
        let medical_personnel_iota_address =
            get_iota_address_from_keys_entry(&keys_entry).context(current_fn!())?;
        let medical_personnel_iota_key_pair =
            get_iota_key_pair_from_keys_entry(&keys_entry, pin.clone()).context(current_fn!())?;
        let (medical_personnel_pre_secret_key, _) =
            get_pre_keys_from_keys_entry(&keys_entry, pin).context(current_fn!())?;

        (
            activation_key,
            medical_personnel_iota_address,
            medical_personnel_iota_key_pair,
            medical_personnel_pre_secret_key,
        )
    };

    // do cleanup
    let _ = state
        .move_call
        .cleanup_read_access(
            activation_key.clone(),
            medical_personnel_iota_address,
            medical_personnel_iota_key_pair,
        )
        .await
        .context(current_fn!())?;

    // get the data
    let access = state
        .move_call
        .get_read_access(activation_key, medical_personnel_iota_address)
        .await
        .context(current_fn!())?;

    let access = access
        .into_iter()
        .map(|access| {
            let access_metadata: AccessMetadataEncrypted =
                serde_deserialize_from_base64(access.metadata).context(current_fn!())?;
            let access_metadata = decrypt_original(
                &medical_personnel_pre_secret_key,
                &serde_deserialize_from_base64(access_metadata.capsule).context(current_fn!())?,
                &STANDARD
                    .decode(access_metadata.enc_data)
                    .context(current_fn!())?,
            )
            .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
            let access_metadata: AccessMetadata =
                serde_json::from_slice(&access_metadata).context(current_fn!())?;

            let access = AccessData {
                access_data_types: access.access_data_types,
                access_token: access_metadata.access_token,
                exp: access.exp,
                medical_metadata_index: access.medical_metadata_index,
                patient_iota_address: access_metadata.patient_iota_address,
                patient_name: access_metadata.patient_name,
                patient_pre_public_key: access_metadata.patient_pre_public_key,
            };

            Ok(access)
        })
        .collect::<Result<Vec<AccessData>, HospitalError>>()?;

    Ok(SuccessResponse {
        data: access,
        status: ResponseStatus::Success,
    })
}

#[tauri::command]
pub async fn get_update_access_medical_personnel(
    state: State<'_, Mutex<AppState>>,
) -> Result<SuccessResponse<Vec<AccessData>>, HospitalError> {
    let state = state.lock().await;
    let keys_entry = parse_keys_entry(&state.keys_entry.get_secret().context(current_fn!())?)
        .context(current_fn!())?;

    let (
        activation_key,
        medical_personnel_iota_address,
        medical_personnel_iota_key_pair,
        medical_personnel_pre_secret_key,
    ) = {
        let pin = state
            .auth_state
            .session_pin
            .clone()
            .ok_or(anyhow!("Session PIN not found on auth state").context(current_fn!()))?;
        let activation_key =
            encode_activation_key_from_keys_entry(&keys_entry).context(current_fn!())?;
        let medical_personnel_iota_address =
            get_iota_address_from_keys_entry(&keys_entry).context(current_fn!())?;
        let medical_personnel_iota_key_pair =
            get_iota_key_pair_from_keys_entry(&keys_entry, pin.clone()).context(current_fn!())?;
        let (medical_personnel_pre_secret_key, _) =
            get_pre_keys_from_keys_entry(&keys_entry, pin).context(current_fn!())?;

        (
            activation_key,
            medical_personnel_iota_address,
            medical_personnel_iota_key_pair,
            medical_personnel_pre_secret_key,
        )
    };

    // do cleanup
    let _ = state
        .move_call
        .cleanup_update_access(
            activation_key.clone(),
            medical_personnel_iota_address,
            medical_personnel_iota_key_pair,
        )
        .await
        .context(current_fn!())?;

    // get the data
    let access = state
        .move_call
        .get_update_access(activation_key, medical_personnel_iota_address)
        .await
        .context(current_fn!())?;

    let access = access
        .into_iter()
        .map(|access| {
            let access_metadata: AccessMetadataEncrypted =
                serde_deserialize_from_base64(access.metadata).context(current_fn!())?;
            let access_metadata = decrypt_original(
                &medical_personnel_pre_secret_key,
                &serde_deserialize_from_base64(access_metadata.capsule).context(current_fn!())?,
                &STANDARD
                    .decode(access_metadata.enc_data)
                    .context(current_fn!())?,
            )
            .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;
            let access_metadata: AccessMetadata =
                serde_json::from_slice(&access_metadata).context(current_fn!())?;

            let access = AccessData {
                access_data_types: access.access_data_types,
                access_token: access_metadata.access_token,
                exp: access.exp,
                medical_metadata_index: access.medical_metadata_index,
                patient_iota_address: access_metadata.patient_iota_address,
                patient_name: access_metadata.patient_name,
                patient_pre_public_key: access_metadata.patient_pre_public_key,
            };

            Ok(access)
        })
        .collect::<Result<Vec<AccessData>, HospitalError>>()?;

    Ok(SuccessResponse {
        data: access,
        status: ResponseStatus::Success,
    })
}

#[tauri::command]
pub async fn update_medical_record(
    _state: State<'_, Mutex<AppState>>,
    access_token: String,
    data: CommandUpdateMedicalRecordPayload,
    patient_iota_address: String,
    patient_pre_public_key: String,
) -> Result<SuccessResponse<()>, HospitalError> {
    let req_client = reqwest::Client::new();

    let (medical_metadata, patient_iota_address) = {
        let patient_iota_address =
            IotaAddress::from_str(&patient_iota_address).context(current_fn!())?;
        let patient_pre_public_key: PublicKey =
            serde_deserialize_from_base64(patient_pre_public_key).context(current_fn!())?;

        let medical_data = sanitize_update_medical_record_payload(data).context(current_fn!())?;
        let (enc_medical_data, medical_data_key, medical_data_nonce) =
            aes_encrypt(&serde_json::to_vec(&medical_data).context(current_fn!())?)
                .context(current_fn!())?;

        let medical_data_key_nonce = KeyNonce {
            key: STANDARD.encode(medical_data_key),
            nonce: STANDARD.encode(medical_data_nonce),
        };
        let (medical_data_key_nonce_capsule, enc_medical_data_key_nonce) = encrypt(
            &patient_pre_public_key,
            &serde_json::to_vec(&medical_data_key_nonce).context(current_fn!())?,
        )
        .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?;

        let medical_metadata = MedicalMetadata {
            capsule: serde_serialize_to_base64(&medical_data_key_nonce_capsule)
                .context(current_fn!())?,
            enc_data: STANDARD.encode(enc_medical_data),
            enc_key_and_nonce: STANDARD.encode(enc_medical_data_key_nonce),
        };

        (medical_metadata, patient_iota_address)
    };

    let _ = do_http_put_request_json::<
        _,
        ProxyReencryptionSuccessResponse<()>,
        ProxyReencryptionErrorResponse,
    >(
        Some(access_token),
        &format!("{}/medical-record", PROXY_BASE_URL),
        &json!({
            "medical_metadata": serde_serialize_to_base64(&medical_metadata).context(current_fn!())?,
            "patient_iota_address": patient_iota_address.to_string(),
        }),
        &req_client,
        StatusCode::OK,
    )
    .await
    .context(current_fn!())?;

    Ok(SuccessResponse {
        status: ResponseStatus::Success,
        data: (),
    })
}

fn sanitize_new_medical_record_payload(
    data: CommandNewMedicalRecordPayload,
) -> Result<MedicalData, HospitalError> {
    sanitize_medical_data(
        data.anamnesis,
        data.diagnose,
        data.physical_check,
        data.psychological_check,
        data.therapy,
    )
}

fn sanitize_update_medical_record_payload(
    data: CommandUpdateMedicalRecordPayload,
) -> Result<MedicalData, HospitalError> {
    sanitize_medical_data(
        data.anamnesis,
        data.diagnose,
        data.physical_check,
        data.psychological_check,
        data.therapy,
    )
}

fn sanitize_medical_data(
    anamnesis: String,
    diagnose: String,
    physical_check: String,
    psychological_check: String,
    therapy: String,
) -> Result<MedicalData, HospitalError> {
    let medical_data = MedicalData {
        anamnesis: sanitize_required_clinical_field("anamnesis", &anamnesis)?,
        diagnose: sanitize_required_clinical_field("diagnose", &diagnose)?,
        physical_check: sanitize_required_clinical_field("physical_check", &physical_check)?,
        psychological_check: sanitize_required_clinical_field(
            "psychological_check",
            &psychological_check,
        )?,
        therapy: sanitize_required_clinical_field("therapy", &therapy)?,
    };

    Ok(medical_data)
}

fn sanitize_required_clinical_field(field: &str, value: &str) -> Result<String, HospitalError> {
    let sanitized = sanitize_clinical_text(value);
    if sanitized.len() < 2 {
        return Err(HospitalError::Anyhow(anyhow!(
            "Invalid args: data.{field} is invalid after sanitization"
        )));
    }
    Ok(sanitized)
}
