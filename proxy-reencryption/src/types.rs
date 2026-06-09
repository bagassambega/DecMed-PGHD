use std::{
    collections::HashMap,
    fmt::Debug,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use anyhow::{anyhow, Context};
use iota_json_rpc_types::{IotaObjectRef, IotaTransactionBlockEffects};
use iota_types::{
    base_types::{IotaAddress, ObjectID},
    Identifier,
};
use r2d2::Pool;
use redis::{Client, Commands, SetExpiry, SetOptions};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::move_call::MoveCall;

#[derive(Clone)]
pub enum CacheStore {
    Redis(Pool<Client>),
    Memory(Arc<Mutex<HashMap<String, CacheEntry>>>),
}

#[derive(Clone)]
pub struct CacheEntry {
    value: String,
    expires_at: Instant,
}

impl CacheStore {
    pub fn memory() -> Self {
        Self::Memory(Arc::new(Mutex::new(HashMap::new())))
    }

    pub fn redis(pool: Pool<Client>) -> Self {
        Self::Redis(pool)
    }

    pub fn get(&self, key: &str) -> anyhow::Result<String> {
        match self {
            Self::Redis(pool) => {
                let mut conn = pool
                    .get()
                    .context("failed to get Redis connection from pool")?;
                conn.get(key).context("cache key not found")
            }
            Self::Memory(cache) => {
                let mut cache = cache.lock().map_err(|_| anyhow!("cache lock poisoned"))?;
                match cache.get(key) {
                    Some(entry) if entry.expires_at > Instant::now() => Ok(entry.value.clone()),
                    Some(_) => {
                        cache.remove(key);
                        Err(anyhow!("cache key expired"))
                    }
                    None => Err(anyhow!("cache key not found")),
                }
            }
        }
    }

    pub fn set_ex(&self, key: String, value: String, ttl_secs: u64) -> anyhow::Result<()> {
        match self {
            Self::Redis(pool) => {
                let mut conn = pool
                    .get()
                    .context("failed to get Redis connection from pool")?;
                conn.set_options(
                    key,
                    value,
                    SetOptions::default().with_expiration(SetExpiry::EX(ttl_secs)),
                )
                .context("failed to write cache key")
            }
            Self::Memory(cache) => {
                let mut cache = cache.lock().map_err(|_| anyhow!("cache lock poisoned"))?;
                cache.insert(
                    key,
                    CacheEntry {
                        value,
                        expires_at: Instant::now() + Duration::from_secs(ttl_secs),
                    },
                );
                Ok(())
            }
        }
    }

    pub fn del(&self, key: &str) -> anyhow::Result<()> {
        match self {
            Self::Redis(pool) => {
                let mut conn = pool
                    .get()
                    .context("failed to get Redis connection from pool")?;
                conn.del(key).context("failed to delete cache key")
            }
            Self::Memory(cache) => {
                let mut cache = cache.lock().map_err(|_| anyhow!("cache lock poisoned"))?;
                cache.remove(key);
                Ok(())
            }
        }
    }
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub enum AuthRole {
    AdministrativePersonnel,
    MedicalPersonnel,
    Patient,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub enum MoveHospitalPersonnelRole {
    Admin,
    AdministrativePersonnel,
    MedicalPersonnel,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub enum ReencryptionPurposeType {
    Read,
    Update,
    ReadPghd,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AccessKeys {
    pub enc_data_pre_secret_key_seed: String,
    pub k_frag: String,
    pub data_pre_public_key: String,
    pub data_pre_secret_key_seed_capsule: String,
    pub patient_pre_public_key: String,
    pub signer_pre_public_key: String,
}

pub struct AppState {
    pub global_admin_iota_address: String,
    pub global_admin_iota_key_pair: String,
    pub jwt_ecdsa_key_pair: String,
    pub jwt_ecdsa_pub_key: String,
    pub move_call: MoveCall,
    pub proxy_iota_address: String,
    pub proxy_iota_key_pair: String,
    pub cache_store: CacheStore,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct HandlerRegisterPghdPatientPayload {
    pub patient_id_hash: Option<String>,
    pub patient_iota_address: String,
    pub pghd_public_key: String,
    pub pre_public_key: Option<String>,
    pub pghd_pre_public_key: Option<String>,
    pub medical_pre_public_key: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct HandlerSubmitPghdPayload {
    pub batch_id: String,
    pub patient_id_hash: Option<String>,
    pub patient_iota_address: String,
    pub enc_pghd: String,
    pub h_cipher: String,
    pub enc_aes_key_nonce: String,
    pub capsule: String,
    pub pghd_outer_signature: String,
}

#[derive(Debug, Deserialize)]
pub struct HandlerGetPghdQueryParams {
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct HandlerInvalidatePghdPayload {
    pub cid: String,
    pub failure_reason: String,
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct PghdMetadata {
    pub batch_id: String,
    pub capsule: String,
    pub cid: String,
    pub created_at: String,
    pub enc_aes_key_nonce: String,
    pub h_cipher: String,
    pub patient_iota_address: String,
    pub pghd_outer_signature: String,
    pub verified_by_proxy: bool,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct AuthenticateHandlerPayload {
    pub signature: String,
    pub iota_address: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct AuthenticateHandlerResponse {
    pub access_token: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ClientMedicalMetadata {
    pub capsule: String,
    pub enc_data: String,
    pub enc_key_and_nonce: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CurrentUser {
    pub iota_address: String,
    pub purpose: ReencryptionPurposeType,
    pub role: AuthRole,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct DecmedPackage {
    pub package_id: ObjectID,
    pub module_admin: Identifier,
    pub module_proxy: Identifier,

    pub address_id_object_id: ObjectID,
    pub address_id_object_version: u64,
    pub hospital_id_metadata_object_id: ObjectID,
    pub hospital_id_metadata_object_version: u64,
    pub hospital_personnel_id_account_object_id: ObjectID,
    pub hospital_personnel_id_account_object_version: u64,
    pub patient_id_account_object_id: ObjectID,
    pub patient_id_account_object_version: u64,

    pub global_admin_cap_id: ObjectID,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ErrorResponse {
    pub error: String,
    pub status_code: u16,
}

#[derive(Debug, Deserialize, JsonSchema, Serialize)]
pub struct ExecuteTxResponse {
    pub effects: Option<IotaTransactionBlockEffects>,
    pub error: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct GenerateAndRegisterProxyAddress {
    pub iota_address: String,
    pub iota_keypair: String,
    pub seed_words: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct GenerateJwtHandlerResponse {
    pub public_key: String,
    pub secret_key: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct GetNonceHandlerPayload {
    pub iota_address: String, // hex string
}

#[derive(Debug, Deserialize, Serialize)]
pub struct GenerateSignatureHandlerPayload {
    pub iota_keypair: String,
    pub nonce: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct JwtClaims {
    pub role: AuthRole,
    pub purpose: ReencryptionPurposeType,
}

#[derive(Debug, Deserialize, JsonSchema, Serialize)]
pub struct ReserveGasResponse {
    pub error: Option<String>,
    pub result: Option<ReserveGasResult>,
}

#[derive(Debug, Deserialize, JsonSchema, Serialize)]
pub struct ReserveGasResult {
    pub gas_coins: Vec<IotaObjectRef>,
    pub reservation_id: u64,
    pub sponsor_address: IotaAddress,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct HandlerCreateMedicalRecordPayload {
    pub medical_metadata: String,
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize)]
pub struct HandlerGetAdministrativeDataQueryParams {
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize)]
pub struct HandlerGetMedicalRecordQueryParams {
    #[serde(deserialize_with = "crate::utils::Utils::empty_string_as_none")]
    pub index: Option<u64>,
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize)]
pub struct HandlerGetMedicalRecordUpdateQueryParams {
    #[serde(deserialize_with = "crate::utils::Utils::empty_string_as_none")]
    pub index: Option<u64>,
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct HandlerStoreKeysPayload {
    pub enc_data_pre_secret_key_seed: String,
    pub hospital_personnel_iota_address: String,
    pub k_frag: String,
    pub data_pre_public_key: String,
    pub data_pre_secret_key_seed_capsule: String,
    pub patient_iota_address: String,
    pub patient_pre_public_key: String,
    pub purpose: Option<ReencryptionPurposeType>,
    pub signature: String,
    pub signer_pre_public_key: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct HandlerUpdateMedicalRecordPayload {
    pub medical_metadata: String,
    pub patient_iota_address: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct MedicalMetadata {
    pub capsule: String,
    pub cid: String,
    pub created_at: String,
    pub enc_key_and_nonce: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct MovePatientAdministrativeMetadata {
    pub private_metadata: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct MovePatientMedicalMetadata {
    pub index: u64,
    pub metadata: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct MovePatientPghdMetadata {
    pub index: u64,
    pub cid: String,
    pub h_cipher: String,
    pub metadata: String,
    pub status: u8,
    pub failure_reason: String,
    pub timestamp: u64,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct PatientPrivateAdministrativeMetadata {
    pub capsule: String,
    pub enc_data: String,
    pub enc_key_nonce: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct SuccessResponse<T>
where
    T: Debug,
{
    pub data: T,
    pub status_code: u16,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct UtilIpfsAddResponse {
    #[serde(default)]
    pub allocations: Vec<String>,
    #[serde(default, alias = "Hash")]
    pub cid: String,
    #[serde(default, alias = "Name")]
    pub name: String,
    #[serde(default, alias = "Size", deserialize_with = "deserialize_u64_from_any")]
    pub size: u64,
}

fn deserialize_u64_from_any<'de, D>(deserializer: D) -> Result<u64, D::Error>
where
    D: serde::Deserializer<'de>,
{
    let value = serde_json::Value::deserialize(deserializer)?;
    match value {
        serde_json::Value::Number(number) => number
            .as_u64()
            .ok_or_else(|| serde::de::Error::custom("expected unsigned integer")),
        serde_json::Value::String(value) => value
            .parse::<u64>()
            .map_err(|err| serde::de::Error::custom(format!("expected numeric string: {err}"))),
        serde_json::Value::Null => Ok(0),
        other => Err(serde::de::Error::custom(format!("expected integer, got {other}"))),
    }
}
