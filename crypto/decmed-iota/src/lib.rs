use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;
use std::str::FromStr;

use anyhow::{anyhow, Context};
use argon2::{
    password_hash::{PasswordHasher, SaltString},
    Algorithm, Argon2, Params, Version,
};
use bip39::Mnemonic;
use iota_json_rpc_types::{DevInspectResults, IotaTransactionBlockEffectsAPI};
use iota_keys::key_derive::derive_key_pair_from_path;
use iota_sdk::{IotaClient, IotaClientBuilder};
use iota_types::base_types::{IotaAddress, ObjectID, ObjectRef};
use iota_types::crypto::{
    EmptySignInfo, EncodeDecodeBase64, IotaKeyPair, Signature, SignatureScheme,
};
use iota_types::message_envelope::Envelope;
use iota_types::programmable_transaction_builder::ProgrammableTransactionBuilder;
use iota_types::transaction::{
    CallArg, ObjectArg, ProgrammableTransaction, SenderSignedData, Transaction, TransactionData,
    TransactionDataAPI,
};
use iota_types::{Identifier, TypeTag};
use jni::objects::{JClass, JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use serde_json::json;
use shared_crypto::intent::{Intent, IntentMessage};

const CLOCK_OBJECT_ID: &str = "0x6";
const CLOCK_INITIAL_SHARED_VERSION: u64 = 1;
const DEFAULT_GAS_BUDGET: u64 = 100_000_000;
const DEFAULT_GAS_RESERVE_NANOS: u64 = 2_000_000_000;
const DEFAULT_GAS_RESERVE_SECONDS: u64 = 10;
const DEFAULT_HASH_SALT: &str = "169224A2BE2B267684F93A9CE38080D359BD774741FD3AE738D09B657A1A8104";
const MODULE_PATIENT: &str = "patient";

#[derive(Debug, Deserialize)]
pub struct AndroidIotaConfig {
    pub iota_url: String,
    pub gas_station_base_url: String,
    pub package_id: String,
    pub address_id_object_id: String,
    pub address_id_object_version: u64,
    pub hospital_id_metadata_object_id: Option<String>,
    pub hospital_id_metadata_object_version: Option<u64>,
    pub hospital_personnel_id_account_object_id: Option<String>,
    pub hospital_personnel_id_account_object_version: Option<u64>,
    pub patient_id_account_object_id: String,
    pub patient_id_account_object_version: u64,
    pub hash_salt: Option<String>,
    pub gas_budget: Option<u64>,
    pub gas_reserve_nanos: Option<u64>,
    pub gas_reserve_seconds: Option<u64>,
    pub gas_station_token: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct DerivedIotaIdentity {
    pub id_hash: String,
    pub iota_address: String,
    pub iota_key_pair: String,
}

pub fn generate_mnemonic() -> anyhow::Result<String> {
    let mnemonic = Mnemonic::generate(12).context("generate BIP-39 mnemonic")?;
    Ok(mnemonic.words().collect::<Vec<&str>>().join(" "))
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ReserveGasResponse {
    pub result: Option<ReserveGasResponseData>,
    pub error: Option<serde_json::Value>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ReserveGasResponseData {
    pub sponsor_address: IotaAddress,
    pub reservation_id: u64,
    pub gas_coins: Vec<GasCoin>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct GasCoin {
    #[serde(alias = "objectId")]
    pub object_id: ObjectID,
    pub version: u64,
    pub digest: String,
}

impl GasCoin {
    fn to_object_ref(&self) -> anyhow::Result<ObjectRef> {
        Ok((
            self.object_id,
            self.version.into(),
            self.digest.parse().context("invalid gas coin digest")?,
        ))
    }
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ExecuteTxResponse {
    pub effects: Option<iota_json_rpc_types::IotaTransactionBlockEffects>,
    pub error: Option<serde_json::Value>,
}

pub fn derive_iota_identity(
    seed_words: &str,
    patient_id: &str,
) -> anyhow::Result<DerivedIotaIdentity> {
    let seed = Mnemonic::from_str(seed_words)
        .context("invalid BIP-39 mnemonic")?
        .to_seed_normalized(patient_id);
    let (iota_address, iota_key_pair) = generate_iota_keys_ed(&seed)?;
    Ok(DerivedIotaIdentity {
        id_hash: argon_hash(patient_id, DEFAULT_HASH_SALT)?,
        iota_address: iota_address.to_string(),
        iota_key_pair: iota_key_pair
            .encode()
            .map_err(|err| anyhow!(err.to_string()))
            .context("failed to encode IOTA keypair")?,
    })
}

pub async fn signup_and_publish_pghd_key(
    config: AndroidIotaConfig,
    patient_id_hash: String,
    private_metadata: String,
    pghd_public_key: String,
    sender_address: String,
    sender_key_pair: String,
) -> anyhow::Result<()> {
    let sender = IotaAddress::from_str(&sender_address).context("invalid sender address")?;
    let sender_key_pair = IotaKeyPair::decode(&sender_key_pair)
        .map_err(|err| anyhow!(err.to_string()))
        .context("invalid sender IOTA keypair")?;
    let package = AndroidMovePackage::from_config(config)?;

    package
        .execute_patient_call(
            "signup",
            vec![
                package.address_id_arg(true),
                CallArg::Pure(
                    bcs::to_bytes(&patient_id_hash).context("serialize patient_id_hash")?,
                ),
                package.patient_id_account_arg(true),
                CallArg::Pure(bcs::to_bytes(&private_metadata).context("serialize metadata")?),
            ],
            sender,
            &sender_key_pair,
        )
        .await?;

    package
        .execute_patient_call(
            "set_pghd_public_key",
            vec![
                package.address_id_arg(false),
                package.patient_id_account_arg(true),
                CallArg::Pure(bcs::to_bytes(&pghd_public_key).context("serialize public key")?),
            ],
            sender,
            &sender_key_pair,
        )
        .await?;

    Ok(())
}

pub async fn ensure_registered(
    config: AndroidIotaConfig,
    sender_address: String,
) -> anyhow::Result<()> {
    let sender = IotaAddress::from_str(&sender_address).context("invalid sender address")?;
    let package = AndroidMovePackage::from_config(config)?;
    package
        .read_patient_call(
            "is_account_registered",
            vec![package.address_id_arg(false)],
            sender,
        )
        .await
        .map(|_| ())
}

pub async fn get_pghd_public_key(
    config: AndroidIotaConfig,
    patient_address: String,
    sender_address: String,
) -> anyhow::Result<String> {
    let patient_address =
        IotaAddress::from_str(&patient_address).context("invalid patient address")?;
    let sender = IotaAddress::from_str(&sender_address).context("invalid sender address")?;
    let package = AndroidMovePackage::from_config(config)?;
    let response = package
        .read_patient_call(
            "get_patient_pghd_public_key",
            vec![
                package.address_id_arg(false),
                CallArg::Pure(
                    bcs::to_bytes(&patient_address).context("serialize patient address")?,
                ),
                package.patient_id_account_arg(false),
            ],
            sender,
        )
        .await?;
    parse_move_read_only_result(response, 0)
}

pub async fn create_pghd_access(
    config: AndroidIotaConfig,
    date: String,
    hospital_personnel_address: String,
    metadata: String,
    sender_address: String,
    sender_key_pair: String,
) -> anyhow::Result<()> {
    let hospital_personnel_address = IotaAddress::from_str(&hospital_personnel_address)
        .context("invalid hospital personnel address")?;
    let sender = IotaAddress::from_str(&sender_address).context("invalid sender address")?;
    let sender_key_pair = IotaKeyPair::decode(&sender_key_pair)
        .map_err(|err| anyhow!(err.to_string()))
        .context("invalid sender IOTA keypair")?;
    let package = AndroidMovePackage::from_config(config)?;

    package
        .execute_patient_call(
            "create_access",
            vec![
                package.address_id_arg(false),
                package.clock_arg()?,
                CallArg::Pure(bcs::to_bytes(&date).context("serialize access date")?),
                package.hospital_id_metadata_arg(false)?,
                CallArg::Pure(
                    bcs::to_bytes(&hospital_personnel_address)
                        .context("serialize hospital personnel address")?,
                ),
                package.hospital_personnel_id_account_arg(true)?,
                CallArg::Pure(bcs::to_bytes(&vec![metadata]).context("serialize PGHD metadata")?),
                package.patient_id_account_arg(true),
            ],
            sender,
            &sender_key_pair,
        )
        .await
}

pub fn sign_personal_message(sender_key_pair: String, message: String) -> anyhow::Result<String> {
    let sender_key_pair = IotaKeyPair::decode(&sender_key_pair)
        .map_err(|err| anyhow!(err.to_string()))
        .context("invalid sender IOTA keypair")?;
    let intent_message = IntentMessage::new(Intent::personal_message(), message);
    Ok(Signature::new_secure(&intent_message, &sender_key_pair).encode_base64())
}

struct AndroidMovePackage {
    config: AndroidIotaConfig,
    package_id: ObjectID,
    module_patient: Identifier,
    address_id_object_id: ObjectID,
    hospital_id_metadata_object_id: Option<ObjectID>,
    hospital_personnel_id_account_object_id: Option<ObjectID>,
    patient_id_account_object_id: ObjectID,
}

impl AndroidMovePackage {
    fn from_config(config: AndroidIotaConfig) -> anyhow::Result<Self> {
        Ok(Self {
            package_id: ObjectID::from_str(&config.package_id).context("invalid package id")?,
            module_patient: Identifier::from_str(MODULE_PATIENT)
                .context("invalid patient module")?,
            address_id_object_id: ObjectID::from_str(&config.address_id_object_id)
                .context("invalid AddressId object id")?,
            hospital_id_metadata_object_id: config
                .hospital_id_metadata_object_id
                .as_deref()
                .filter(|it| !it.is_empty())
                .map(ObjectID::from_str)
                .transpose()
                .context("invalid HospitalIdMetadata object id")?,
            hospital_personnel_id_account_object_id: config
                .hospital_personnel_id_account_object_id
                .as_deref()
                .filter(|it| !it.is_empty())
                .map(ObjectID::from_str)
                .transpose()
                .context("invalid HospitalPersonnelIdAccount object id")?,
            patient_id_account_object_id: ObjectID::from_str(&config.patient_id_account_object_id)
                .context("invalid PatientIdAccount object id")?,
            config,
        })
    }

    fn address_id_arg(&self, mutable: bool) -> CallArg {
        shared_object_arg(
            self.address_id_object_id,
            self.config.address_id_object_version,
            mutable,
        )
    }

    fn patient_id_account_arg(&self, mutable: bool) -> CallArg {
        shared_object_arg(
            self.patient_id_account_object_id,
            self.config.patient_id_account_object_version,
            mutable,
        )
    }

    fn hospital_id_metadata_arg(&self, mutable: bool) -> anyhow::Result<CallArg> {
        let id = self
            .hospital_id_metadata_object_id
            .ok_or_else(|| anyhow!("DECMED_HOSPITAL_ID_METADATA_OBJECT_ID must be configured"))?;
        let version = self
            .config
            .hospital_id_metadata_object_version
            .filter(|version| *version > 0)
            .ok_or_else(|| {
                anyhow!("DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION must be configured")
            })?;
        Ok(shared_object_arg(id, version, mutable))
    }

    fn hospital_personnel_id_account_arg(&self, mutable: bool) -> anyhow::Result<CallArg> {
        let id = self
            .hospital_personnel_id_account_object_id
            .ok_or_else(|| {
                anyhow!("DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID must be configured")
            })?;
        let version = self
            .config
            .hospital_personnel_id_account_object_version
            .filter(|version| *version > 0)
            .ok_or_else(|| {
                anyhow!("DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION must be configured")
            })?;
        Ok(shared_object_arg(id, version, mutable))
    }

    #[allow(dead_code)]
    fn clock_arg(&self) -> anyhow::Result<CallArg> {
        Ok(shared_object_arg(
            ObjectID::from_str(CLOCK_OBJECT_ID)?,
            CLOCK_INITIAL_SHARED_VERSION,
            false,
        ))
    }

    async fn execute_patient_call(
        &self,
        function: &str,
        args: Vec<CallArg>,
        sender: IotaAddress,
        sender_key_pair: &IotaKeyPair,
    ) -> anyhow::Result<()> {
        let iota_client = self.iota_client().await?;
        let pt = construct_pt(
            function,
            self.package_id,
            self.module_patient.clone(),
            vec![],
            args,
        )?;
        let (sponsor_account, reservation_id, gas_coins) = self.reserve_gas().await?;
        let ref_gas_price = iota_client
            .governance_api()
            .get_reference_gas_price()
            .await
            .context("get reference gas price")?;
        let tx_data = construct_sponsored_tx_data(
            sender,
            gas_coins,
            pt,
            self.config.gas_budget.unwrap_or(DEFAULT_GAS_BUDGET),
            ref_gas_price,
            sponsor_account,
        );
        let tx = Transaction::from_data_and_signer(tx_data, vec![sender_key_pair]);
        let response = self.execute_tx(tx, reservation_id).await?;
        handle_error_execute_tx(response)
    }

    async fn read_patient_call(
        &self,
        function: &str,
        args: Vec<CallArg>,
        sender: IotaAddress,
    ) -> anyhow::Result<DevInspectResults> {
        let iota_client = self.iota_client().await?;
        let pt = construct_pt(
            function,
            self.package_id,
            self.module_patient.clone(),
            vec![],
            args,
        )?;
        let response = iota_client
            .read_api()
            .dev_inspect_transaction_block(
                sender,
                iota_types::transaction::TransactionKind::ProgrammableTransaction(pt),
                None,
                None,
                None,
            )
            .await
            .context("dev inspect Move call")?;
        handle_error_move_call_read_only(response.clone())?;
        Ok(response)
    }

    async fn iota_client(&self) -> anyhow::Result<IotaClient> {
        IotaClientBuilder::default()
            .build(&self.config.iota_url)
            .await
            .context("build IOTA client")
    }

    async fn reserve_gas(&self) -> anyhow::Result<(IotaAddress, u64, Vec<ObjectRef>)> {
        let client = reqwest::Client::new();
        let url = format!(
            "{}/reserve_gas",
            self.config.gas_station_base_url.trim_end_matches('/')
        );
        let mut request = client.post(url);
        if let Some(token) = self
            .config
            .gas_station_token
            .as_deref()
            .filter(|it| !it.is_empty())
        {
            request = request.bearer_auth(token);
        } else {
            request = request.bearer_auth("token");
        }
        let response = request
            .json(&json!({
                "gas_budget": self.config.gas_reserve_nanos.unwrap_or(DEFAULT_GAS_RESERVE_NANOS),
                "reserve_duration_secs": self.config.gas_reserve_seconds.unwrap_or(DEFAULT_GAS_RESERVE_SECONDS)
            }))
            .send()
            .await
            .context("reserve gas")?;
        let body = decode_json_response::<ReserveGasResponse>(response, "reserve gas").await?;
        if let Some(error) = body.error {
            return Err(anyhow!(format_json_error(error)).context("reserve gas failed"));
        }
        let result = body
            .result
            .ok_or_else(|| anyhow!("reserve gas result not found"))?;
        let gas_coins = result
            .gas_coins
            .iter()
            .map(GasCoin::to_object_ref)
            .collect::<anyhow::Result<Vec<_>>>()?;
        Ok((result.sponsor_address, result.reservation_id, gas_coins))
    }

    async fn execute_tx(
        &self,
        tx: Envelope<SenderSignedData, EmptySignInfo>,
        reservation_id: u64,
    ) -> anyhow::Result<ExecuteTxResponse> {
        let (tx_base_64, signature_base_64) = tx.to_tx_bytes_and_signatures();
        let client = reqwest::Client::new();
        let url = format!(
            "{}/execute_tx",
            self.config.gas_station_base_url.trim_end_matches('/')
        );
        let mut request = client.post(url);
        if let Some(token) = self
            .config
            .gas_station_token
            .as_deref()
            .filter(|it| !it.is_empty())
        {
            request = request.bearer_auth(token);
        } else {
            request = request.bearer_auth("token");
        }
        let response = request
            .json(&json!({
                "reservation_id": reservation_id,
                "tx_bytes": tx_base_64.encoded(),
                "user_sig": signature_base_64[0].encoded()
            }))
            .send()
            .await
            .context("execute sponsored transaction")?;
        decode_json_response::<ExecuteTxResponse>(response, "execute sponsored transaction").await
    }
}

fn generate_iota_keys_ed(seed: &[u8]) -> anyhow::Result<(IotaAddress, IotaKeyPair)> {
    derive_key_pair_from_path(
        seed,
        Some(bip32::DerivationPath::from_str("m/44'/4218'/0'/0'/0'").unwrap()),
        &SignatureScheme::ED25519,
    )
    .map_err(|err| anyhow!(err.to_string()))
    .context("derive IOTA Ed25519 keypair")
}

fn construct_pt(
    function_name: &str,
    package: ObjectID,
    module: Identifier,
    type_arguments: Vec<TypeTag>,
    call_args: Vec<CallArg>,
) -> anyhow::Result<ProgrammableTransaction> {
    let mut builder = ProgrammableTransactionBuilder::new();
    let function = Identifier::from_str(function_name).context("invalid Move function")?;
    builder
        .move_call(package, module, function, type_arguments, call_args)
        .context("build Move call")?;
    Ok(builder.finish())
}

fn construct_sponsored_tx_data(
    sender: IotaAddress,
    gas_payment: Vec<ObjectRef>,
    pt: ProgrammableTransaction,
    gas_budget: u64,
    gas_price: u64,
    sponsor_address: IotaAddress,
) -> TransactionData {
    let mut tx_data =
        TransactionData::new_programmable(sender, gas_payment.clone(), pt, gas_budget, gas_price);
    tx_data.gas_data_mut().payment = gas_payment;
    tx_data.gas_data_mut().owner = sponsor_address;
    tx_data
}

fn shared_object_arg(id: ObjectID, version: u64, mutable: bool) -> CallArg {
    CallArg::Object(ObjectArg::SharedObject {
        id,
        initial_shared_version: version.into(),
        mutable,
    })
}

fn parse_move_read_only_result<T: DeserializeOwned>(
    val: DevInspectResults,
    index: usize,
) -> anyhow::Result<T> {
    let res = val.results.context("Move result missing")?[0].return_values[index]
        .0
        .to_vec();
    bcs::from_bytes::<T>(&res).context("decode Move return value")
}

fn handle_error_move_call_read_only(response: DevInspectResults) -> anyhow::Result<()> {
    if let Some(error) = response.error {
        return Err(anyhow!(error).context("Move read-only call failed"));
    }
    if response.effects.status().is_err() {
        return Err(
            anyhow!(response.effects.status().to_string()).context("Move read-only effects failed")
        );
    }
    Ok(())
}

fn handle_error_execute_tx(response: ExecuteTxResponse) -> anyhow::Result<()> {
    if let Some(error) = response.error {
        return Err(anyhow!(format_json_error(error)).context("IOTA transaction failed"));
    }
    if let Some(effects) = response.effects {
        if effects.status().is_err() {
            return Err(
                anyhow!(effects.status().to_string()).context("IOTA transaction effects failed")
            );
        }
    }
    Ok(())
}

fn format_json_error(error: serde_json::Value) -> String {
    match error {
        serde_json::Value::String(value) => value,
        value => value.to_string(),
    }
}

async fn decode_json_response<T: DeserializeOwned>(
    response: reqwest::Response,
    label: &str,
) -> anyhow::Result<T> {
    let status = response.status();
    let url = response.url().to_string();
    let body = response
        .text()
        .await
        .with_context(|| format!("{label}: read response body from {url}"))?;
    native_log_chunks(&format!("{label}: HTTP {status} from {url}; response body: {body}"));

    if !status.is_success() {
        return Err(anyhow!(
            "{label}: HTTP {status} from {url}; response body: {body}"
        ));
    }

    serde_json::from_str::<T>(&body).with_context(|| {
        format!("{label}: decode JSON response from {url}; response body: {body}")
    })
}

fn native_log_chunks(message: &str) {
    const CHUNK_SIZE: usize = 3_500;
    if message.is_empty() {
        eprintln!("DecmedIotaNative: ");
        return;
    }
    let total = message.len().div_ceil(CHUNK_SIZE);
    for (index, chunk) in message.as_bytes().chunks(CHUNK_SIZE).enumerate() {
        eprintln!(
            "DecmedIotaNative [{}/{}]: {}",
            index + 1,
            total,
            String::from_utf8_lossy(chunk)
        );
    }
}

fn argon_hash(password: &str, salt: &str) -> anyhow::Result<String> {
    let salt = SaltString::from_b64(salt).map_err(|err| anyhow!(err.to_string()))?;
    let argon2 = Argon2::new_with_secret(
        DEFAULT_HASH_SALT.as_bytes(),
        Algorithm::Argon2id,
        Version::V0x13,
        Params::DEFAULT,
    )
    .map_err(|err| anyhow!(err.to_string()))?;
    let hash = argon2
        .hash_password(password.as_bytes(), &salt)
        .map_err(|err| anyhow!(err.to_string()))?
        .to_string();
    Ok(hex::encode(hash))
}

fn parse_config(config_json: &str) -> anyhow::Result<AndroidIotaConfig> {
    serde_json::from_str(config_json).context("invalid Android IOTA config")
}

fn block_on<T, F>(future: F) -> anyhow::Result<T>
where
    F: std::future::Future<Output = anyhow::Result<T>>,
{
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
        .context("build Tokio runtime")?
        .block_on(future)
}

fn jni_result<T, F>(mut env: JNIEnv, f: F) -> jstring
where
    T: Serialize,
    F: FnOnce(&mut JNIEnv) -> anyhow::Result<T>,
{
    let body = match catch_unwind(AssertUnwindSafe(|| f(&mut env))) {
        Ok(Ok(value)) => serde_json::json!({ "ok": true, "data": value }),
        Ok(Err(err)) => serde_json::json!({ "ok": false, "error": format_anyhow_error(&err) }),
        Err(err) => {
            let message = err
                .downcast_ref::<&str>()
                .map(|it| (*it).to_string())
                .or_else(|| err.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "Native IOTA call panicked.".to_string());
            serde_json::json!({ "ok": false, "error": message })
        }
    };
    match env.new_string(body.to_string()) {
        Ok(value) => value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn format_anyhow_error(err: &anyhow::Error) -> String {
    err.chain()
        .enumerate()
        .map(|(index, cause)| {
            if index == 0 {
                cause.to_string()
            } else {
                format!("caused by {index}: {cause}")
            }
        })
        .collect::<Vec<_>>()
        .join("\n")
}

fn jstring_to_string(env: &mut JNIEnv, value: JString) -> anyhow::Result<String> {
    Ok(env.get_string(&value)?.into())
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_initAndroidTlsJson(
    env: JNIEnv,
    _class: JClass,
    context: JObject,
) -> jstring {
    jni_result(env, |env| {
        #[cfg(target_os = "android")]
        {
            rustls_platform_verifier::android::init_with_env(env, context)
                .context("initialize Android TLS verifier")?;
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = env;
            let _ = context;
        }
        Ok(())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_derivePatientIdentityJson(
    env: JNIEnv,
    _class: JClass,
    seed_words: JString,
    patient_id: JString,
) -> jstring {
    jni_result(env, |env| {
        let seed_words = jstring_to_string(env, seed_words)?;
        let patient_id = jstring_to_string(env, patient_id)?;
        derive_iota_identity(&seed_words, &patient_id)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_generateMnemonicJson(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    jni_result(env, |_env| generate_mnemonic())
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_signupAndPublishPghdKeyJson(
    env: JNIEnv,
    _class: JClass,
    config: JString,
    patient_id_hash: JString,
    private_metadata: JString,
    pghd_public_key: JString,
    sender_address: JString,
    sender_key_pair: JString,
) -> jstring {
    jni_result(env, |env| {
        let config = parse_config(&jstring_to_string(env, config)?)?;
        let patient_id_hash = jstring_to_string(env, patient_id_hash)?;
        let private_metadata = jstring_to_string(env, private_metadata)?;
        let pghd_public_key = jstring_to_string(env, pghd_public_key)?;
        let sender_address = jstring_to_string(env, sender_address)?;
        let sender_key_pair = jstring_to_string(env, sender_key_pair)?;
        block_on(signup_and_publish_pghd_key(
            config,
            patient_id_hash,
            private_metadata,
            pghd_public_key,
            sender_address,
            sender_key_pair,
        ))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_ensureRegisteredJson(
    env: JNIEnv,
    _class: JClass,
    config: JString,
    sender_address: JString,
) -> jstring {
    jni_result(env, |env| {
        let config = parse_config(&jstring_to_string(env, config)?)?;
        let sender_address = jstring_to_string(env, sender_address)?;
        block_on(ensure_registered(config, sender_address))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_getPghdPublicKeyJson(
    env: JNIEnv,
    _class: JClass,
    config: JString,
    patient_address: JString,
    sender_address: JString,
) -> jstring {
    jni_result(env, |env| {
        let config = parse_config(&jstring_to_string(env, config)?)?;
        let patient_address = jstring_to_string(env, patient_address)?;
        let sender_address = jstring_to_string(env, sender_address)?;
        block_on(get_pghd_public_key(config, patient_address, sender_address))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_createPghdAccessJson(
    env: JNIEnv,
    _class: JClass,
    config: JString,
    date: JString,
    hospital_personnel_address: JString,
    metadata: JString,
    sender_address: JString,
    sender_key_pair: JString,
) -> jstring {
    jni_result(env, |env| {
        let config = parse_config(&jstring_to_string(env, config)?)?;
        let date = jstring_to_string(env, date)?;
        let hospital_personnel_address = jstring_to_string(env, hospital_personnel_address)?;
        let metadata = jstring_to_string(env, metadata)?;
        let sender_address = jstring_to_string(env, sender_address)?;
        let sender_key_pair = jstring_to_string(env, sender_key_pair)?;
        block_on(create_pghd_access(
            config,
            date,
            hospital_personnel_address,
            metadata,
            sender_address,
            sender_key_pair,
        ))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_iota_DecmedIotaNative_signPersonalMessageJson(
    env: JNIEnv,
    _class: JClass,
    sender_key_pair: JString,
    message: JString,
) -> jstring {
    jni_result(env, |env| {
        let sender_key_pair = jstring_to_string(env, sender_key_pair)?;
        let message = jstring_to_string(env, message)?;
        sign_personal_message(sender_key_pair, message)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn derives_stable_iota_identity() {
        let seed_words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        let one = derive_iota_identity(seed_words, "1234567890123456").unwrap();
        let two = derive_iota_identity(seed_words, "1234567890123456").unwrap();
        assert_eq!(one.iota_address, two.iota_address);
        assert_eq!(one.iota_key_pair, two.iota_key_pair);
        assert!(one.iota_address.starts_with("0x"));
        assert!(!one.id_hash.is_empty());
    }
}
