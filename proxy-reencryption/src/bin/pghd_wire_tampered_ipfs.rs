use std::{env, process, str::FromStr};

use anyhow::{anyhow, Context};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use iota_types::{
    base_types::{IotaAddress, ObjectID},
    crypto::IotaKeyPair,
    Identifier,
};
use proxy_reencryption::{
    constants::{
        DECMED_ADDRESS_ID_OBJECT_ID, DECMED_ADDRESS_ID_OBJECT_VERSION,
        DECMED_GLOBAL_ADMIN_CAP_ID, DECMED_HOSPITAL_ID_METADATA_OBJECT_ID,
        DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION,
        DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID,
        DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION, DECMED_MODULE_ADMIN,
        DECMED_MODULE_PROXY, DECMED_PACKAGE_ID, DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID,
        DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION, DECMED_PROXY_CAP_OBJECT_ID,
        DECMED_PROXY_CAP_OBJECT_VERSION,
    },
    move_call::MoveCall,
    types::{DecmedPackage, HandlerSubmitPghdPayload, PghdMetadata},
    utils::Utils,
};
use serde_json::json;

fn arg_value(args: &[String], name: &str) -> anyhow::Result<String> {
    args.windows(2)
        .find(|window| window[0] == name)
        .map(|window| window[1].clone())
        .ok_or_else(|| anyhow!("missing required argument {name}"))
}

fn optional_arg_value(args: &[String], name: &str) -> Option<String> {
    args.windows(2)
        .find(|window| window[0] == name)
        .map(|window| window[1].clone())
}

fn print_json(value: serde_json::Value) {
    println!("{}", serde_json::to_string_pretty(&value).unwrap());
}

fn decmed_move_call() -> anyhow::Result<MoveCall> {
    Ok(MoveCall {
        decmed_package: DecmedPackage {
            package_id: ObjectID::from_str(DECMED_PACKAGE_ID)?,
            module_admin: Identifier::from_str(DECMED_MODULE_ADMIN)?,
            module_proxy: Identifier::from_str(DECMED_MODULE_PROXY)?,
            address_id_object_id: ObjectID::from_str(DECMED_ADDRESS_ID_OBJECT_ID)?,
            address_id_object_version: DECMED_ADDRESS_ID_OBJECT_VERSION,
            hospital_id_metadata_object_id: ObjectID::from_str(DECMED_HOSPITAL_ID_METADATA_OBJECT_ID)?,
            hospital_id_metadata_object_version: DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION,
            hospital_personnel_id_account_object_id: ObjectID::from_str(
                DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID,
            )?,
            hospital_personnel_id_account_object_version:
                DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION,
            patient_id_account_object_id: ObjectID::from_str(DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID)?,
            patient_id_account_object_version: DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION,
            proxy_cap_object_id: ObjectID::from_str(&Utils::env_string(
                "DECMED_PROXY_CAP_OBJECT_ID",
                DECMED_PROXY_CAP_OBJECT_ID,
            ))?,
            proxy_cap_object_version: Utils::env_string(
                "DECMED_PROXY_CAP_OBJECT_VERSION",
                &DECMED_PROXY_CAP_OBJECT_VERSION.to_string(),
            )
            .parse()?,
            global_admin_cap_id: ObjectID::from_str(DECMED_GLOBAL_ADMIN_CAP_ID)?,
        },
    })
}

#[tokio::main]
async fn main() {
    if let Err(err) = run().await {
        print_json(json!({
            "ok": false,
            "error": err.chain().map(|cause| cause.to_string()).collect::<Vec<_>>().join("\n"),
        }));
        process::exit(1);
    }
}

async fn run() -> anyhow::Result<()> {
    let args = env::args().collect::<Vec<_>>();
    if let Some(env_file) = optional_arg_value(&args, "--env-file") {
        let _ = dotenvy::from_path(env_file);
    } else {
        let _ = dotenvy::dotenv();
        let _ = dotenvy::from_path("proxy-reencryption/.env");
    }
    let payload_path = arg_value(&args, "--payload")?;
    let tampered_cid = arg_value(&args, "--tampered-cid")?;

    let payload_text = std::fs::read_to_string(&payload_path)
        .with_context(|| format!("read payload JSON from {payload_path}"))?;
    let payload: HandlerSubmitPghdPayload =
        serde_json::from_str(&payload_text).context("decode submit payload JSON")?;

    let proxy_iota_address_raw = env::var("PROXY_IOTA_ADDRESS").context(
        "missing PROXY_IOTA_ADDRESS. Set it to the proxy IOTA address, for example \
         export PROXY_IOTA_ADDRESS=0x..., or keep it in proxy-reencryption/.env",
    )?;
    let proxy_iota_address = IotaAddress::from_str(&proxy_iota_address_raw).with_context(|| {
        format!(
            "invalid PROXY_IOTA_ADDRESS: {proxy_iota_address_raw}. This value must be an IOTA address starting with 0x, not the PRE URL"
        )
    })?;
    let proxy_iota_key_pair = IotaKeyPair::decode(
        &env::var("PROXY_IOTA_KEY_PAIR").context("missing PROXY_IOTA_KEY_PAIR")?,
    )
    .map_err(|err| anyhow!(err.to_string()))
    .context("invalid PROXY_IOTA_KEY_PAIR")?;
    let patient_iota_address =
        IotaAddress::from_str(&payload.patient_iota_address).context("invalid patient_iota_address")?;

    let metadata = PghdMetadata {
        batch_id: format!("{}-tampered-ipfs", payload.batch_id),
        capsule: payload.capsule,
        cid: tampered_cid.clone(),
        created_at: Utils::sys_time_to_iso(std::time::SystemTime::now()),
        enc_aes_key_nonce: payload.enc_aes_key_nonce,
        h_cipher: payload.h_cipher.clone(),
        patient_iota_address: payload.patient_iota_address,
        signature: payload.signature,
        verified_by_proxy: true,
    };
    let metadata_base64 = STANDARD.encode(serde_json::to_vec(&metadata)?);

    decmed_move_call()?
        .submit_pghd(
            tampered_cid.clone(),
            payload.h_cipher,
            metadata_base64,
            &patient_iota_address,
            proxy_iota_address,
            proxy_iota_key_pair,
        )
        .await
        .map_err(|err| anyhow!(format!("{err:?}")))?;

    print_json(json!({
        "ok": true,
        "data": {
            "cid": tampered_cid,
            "batch_id": metadata.batch_id,
            "patient_iota_address": patient_iota_address.to_string(),
            "expected_access_error": "OUTER_HASH_MISMATCH"
        }
    }));
    Ok(())
}
