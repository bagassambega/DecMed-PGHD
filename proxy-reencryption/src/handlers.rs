use std::str::FromStr;
use std::sync::Arc;

use anyhow::{anyhow, Context};
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::Response;
use axum::{Extension, Json};
use base64::Engine as _;
use iota_types::base_types::IotaAddress;
use iota_types::crypto::{
    EncodeDecodeBase64, IotaKeyPair, IotaSignature, Signature, SignatureScheme,
};
use jwt_simple::claims::Claims;
use jwt_simple::prelude::{Duration, ECDSAP256KeyPairLike};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use shared_crypto::intent::{Intent, IntentMessage};
use umbral_pre::{reencrypt, Capsule, KeyFrag, PublicKey};

use crate::constants::{
    ADMINISTRATIVE_KEYS_READ_DUR, MEDICAL_KEYS_READ_DUR, MEDICAL_KEYS_UPDATE_DUR, NONCE_EXP_DUR,
    PGHD_KEYS_READ_DUR,
};
use crate::current_fn;
use crate::proxy_error::{ProxyError, ResultExt};
use crate::types::{
    AccessKeys, AppState, AuthRole, ClientMedicalMetadata, CurrentUser,
    GenerateSignatureHandlerPayload, GetNonceHandlerPayload, HandlerCreateMedicalRecordPayload,
    HandlerGetAdministrativeDataQueryParams, HandlerGetMedicalRecordQueryParams,
    HandlerGetMedicalRecordUpdateQueryParams, HandlerGetPghdQueryParams,
    HandlerInvalidatePghdPayload, HandlerRegisterPghdPatientPayload, HandlerSubmitPghdPayload,
    HandlerUpdateMedicalRecordPayload, JwtClaims, MedicalMetadata, MoveHospitalPersonnelRole,
    PatientPrivateAdministrativeMetadata, PghdMetadata, ReencryptionPurposeType,
};
use crate::types::{GenerateJwtHandlerResponse, HandlerRevokeKeysPayload, HandlerStoreKeysPayload};
use crate::utils::Utils;

pub struct Handlers {}

impl Handlers {
    pub async fn register_pghd_patient(
        State(state): State<Arc<AppState>>,
        Json(payload): Json<HandlerRegisterPghdPatientPayload>,
    ) -> Result<Response, ProxyError> {
        let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;

        state
            .cache_store
            .set_ex(
                format!("pghd_public_key:{}", patient_iota_address),
                payload.pghd_public_key,
                365 * 24 * 60 * 60,
            )
            .context(current_fn!())?;

        Ok(Utils::build_success_response((), StatusCode::OK))
    }

    pub async fn submit_pghd(
        State(state): State<Arc<AppState>>,
        Json(payload): Json<HandlerSubmitPghdPayload>,
    ) -> Result<Response, ProxyError> {
        let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let proxy_iota_address =
            IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;
        let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
            .map_err(|e| anyhow!(e.to_string()))
            .context(current_fn!())?;

        let enc_pghd_bytes = base64::engine::general_purpose::STANDARD
            .decode(&payload.enc_pghd)
            .map_err(|_| anyhow!("Invalid enc_pghd base64"))
            .code(StatusCode::BAD_REQUEST)?;
        let h_cipher = hex::encode(Sha256::digest(&enc_pghd_bytes));
        if h_cipher != payload.h_cipher.to_lowercase() {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Invalid h_cipher for encrypted PGHD payload"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let pghd_public_key =
            Self::get_patient_pghd_public_key(&state, &patient_iota_address, proxy_iota_address)
                .await?;

        let h_cipher_bytes = hex::decode(&payload.h_cipher)
            .map_err(|_| anyhow!("Invalid h_cipher hex"))
            .code(StatusCode::BAD_REQUEST)?;
        Utils::verify_pghd_signature(&pghd_public_key, &payload.signature, &h_cipher_bytes)
            .map_err(|e| anyhow!(e.to_string()))
            .code(StatusCode::BAD_REQUEST)?;

        let cid = Utils::add_and_pin_to_ipfs(payload.enc_pghd.clone())
            .await
            .context(current_fn!())?;
        let metadata = PghdMetadata {
            batch_id: payload.batch_id.clone(),
            capsule: payload.capsule,
            cid: cid.clone(),
            created_at: Utils::sys_time_to_iso(std::time::SystemTime::now()),
            enc_aes_key_nonce: payload.enc_aes_key_nonce,
            h_cipher: payload.h_cipher,
            patient_iota_address: patient_iota_address.to_string(),
            signature: payload.signature,
            verified_by_proxy: true,
        };

        state
            .move_call
            .submit_pghd(
                cid.clone(),
                h_cipher.clone(),
                Utils::serde_serialize_to_base64(&metadata).context(current_fn!())?,
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await
            .context(current_fn!())?;

        Ok(Utils::build_success_response(
            json!({ "batch_id": payload.batch_id, "cid": cid }),
            StatusCode::CREATED,
        ))
    }

    pub async fn get_pghd_list(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Query(query): Query<HandlerGetPghdQueryParams>,
    ) -> Result<Response, ProxyError> {
        if current_user.purpose != ReencryptionPurposeType::ReadPghd {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let patient_iota_address = IotaAddress::from_str(&query.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
            .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let proxy_iota_address =
            IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

        Self::ensure_pghd_read_access(
            &state,
            &current_user,
            &patient_iota_address,
            &query.patient_iota_address,
        )?;

        let pghd_metadata = state
            .move_call
            .get_pghd_list(
                &hospital_personnel_iota_address,
                &patient_iota_address,
                proxy_iota_address,
            )
            .await
            .context(current_fn!())?;

        let mut res_data = Vec::new();
        for move_metadata in pghd_metadata {
            let metadata_value: Value =
                match Utils::serde_deserialize_from_base64(move_metadata.metadata.clone()) {
                    Ok(metadata) => metadata,
                    Err(_) => {
                        let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                            .map_err(|e| anyhow!(e.to_string()))
                            .context(current_fn!())?;
                        Self::invalidate_pghd_entry(
                            &state,
                            &hospital_personnel_iota_address,
                            move_metadata.cid.clone(),
                            "LEGACY_PGHD_SIGNATURE_SCHEMA".to_string(),
                            &patient_iota_address,
                            proxy_iota_address,
                            proxy_iota_key_pair,
                        )
                        .await?;
                        continue;
                    }
                };

            if Self::is_legacy_pghd_signature_metadata(&metadata_value) {
                let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                    .map_err(|e| anyhow!(e.to_string()))
                    .context(current_fn!())?;
                Self::invalidate_pghd_entry(
                    &state,
                    &hospital_personnel_iota_address,
                    move_metadata.cid.clone(),
                    "LEGACY_PGHD_SIGNATURE_SCHEMA".to_string(),
                    &patient_iota_address,
                    proxy_iota_address,
                    proxy_iota_key_pair,
                )
                .await?;
                continue;
            }

            let metadata: PghdMetadata =
                serde_json::from_value(metadata_value).context(current_fn!())?;
            if metadata.cid != move_metadata.cid {
                let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                    .map_err(|e| anyhow!(e.to_string()))
                    .context(current_fn!())?;
                Self::invalidate_pghd_entry(
                    &state,
                    &hospital_personnel_iota_address,
                    move_metadata.cid.clone(),
                    "METADATA_CID_MISMATCH".to_string(),
                    &patient_iota_address,
                    proxy_iota_address,
                    proxy_iota_key_pair,
                )
                .await?;
                if let Err(err) = Utils::unpin_ipfs(&metadata.cid).await {
                    eprintln!(
                        "failed to unpin mismatched PGHD payload CID {}: {:?}",
                        metadata.cid, err
                    );
                }
                continue;
            }
            res_data.push(json!({
                "index": move_metadata.index,
                "cid": move_metadata.cid,
                "payload_cid": metadata.cid.clone(),
                "h_cipher": move_metadata.h_cipher,
                "status": move_metadata.status,
                "failure_reason": move_metadata.failure_reason,
                "timestamp": move_metadata.timestamp,
                "metadata": metadata,
            }));
        }

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn get_pghd(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Path(index): Path<u64>,
        Query(query): Query<HandlerGetPghdQueryParams>,
    ) -> Result<Response, ProxyError> {
        if current_user.purpose != ReencryptionPurposeType::ReadPghd {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let patient_iota_address = IotaAddress::from_str(&query.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
            .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let proxy_iota_address =
            IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

        Self::ensure_pghd_read_access(
            &state,
            &current_user,
            &patient_iota_address,
            &query.patient_iota_address,
        )?;

        let (move_metadata, current_index, prev_index, next_index) = state
            .move_call
            .get_pghd(
                &hospital_personnel_iota_address,
                index,
                &patient_iota_address,
                proxy_iota_address,
            )
            .await
            .context(current_fn!())?;
        let metadata_value: Value =
            Utils::serde_deserialize_from_base64(move_metadata.metadata).context(current_fn!())?;
        if Self::is_legacy_pghd_signature_metadata(&metadata_value) {
            let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                .map_err(|e| anyhow!(e.to_string()))
                .context(current_fn!())?;
            Self::invalidate_pghd_entry(
                &state,
                &hospital_personnel_iota_address,
                move_metadata.cid.clone(),
                "LEGACY_PGHD_SIGNATURE_SCHEMA".to_string(),
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await?;
            return Err(ProxyError::Anyhow {
                source: anyhow!("LEGACY_PGHD_SIGNATURE_SCHEMA"),
                code: StatusCode::CONFLICT,
            });
        }
        let metadata: PghdMetadata =
            serde_json::from_value(metadata_value).context(current_fn!())?;
        if metadata.cid != move_metadata.cid {
            let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                .map_err(|e| anyhow!(e.to_string()))
                .context(current_fn!())?;
            Self::invalidate_pghd_entry(
                &state,
                &hospital_personnel_iota_address,
                move_metadata.cid.clone(),
                "METADATA_CID_MISMATCH".to_string(),
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await?;
            if let Err(err) = Utils::unpin_ipfs(&metadata.cid).await {
                eprintln!(
                    "failed to unpin mismatched PGHD payload CID {}: {:?}",
                    metadata.cid, err
                );
            }
            return Err(ProxyError::Anyhow {
                source: anyhow!("METADATA_CID_MISMATCH"),
                code: StatusCode::CONFLICT,
            });
        }
        let enc_pghd = Utils::get_data_ipfs(move_metadata.cid.clone())
            .await
            .context(current_fn!())?;
        let enc_pghd_bytes = base64::engine::general_purpose::STANDARD
            .decode(&enc_pghd)
            .map_err(|_| anyhow!("Invalid enc_pghd base64 from IPFS"))
            .code(StatusCode::BAD_GATEWAY)?;
        let h_download = hex::encode(Sha256::digest(&enc_pghd_bytes));
        if h_download != move_metadata.h_cipher.to_lowercase() {
            let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                .map_err(|e| anyhow!(e.to_string()))
                .context(current_fn!())?;
            Self::invalidate_pghd_entry(
                &state,
                &hospital_personnel_iota_address,
                move_metadata.cid.clone(),
                "OUTER_HASH_MISMATCH".to_string(),
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await?;
            return Err(ProxyError::Anyhow {
                source: anyhow!("ERR_DATA_CORRUPTED"),
                code: StatusCode::CONFLICT,
            });
        }
        let pghd_public_key =
            Self::get_patient_pghd_public_key(&state, &patient_iota_address, proxy_iota_address)
                .await?;
        let h_cipher_bytes = hex::decode(&move_metadata.h_cipher)
            .map_err(|_| anyhow!("Invalid h_cipher hex"))
            .code(StatusCode::INTERNAL_SERVER_ERROR)?;
        if let Err(err) =
            Utils::verify_pghd_signature(&pghd_public_key, &metadata.signature, &h_cipher_bytes)
        {
            let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                .map_err(|e| anyhow!(e.to_string()))
                .context(current_fn!())?;
            Self::invalidate_pghd_entry(
                &state,
                &hospital_personnel_iota_address,
                move_metadata.cid.clone(),
                "SIGNATURE_INVALID".to_string(),
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await?;
            return Err(ProxyError::Anyhow {
                source: anyhow!(err).context("SIGNATURE_INVALID"),
                code: StatusCode::CONFLICT,
            });
        }

        let access_keys: String = state
            .cache_store
            .get(&Self::access_keys_cache_key(
                ReencryptionPurposeType::ReadPghd,
                &current_user.iota_address,
                &query.patient_iota_address,
            ))
            .map_err(|_| anyhow!("Keys not found"))
            .code(StatusCode::BAD_REQUEST)?;
        let access_keys: AccessKeys =
            Utils::serde_deserialize_from_base64(access_keys).context(current_fn!())?;
        let k_frag: KeyFrag = Utils::serde_deserialize_from_base64(access_keys.k_frag.clone())
            .context(current_fn!())?;
        let signer_pre_public_key: PublicKey =
            Utils::serde_deserialize_from_base64(access_keys.signer_pre_public_key.clone())
                .context(current_fn!())?;
        let patient_pre_public_key: PublicKey =
            Utils::serde_deserialize_from_base64(access_keys.patient_pre_public_key.clone())
                .context(current_fn!())?;
        let data_pre_public_key: PublicKey =
            Utils::serde_deserialize_from_base64(access_keys.data_pre_public_key.clone())
                .context(current_fn!())?;
        let capsule: Capsule = Utils::serde_deserialize_from_base64(metadata.capsule.clone())
            .context(current_fn!())?;
        let verified_kfrag = k_frag
            .verify(
                &signer_pre_public_key,
                Some(&patient_pre_public_key),
                Some(&data_pre_public_key),
            )
            .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
        let c_frag = reencrypt(&capsule, verified_kfrag).unverify();
        let res_data = json!({
            "cid": move_metadata.cid,
            "current_index": current_index,
            "c_frag": Utils::serde_serialize_to_base64(&c_frag).context(current_fn!())?,
            "data_pre_secret_key_seed_capsule": access_keys.data_pre_secret_key_seed_capsule,
            "enc_pghd": enc_pghd,
            "enc_aes_key_nonce": metadata.enc_aes_key_nonce,
            "enc_data_pre_secret_key_seed": access_keys.enc_data_pre_secret_key_seed,
            "data_pre_public_key": access_keys.data_pre_public_key,
            "payload_cid": metadata.cid.clone(),
            "metadata": metadata,
            "next_index": next_index,
            "patient_pre_public_key": access_keys.patient_pre_public_key,
            "pghd_public_key": pghd_public_key,
            "prev_index": prev_index,
            "signer_pre_public_key": access_keys.signer_pre_public_key,
        });

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn invalidate_pghd(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Json(payload): Json<HandlerInvalidatePghdPayload>,
    ) -> Result<Response, ProxyError> {
        if current_user.purpose != ReencryptionPurposeType::ReadPghd {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
            .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        Self::ensure_pghd_read_access(
            &state,
            &current_user,
            &patient_iota_address,
            &payload.patient_iota_address,
        )?;

        let proxy_iota_address =
            IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;
        let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
            .map_err(|e| anyhow!(e.to_string()))
            .context(current_fn!())?;

        state
            .move_call
            .invalidate_pghd_entry(
                &hospital_personnel_iota_address,
                payload.cid.clone(),
                payload.failure_reason,
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await
            .context(current_fn!())?;

        let remaining_valid_metadata = state
            .move_call
            .get_pghd_list(
                &hospital_personnel_iota_address,
                &patient_iota_address,
                proxy_iota_address,
            )
            .await
            .context(current_fn!())?;
        if remaining_valid_metadata
            .iter()
            .any(|metadata| metadata.cid == payload.cid)
        {
            return Err(ProxyError::Anyhow {
                source: anyhow!(
                    "PGHD invalidation transaction succeeded but CID {} is still returned as valid on-chain",
                    payload.cid
                ),
                code: StatusCode::CONFLICT,
            });
        }

        if let Err(err) = Utils::unpin_ipfs(&payload.cid).await {
            eprintln!(
                "failed to unpin invalid PGHD CID {}: {:?}",
                payload.cid, err
            );
        }

        Ok(Utils::build_success_response((), StatusCode::OK))
    }

    async fn get_patient_pghd_public_key(
        state: &AppState,
        patient_iota_address: &IotaAddress,
        proxy_iota_address: IotaAddress,
    ) -> Result<String, ProxyError> {
        match state
            .move_call
            .get_patient_pghd_public_key(patient_iota_address, proxy_iota_address)
            .await
        {
            Ok(public_key) => Ok(public_key),
            Err(chain_error) => state
                .cache_store
                .get(&format!("pghd_public_key:{}", patient_iota_address))
                .map_err(|_| anyhow!("Patient PGHD public key not registered: {chain_error}"))
                .code(StatusCode::BAD_REQUEST),
        }
    }

    async fn invalidate_pghd_entry(
        state: &AppState,
        hospital_personnel_iota_address: &IotaAddress,
        cid: String,
        failure_reason: String,
        patient_iota_address: &IotaAddress,
        proxy_iota_address: IotaAddress,
        proxy_iota_key_pair: IotaKeyPair,
    ) -> Result<(), ProxyError> {
        state
            .move_call
            .invalidate_pghd_entry(
                hospital_personnel_iota_address,
                cid.clone(),
                failure_reason,
                patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await
            .context(current_fn!())?;

        if let Err(err) = Utils::unpin_ipfs(&cid).await {
            eprintln!("failed to unpin invalid PGHD CID {}: {:?}", cid, err);
        }

        Ok(())
    }

    fn is_legacy_pghd_signature_metadata(metadata: &Value) -> bool {
        metadata.get("pghd_outer_signature").is_some()
            || metadata
                .get("signature")
                .and_then(Value::as_str)
                .map(str::is_empty)
                .unwrap_or(true)
    }

    fn ensure_pghd_read_access(
        state: &AppState,
        current_user: &CurrentUser,
        patient_iota_address: &IotaAddress,
        patient_iota_address_raw: &str,
    ) -> Result<(), ProxyError> {
        match &current_user.role {
            AuthRole::Patient => {
                let current_patient_address = IotaAddress::from_str(&current_user.iota_address)
                    .map_err(|_| anyhow!("Invalid patient IOTA address"))
                    .code(StatusCode::BAD_REQUEST)?;
                if current_patient_address != *patient_iota_address {
                    return Err(ProxyError::Anyhow {
                        source: anyhow!("Illegal action. Patient can only access their own PGHD"),
                        code: StatusCode::UNAUTHORIZED,
                    });
                }
                Ok(())
            }
            AuthRole::MedicalPersonnel => {
                state
                    .cache_store
                    .get(&Self::access_keys_cache_key(
                        ReencryptionPurposeType::ReadPghd,
                        &current_user.iota_address,
                        patient_iota_address_raw,
                    ))
                    .map_err(|_| anyhow!("Keys not found"))
                    .code(StatusCode::BAD_REQUEST)?;
                Ok(())
            }
            _ => Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid role"),
                code: StatusCode::UNAUTHORIZED,
            }),
        }
    }

    fn access_keys_cache_key(
        purpose: ReencryptionPurposeType,
        hospital_personnel_iota_address: &str,
        patient_iota_address: &str,
    ) -> String {
        let purpose = match purpose {
            ReencryptionPurposeType::Read => "read",
            ReencryptionPurposeType::Update => "update",
            ReencryptionPurposeType::ReadPghd => "read_pghd",
        };
        format!(
            "keys:{}:{}@{}",
            purpose, hospital_personnel_iota_address, patient_iota_address
        )
    }

    pub async fn create_medical_record(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Json(payload): Json<HandlerCreateMedicalRecordPayload>,
    ) -> Result<Response, ProxyError> {
        if current_user.role != AuthRole::MedicalPersonnel {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid role"),
                code: StatusCode::UNAUTHORIZED,
            });
        }

        if current_user.purpose != ReencryptionPurposeType::Update {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let (
            medical_metadata,
            hospital_personnel_iota_address,
            proxy_iota_address,
            proxy_iota_key_pair,
            patient_iota_address,
        ) = {
            let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
                .map_err(|_| anyhow!("Invalid patient IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
            let medical_metadata: ClientMedicalMetadata =
                Utils::serde_deserialize_from_base64(payload.medical_metadata)
                    .map_err(|_| anyhow!("Invalid medical metadata"))
                    .code(StatusCode::BAD_REQUEST)?;
            let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))?;
            let proxy_iota_address =
                IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;
            let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                .map_err(|e| anyhow!(e.to_string()))
                .context(current_fn!())?;

            (
                medical_metadata,
                hospital_personnel_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
                patient_iota_address,
            )
        };

        let cid = Utils::add_and_pin_to_ipfs(medical_metadata.enc_data)
            .await
            .context(current_fn!())?;
        let created_at = Utils::sys_time_to_iso(std::time::SystemTime::now());

        let medical_metadata = MedicalMetadata {
            capsule: medical_metadata.capsule,
            cid,
            created_at,
            enc_key_and_nonce: medical_metadata.enc_key_and_nonce,
        };

        let _ = state
            .move_call
            .create_medical_record(
                &hospital_personnel_iota_address,
                Utils::serde_serialize_to_base64(&medical_metadata).context(current_fn!())?,
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await
            .context(current_fn!())?;

        Ok(Utils::build_success_response((), StatusCode::OK))
    }

    /**
     * This is just helper function
     */
    pub async fn generate_and_register_proxy_address(
        State(state): State<Arc<AppState>>,
    ) -> Result<Response, ProxyError> {
        let mnemonic = Utils::generate_mnemonic(12).context(current_fn!())?;

        let seed_words: Vec<&str> = mnemonic.words().collect();
        let seed_words = seed_words.join(" ");
        let seed = mnemonic.to_seed_normalized("proxy");

        let (proxy_iota_address, proxy_iota_keypair) =
            Utils::generate_iota_keys_ed(&seed).context(current_fn!())?;

        let _ = state
            .move_call
            .create_capability(
                &proxy_iota_address,
                IotaAddress::from_str(&state.global_admin_iota_address).context(current_fn!())?,
                IotaKeyPair::decode(&state.global_admin_iota_key_pair.clone())
                    .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?,
            )
            .await
            .context(current_fn!())?;

        let res_data = json!({
            "iota_address": proxy_iota_address.to_string(),
            "iota_key_pair": proxy_iota_keypair.encode().map_err(|e| anyhow!(e.to_string()).context(current_fn!()))?,
            "seed_words": seed_words,
        });

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn generate_jwt_handler() -> Result<Response, ProxyError> {
        let (public_key, secret_key) = Utils::generate_jwt().context(current_fn!())?;

        let res_data = GenerateJwtHandlerResponse {
            public_key,
            secret_key,
        };

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    /**
     * This is just helper function
     */
    pub async fn generate_signature(
        Json(payload): Json<GenerateSignatureHandlerPayload>,
    ) -> Result<Response, ProxyError> {
        let iota_keypair = IotaKeyPair::decode(&payload.iota_keypair)
            .map_err(|e| anyhow!(e.to_string()).context(current_fn!()))
            .code(StatusCode::BAD_REQUEST)?;

        let intent_message = IntentMessage::new(Intent::personal_message(), payload.nonce);
        let signature = Signature::new_secure(&intent_message, &iota_keypair);
        let signature_string = signature.encode_base64();

        Ok(Utils::build_success_response(
            signature_string,
            StatusCode::OK,
        ))
    }

    pub async fn get_administrative_data(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Query(query): Query<HandlerGetAdministrativeDataQueryParams>,
    ) -> Result<Response, ProxyError> {
        if current_user.role != AuthRole::AdministrativePersonnel
            && current_user.role != AuthRole::MedicalPersonnel
        {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid role"),
                code: StatusCode::UNAUTHORIZED,
            });
        }

        if current_user.purpose != ReencryptionPurposeType::Read
            && !(current_user.role == AuthRole::MedicalPersonnel
                && current_user.purpose == ReencryptionPurposeType::Update)
        {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let (hospital_personnel_iota_address, patient_iota_address, proxy_iota_address) = {
            let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
            let patient_iota_address = IotaAddress::from_str(&query.patient_iota_address)
                .map_err(|_| anyhow!("Invalid patient IOTA address"))
                .code(StatusCode::UNAUTHORIZED)?;
            let proxy_iota_address =
                IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

            (
                hospital_personnel_iota_address,
                patient_iota_address,
                proxy_iota_address,
            )
        };

        let (
            enc_patient_private_adm_data,
            access_keys,
            c_frag,
            enc_patient_private_adm_data_key_nonce,
            patient_private_adm_data_capsule,
        ) = {
            let access_keys: String = state
                .cache_store
                .get(&Self::access_keys_cache_key(
                    current_user.purpose,
                    &current_user.iota_address,
                    &query.patient_iota_address,
                ))
                .map_err(|_| anyhow!("Keys not found"))
                .code(StatusCode::BAD_REQUEST)?;

            let access_keys: AccessKeys =
                Utils::serde_deserialize_from_base64(access_keys).context(current_fn!())?;

            let patient_administrative_metadata = state
                .move_call
                .get_administrative_data(
                    &hospital_personnel_iota_address,
                    &patient_iota_address,
                    proxy_iota_address,
                )
                .await
                .context(current_fn!())?;

            let patient_private_adm_metadata: PatientPrivateAdministrativeMetadata =
                Utils::serde_deserialize_from_base64(
                    patient_administrative_metadata.private_metadata,
                )
                .context(current_fn!())?;

            let k_frag: KeyFrag = Utils::serde_deserialize_from_base64(access_keys.k_frag.clone())
                .context(current_fn!())?;
            let signer_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.signer_pre_public_key.clone())
                    .context(current_fn!())?;
            let patient_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.patient_pre_public_key.clone())
                    .context(current_fn!())?;
            let data_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.data_pre_public_key.clone())
                    .context(current_fn!())?;
            let patient_private_adm_metadata_key_nonce_capsule: Capsule =
                Utils::serde_deserialize_from_base64(patient_private_adm_metadata.capsule.clone())
                    .context(current_fn!())?;

            let verified_kfrag = k_frag
                .verify(
                    &signer_pre_public_key,
                    Some(&patient_pre_public_key),
                    Some(&data_pre_public_key),
                )
                .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
            let verified_cfrag = reencrypt(
                &patient_private_adm_metadata_key_nonce_capsule,
                verified_kfrag,
            );
            let c_frag = verified_cfrag.unverify();

            (
                patient_private_adm_metadata.enc_data,
                access_keys,
                c_frag,
                patient_private_adm_metadata.enc_key_nonce,
                patient_private_adm_metadata.capsule,
            )
        };

        let res_data = json!({
            "c_frag": Utils::serde_serialize_to_base64(&c_frag).context(current_fn!())?,
            "data_pre_public_key": access_keys.data_pre_public_key,
            "data_pre_secret_key_seed_capsule": access_keys.data_pre_secret_key_seed_capsule,
            "enc_data_pre_secret_key_seed": access_keys.enc_data_pre_secret_key_seed,
            "enc_patient_private_adm_data": enc_patient_private_adm_data,
            "enc_patient_private_adm_data_key_nonce": enc_patient_private_adm_data_key_nonce,
            "patient_pre_public_key": access_keys.patient_pre_public_key,
            "patient_private_adm_data_capsule": patient_private_adm_data_capsule,
            "signer_pre_public_key": access_keys.signer_pre_public_key,
        });

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn get_medical_record(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Query(query): Query<HandlerGetMedicalRecordQueryParams>,
    ) -> Result<Response, ProxyError> {
        if current_user.role != AuthRole::MedicalPersonnel {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid role"),
                code: StatusCode::UNAUTHORIZED,
            });
        }

        if current_user.purpose != ReencryptionPurposeType::Read {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let (hospital_personnel_iota_address, patient_iota_address, proxy_iota_address) = {
            let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
            let patient_iota_address = IotaAddress::from_str(&query.patient_iota_address)
                .map_err(|_| anyhow!("Invalid patient IOTA address"))
                .code(StatusCode::UNAUTHORIZED)?;
            let proxy_iota_address =
                IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

            (
                hospital_personnel_iota_address,
                patient_iota_address,
                proxy_iota_address,
            )
        };

        let (
            enc_administrative_data,
            enc_medical_data,
            access_keys,
            c_frag_administrative,
            c_frag_medical,
            current_index,
            prev_index,
            next_index,
            enc_medical_data_key_nonce,
            medical_data_capsule,
            medical_data_created_at,
            enc_administrative_data_key_nonce,
            administrative_data_capsule,
        ) = {
            let access_keys: String = state
                .cache_store
                .get(&Self::access_keys_cache_key(
                    ReencryptionPurposeType::Read,
                    &current_user.iota_address,
                    &query.patient_iota_address,
                ))
                .map_err(|_| anyhow!("Keys not found"))
                .code(StatusCode::BAD_REQUEST)?;

            let access_keys: AccessKeys =
                Utils::serde_deserialize_from_base64(access_keys).context(current_fn!())?;

            let (medical_metadata, administrative_metadata, current_index, prev_index, next_index) =
                state
                    .move_call
                    .get_medical_record(
                        &hospital_personnel_iota_address,
                        query.index.unwrap_or(0),
                        &patient_iota_address,
                        proxy_iota_address,
                    )
                    .await
                    .context(current_fn!())?;

            let medical_metadata: MedicalMetadata =
                Utils::serde_deserialize_from_base64(medical_metadata.metadata)
                    .context(current_fn!())?;

            let patient_private_adm_metadata: PatientPrivateAdministrativeMetadata =
                Utils::serde_deserialize_from_base64(administrative_metadata.private_metadata)
                    .context(current_fn!())?;

            let enc_medical_data = Utils::get_data_ipfs(medical_metadata.cid)
                .await
                .context(current_fn!())?;

            let k_frag: KeyFrag = Utils::serde_deserialize_from_base64(access_keys.k_frag.clone())
                .context(current_fn!())?;
            let signer_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.signer_pre_public_key.clone())
                    .context(current_fn!())?;
            let patient_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.patient_pre_public_key.clone())
                    .context(current_fn!())?;
            let medical_record_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.data_pre_public_key.clone())
                    .context(current_fn!())?;
            let medical_metadata_key_nonce_capsule: Capsule =
                Utils::serde_deserialize_from_base64(medical_metadata.capsule.clone())
                    .context(current_fn!())?;
            let patient_private_adm_metadata_key_nonce_capsule: Capsule =
                Utils::serde_deserialize_from_base64(patient_private_adm_metadata.capsule.clone())
                    .context(current_fn!())?;

            let verified_kfrag = k_frag
                .verify(
                    &signer_pre_public_key,
                    Some(&patient_pre_public_key),
                    Some(&medical_record_pre_public_key),
                )
                .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
            let verified_cfrag_medical =
                reencrypt(&medical_metadata_key_nonce_capsule, verified_kfrag.clone());
            let c_frag_medical = verified_cfrag_medical.unverify();

            let verified_cfrag_administrative = reencrypt(
                &patient_private_adm_metadata_key_nonce_capsule,
                verified_kfrag,
            );
            let c_frag_administrative = verified_cfrag_administrative.unverify();

            (
                patient_private_adm_metadata.enc_data,
                enc_medical_data,
                access_keys,
                c_frag_administrative,
                c_frag_medical,
                current_index,
                prev_index,
                next_index,
                medical_metadata.enc_key_and_nonce,
                medical_metadata.capsule,
                medical_metadata.created_at,
                patient_private_adm_metadata.enc_key_nonce,
                patient_private_adm_metadata.capsule,
            )
        };

        let res_data = json!({
            "administrative_data_capsule": administrative_data_capsule,
            "c_frag_administrative": Utils::serde_serialize_to_base64(&c_frag_administrative).context(current_fn!())?,
            "c_frag_medical": Utils::serde_serialize_to_base64(&c_frag_medical).context(current_fn!())?,
            "current_index": current_index,
            "data_pre_public_key": access_keys.data_pre_public_key,
            "data_pre_secret_key_seed_capsule": access_keys.data_pre_secret_key_seed_capsule,
            "enc_administrative_data": enc_administrative_data,
            "enc_administrative_data_key_nonce": enc_administrative_data_key_nonce,
            "enc_data_pre_secret_key_seed": access_keys.enc_data_pre_secret_key_seed,
            "enc_medical_data": enc_medical_data,
            "enc_medical_data_key_nonce": enc_medical_data_key_nonce,
            "medical_data_capsule": medical_data_capsule,
            "medical_data_created_at": medical_data_created_at,
            "next_index": next_index,
            "patient_pre_public_key": access_keys.patient_pre_public_key,
            "prev_index": prev_index,
            "signer_pre_public_key": access_keys.signer_pre_public_key,
        });

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn get_medical_record_update(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Query(query): Query<HandlerGetMedicalRecordUpdateQueryParams>,
    ) -> Result<Response, ProxyError> {
        if current_user.role != AuthRole::MedicalPersonnel {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid role"),
                code: StatusCode::UNAUTHORIZED,
            });
        }

        if current_user.purpose != ReencryptionPurposeType::Update {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let (hospital_personnel_iota_address, patient_iota_address, proxy_iota_address) = {
            let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
            let patient_iota_address = IotaAddress::from_str(&query.patient_iota_address)
                .map_err(|_| anyhow!("Invalid patient IOTA address"))
                .code(StatusCode::UNAUTHORIZED)?;
            let proxy_iota_address =
                IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

            (
                hospital_personnel_iota_address,
                patient_iota_address,
                proxy_iota_address,
            )
        };

        let (
            enc_administrative_data,
            enc_medical_data,
            access_keys,
            c_frag_administrative,
            c_frag_medical,
            enc_medical_data_key_nonce,
            medical_data_capsule,
            medical_data_created_at,
            enc_administrative_data_key_nonce,
            administrative_data_capsule,
        ) = {
            let access_keys: String = state
                .cache_store
                .get(&Self::access_keys_cache_key(
                    ReencryptionPurposeType::Update,
                    &current_user.iota_address,
                    &query.patient_iota_address,
                ))
                .map_err(|_| anyhow!("Keys not found"))
                .code(StatusCode::BAD_REQUEST)?;

            let access_keys: AccessKeys =
                Utils::serde_deserialize_from_base64(access_keys).context(current_fn!())?;

            let (medical_metadata, administrative_metadata) = state
                .move_call
                .get_medical_record_update(
                    &hospital_personnel_iota_address,
                    query.index.unwrap_or(0),
                    &patient_iota_address,
                    proxy_iota_address,
                )
                .await
                .context(current_fn!())?;

            let medical_metadata: MedicalMetadata =
                Utils::serde_deserialize_from_base64(medical_metadata.metadata)
                    .context(current_fn!())?;

            let patient_private_adm_metadata: PatientPrivateAdministrativeMetadata =
                Utils::serde_deserialize_from_base64(administrative_metadata.private_metadata)
                    .context(current_fn!())?;

            let enc_medical_data = Utils::get_data_ipfs(medical_metadata.cid)
                .await
                .context(current_fn!())?;

            let k_frag: KeyFrag = Utils::serde_deserialize_from_base64(access_keys.k_frag.clone())
                .context(current_fn!())?;
            let signer_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.signer_pre_public_key.clone())
                    .context(current_fn!())?;
            let patient_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.patient_pre_public_key.clone())
                    .context(current_fn!())?;
            let data_pre_public_key: PublicKey =
                Utils::serde_deserialize_from_base64(access_keys.data_pre_public_key.clone())
                    .context(current_fn!())?;
            let medical_metadata_key_nonce_capsule: Capsule =
                Utils::serde_deserialize_from_base64(medical_metadata.capsule.clone())
                    .context(current_fn!())?;
            let patient_private_adm_metadata_key_nonce_capsule: Capsule =
                Utils::serde_deserialize_from_base64(patient_private_adm_metadata.capsule.clone())
                    .context(current_fn!())?;

            let verified_kfrag = k_frag
                .verify(
                    &signer_pre_public_key,
                    Some(&patient_pre_public_key),
                    Some(&data_pre_public_key),
                )
                .map_err(|e| anyhow!(e.0.to_string()).context(current_fn!()))?;
            let verified_cfrag_medical =
                reencrypt(&medical_metadata_key_nonce_capsule, verified_kfrag.clone());
            let c_frag_medical = verified_cfrag_medical.unverify();

            let verified_cfrag_administrative = reencrypt(
                &patient_private_adm_metadata_key_nonce_capsule,
                verified_kfrag,
            );
            let c_frag_administrative = verified_cfrag_administrative.unverify();

            (
                patient_private_adm_metadata.enc_data,
                enc_medical_data,
                access_keys,
                c_frag_administrative,
                c_frag_medical,
                medical_metadata.enc_key_and_nonce,
                medical_metadata.capsule,
                medical_metadata.created_at,
                patient_private_adm_metadata.enc_key_nonce,
                patient_private_adm_metadata.capsule,
            )
        };

        let res_data = json!({
            "administrative_data_capsule": administrative_data_capsule,
            "c_frag_administrative": Utils::serde_serialize_to_base64(&c_frag_administrative).context(current_fn!())?,
            "c_frag_medical": Utils::serde_serialize_to_base64(&c_frag_medical).context(current_fn!())?,
            "data_pre_public_key": access_keys.data_pre_public_key,
            "data_pre_secret_key_seed_capsule": access_keys.data_pre_secret_key_seed_capsule,
            "enc_administrative_data": enc_administrative_data,
            "enc_administrative_data_key_nonce": enc_administrative_data_key_nonce,
            "enc_data_pre_secret_key_seed": access_keys.enc_data_pre_secret_key_seed,
            "enc_medical_data": enc_medical_data,
            "enc_medical_data_key_nonce": enc_medical_data_key_nonce,
            "medical_data_capsule": medical_data_capsule,
            "medical_data_created_at": medical_data_created_at,
            "patient_pre_public_key": access_keys.patient_pre_public_key,
            "signer_pre_public_key": access_keys.signer_pre_public_key,
        });

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn get_nonce_handler(
        State(state): State<Arc<AppState>>,
        Json(payload): Json<GetNonceHandlerPayload>,
    ) -> Result<Response, ProxyError> {
        let patient_iota_address = IotaAddress::from_str(&payload.iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let proxy_iota_address =
            IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

        let _ = state
            .move_call
            .is_patient_registered(&patient_iota_address, proxy_iota_address)
            .await
            .context(current_fn!())?;

        let nonce = Utils::generate_64_bytes_seed();
        let nonce = hex::encode(&nonce);

        state
            .cache_store
            .set_ex(
                format!("nonce:{}", patient_iota_address),
                nonce.clone(),
                Utils::env_u64("NONCE_EXP_DUR_SECONDS", NONCE_EXP_DUR),
            )
            .context(current_fn!())?;

        Ok(Utils::build_success_response(nonce, StatusCode::OK))
    }

    pub async fn store_keys(
        State(state): State<Arc<AppState>>,
        Json(payload): Json<HandlerStoreKeysPayload>,
    ) -> Result<Response, ProxyError> {
        let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let hospital_personnel_iota_address =
            IotaAddress::from_str(&payload.hospital_personnel_iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
        let signature = Utils::construct_signature_from_str(&payload.signature)
            .map_err(|_| anyhow!("Invalid signature"))
            .code(StatusCode::BAD_REQUEST)?;
        let proxy_iota_address =
            IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;

        let nonce: String = state
            .cache_store
            .get(&format!("nonce:{}", patient_iota_address))
            .map_err(|_| anyhow!("Nonce not found"))
            .code(StatusCode::BAD_REQUEST)?;

        let intent_message = IntentMessage::new(Intent::personal_message(), nonce);

        let _ = signature
            .verify_secure(
                &intent_message,
                patient_iota_address,
                SignatureScheme::ED25519,
            )
            .map_err(|_| anyhow!("Failed to verify signature"))
            .code(StatusCode::UNAUTHORIZED)?;

        state
            .cache_store
            .del(&format!("nonce:{}", patient_iota_address))
            .map_err(|_| anyhow!("Nonce expired"))
            .code(StatusCode::UNAUTHORIZED)?;

        // Get the role of hospital personnel
        let role = state
            .move_call
            .get_hospital_personnel_role(&hospital_personnel_iota_address, proxy_iota_address)
            .await
            .context(current_fn!())?;

        let requested_purpose = payload.purpose.unwrap_or(ReencryptionPurposeType::Read);
        let (hospital_personnel_role, read_keys_duration, update_keys_duration): (
            AuthRole,
            u64,
            Option<u64>,
        ) = match role {
            MoveHospitalPersonnelRole::AdministrativePersonnel => (
                AuthRole::AdministrativePersonnel,
                Utils::env_u64(
                    "ADMINISTRATIVE_KEYS_READ_DUR_SECONDS",
                    ADMINISTRATIVE_KEYS_READ_DUR,
                ),
                None,
            ),
            MoveHospitalPersonnelRole::MedicalPersonnel => (
                AuthRole::MedicalPersonnel,
                Utils::env_u64("MEDICAL_KEYS_READ_DUR_SECONDS", MEDICAL_KEYS_READ_DUR),
                Some(Utils::env_u64(
                    "MEDICAL_KEYS_UPDATE_DUR_SECONDS",
                    MEDICAL_KEYS_UPDATE_DUR,
                )),
            ),
            _ => {
                return Err(ProxyError::Anyhow {
                    source: anyhow!("Invalid personnel account"),
                    code: StatusCode::BAD_REQUEST,
                })
            }
        };

        // Create access token for hospital personnel
        let es256_keypair = Utils::construct_es256_key_pair_from_pem(&state.jwt_ecdsa_key_pair)
            .context(current_fn!())?;

        let read_purpose = if requested_purpose == ReencryptionPurposeType::ReadPghd {
            ReencryptionPurposeType::ReadPghd
        } else {
            ReencryptionPurposeType::Read
        };
        let read_claims = JwtClaims {
            role: hospital_personnel_role.clone(),
            purpose: read_purpose,
        };
        let read_claim_duration = if read_purpose == ReencryptionPurposeType::ReadPghd {
            Utils::env_u64("PGHD_KEYS_READ_DUR_SECONDS", PGHD_KEYS_READ_DUR)
        } else {
            read_keys_duration
        };
        let read_claims =
            Claims::with_custom_claims(read_claims, Duration::from_secs(read_claim_duration))
                .with_subject(hospital_personnel_iota_address);

        let hospital_personnel_access_token_update = if update_keys_duration.is_some()
            && requested_purpose != ReencryptionPurposeType::ReadPghd
        {
            let update_claims = JwtClaims {
                role: hospital_personnel_role,
                purpose: ReencryptionPurposeType::Update,
            };
            let update_claims = Claims::with_custom_claims(
                update_claims,
                Duration::from_secs(update_keys_duration.unwrap()),
            )
            .with_subject(hospital_personnel_iota_address);
            Some(es256_keypair.sign(update_claims).context(current_fn!())?)
        } else {
            None
        };

        let hospital_personnel_access_token_read =
            es256_keypair.sign(read_claims).context(current_fn!())?;

        let access_keys = AccessKeys {
            enc_data_pre_secret_key_seed: payload.enc_data_pre_secret_key_seed,
            k_frag: payload.k_frag,
            data_pre_public_key: payload.data_pre_public_key,
            data_pre_secret_key_seed_capsule: payload.data_pre_secret_key_seed_capsule,
            patient_pre_public_key: payload.patient_pre_public_key,
            signer_pre_public_key: payload.signer_pre_public_key,
        };

        state
            .cache_store
            .set_ex(
                Self::access_keys_cache_key(
                    read_purpose,
                    &hospital_personnel_iota_address.to_string(),
                    &patient_iota_address.to_string(),
                ),
                Utils::serde_serialize_to_base64(&access_keys).context(current_fn!())?,
                read_claim_duration,
            )
            .context(current_fn!())?;

        if let Some(update_keys_duration) = update_keys_duration {
            if requested_purpose != ReencryptionPurposeType::ReadPghd {
                state
                    .cache_store
                    .set_ex(
                        Self::access_keys_cache_key(
                            ReencryptionPurposeType::Update,
                            &hospital_personnel_iota_address.to_string(),
                            &patient_iota_address.to_string(),
                        ),
                        Utils::serde_serialize_to_base64(&access_keys).context(current_fn!())?,
                        update_keys_duration,
                    )
                    .context(current_fn!())?;
            }
        }

        let res_data = json!({
            "access_token_read": hospital_personnel_access_token_read,
            "access_token_read_pghd": if read_purpose == ReencryptionPurposeType::ReadPghd {
                Some(hospital_personnel_access_token_read.clone())
            } else {
                None
            },
            "access_token_update": hospital_personnel_access_token_update,
        });

        Ok(Utils::build_success_response(res_data, StatusCode::OK))
    }

    pub async fn revoke_keys(
        State(state): State<Arc<AppState>>,
        Json(payload): Json<HandlerRevokeKeysPayload>,
    ) -> Result<Response, ProxyError> {
        let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
            .map_err(|_| anyhow!("Invalid patient IOTA address"))
            .code(StatusCode::BAD_REQUEST)?;
        let hospital_personnel_iota_address =
            IotaAddress::from_str(&payload.hospital_personnel_iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
        let signature = Utils::construct_signature_from_str(&payload.signature)
            .map_err(|_| anyhow!("Invalid signature"))
            .code(StatusCode::BAD_REQUEST)?;

        let nonce_key = format!("nonce:{}", patient_iota_address);
        let nonce: String = state
            .cache_store
            .get(&nonce_key)
            .map_err(|_| anyhow!("Nonce not found"))
            .code(StatusCode::BAD_REQUEST)?;

        let intent_message = IntentMessage::new(Intent::personal_message(), nonce);
        signature
            .verify_secure(
                &intent_message,
                patient_iota_address,
                SignatureScheme::ED25519,
            )
            .map_err(|_| anyhow!("Failed to verify signature"))
            .code(StatusCode::UNAUTHORIZED)?;

        state
            .cache_store
            .del(&nonce_key)
            .map_err(|_| anyhow!("Nonce expired"))
            .code(StatusCode::UNAUTHORIZED)?;

        let mut revoked_keys = Vec::new();
        let purposes = match payload.purpose {
            ReencryptionPurposeType::ReadPghd => vec![ReencryptionPurposeType::ReadPghd],
            ReencryptionPurposeType::Read => vec![ReencryptionPurposeType::Read],
            ReencryptionPurposeType::Update => {
                vec![
                    ReencryptionPurposeType::Read,
                    ReencryptionPurposeType::Update,
                ]
            }
        };

        for purpose in purposes {
            let key = Self::access_keys_cache_key(
                purpose,
                &hospital_personnel_iota_address.to_string(),
                &patient_iota_address.to_string(),
            );
            state.cache_store.del(&key).context(current_fn!())?;
            revoked_keys.push(key);
        }

        Ok(Utils::build_success_response(
            json!({ "revoked_keys": revoked_keys }),
            StatusCode::OK,
        ))
    }

    pub async fn update_medical_record(
        State(state): State<Arc<AppState>>,
        Extension(current_user): Extension<CurrentUser>,
        Json(payload): Json<HandlerUpdateMedicalRecordPayload>,
    ) -> Result<Response, ProxyError> {
        if current_user.role != AuthRole::MedicalPersonnel {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid role"),
                code: StatusCode::UNAUTHORIZED,
            });
        }

        if current_user.purpose != ReencryptionPurposeType::Update {
            return Err(ProxyError::Anyhow {
                source: anyhow!("Illegal action. Invalid purpose"),
                code: StatusCode::BAD_REQUEST,
            });
        }

        let (
            medical_metadata,
            hospital_personnel_iota_address,
            proxy_iota_address,
            proxy_iota_key_pair,
            patient_iota_address,
        ) = {
            let patient_iota_address = IotaAddress::from_str(&payload.patient_iota_address)
                .map_err(|_| anyhow!("Invalid patient IOTA address"))
                .code(StatusCode::BAD_REQUEST)?;
            let medical_metadata: ClientMedicalMetadata =
                Utils::serde_deserialize_from_base64(payload.medical_metadata)
                    .map_err(|_| anyhow!("Invalid medical metadata"))
                    .code(StatusCode::BAD_REQUEST)?;
            let hospital_personnel_iota_address = IotaAddress::from_str(&current_user.iota_address)
                .map_err(|_| anyhow!("Invalid hospital personnel IOTA address"))?;
            let proxy_iota_address =
                IotaAddress::from_str(&state.proxy_iota_address).context(current_fn!())?;
            let proxy_iota_key_pair = IotaKeyPair::decode(&state.proxy_iota_key_pair)
                .map_err(|e| anyhow!(e.to_string()))
                .context(current_fn!())?;

            (
                medical_metadata,
                hospital_personnel_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
                patient_iota_address,
            )
        };

        let cid = Utils::add_and_pin_to_ipfs(medical_metadata.enc_data)
            .await
            .context(current_fn!())?;
        let created_at = Utils::sys_time_to_iso(std::time::SystemTime::now());

        let medical_metadata = MedicalMetadata {
            capsule: medical_metadata.capsule,
            cid,
            created_at,
            enc_key_and_nonce: medical_metadata.enc_key_and_nonce,
        };

        let _ = state
            .move_call
            .update_medical_record(
                &hospital_personnel_iota_address,
                Utils::serde_serialize_to_base64(&medical_metadata).context(current_fn!())?,
                &patient_iota_address,
                proxy_iota_address,
                proxy_iota_key_pair,
            )
            .await
            .context(current_fn!())?;

        Ok(Utils::build_success_response((), StatusCode::OK))
    }
}
