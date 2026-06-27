mod activation;
mod client_error;
mod constants;
mod hospital;
mod macros;
mod move_call;
mod types;
mod utils;

use std::{env, process::Command, str::FromStr};

use iota_types::{base_types::ObjectID, Identifier};
use keyring::Entry;
use tauri::{async_runtime::Mutex, Manager};

use crate::{
    constants::{
        DECMED_ADDRESS_ID_OBJECT_ID, DECMED_ADDRESS_ID_OBJECT_VERSION, DECMED_GLOBAL_ADMIN_CAP_ID,
        DECMED_HOSPITAL_ID_METADATA_OBJECT_ID, DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION,
        DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID,
        DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION, DECMED_MODULE_ADMIN,
        DECMED_MODULE_HOSPITAL_PERSONNEL, DECMED_PACKAGE_ID, DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID,
        DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION,
    },
    move_call::MoveCall,
    types::{AppState, DecmedPackage, KeysEntry},
};

const DEFAULT_MINISTRY_ADMIN_ADDRESS: &str =
    "0x1806d6620d92b40c54332c63a42c4a6e5c024b6464fa1a0ef69ddb02539bed65";
const DEFAULT_MINISTRY_ADMIN_ALIAS: &str = "decmed-publisher-campus";
const DEFAULT_MINISTRY_PRE_SEED: &str = "sM5LRtjsf30Gsbmw7sWesgkdrAOzA9F6qMP8xrmXl1o=";

fn export_iota_private_key(alias: &str) -> Option<String> {
    let output = Command::new("iota")
        .args(["keytool", "export", alias, "--json"])
        .output()
        .ok()?;

    if !output.status.success() {
        return None;
    }

    let value: serde_json::Value = serde_json::from_slice(&output.stdout).ok()?;
    value
        .get("exportedPrivateKey")
        .or_else(|| value.get("key"))
        .and_then(|value| value.as_str())
        .map(String::from)
}

fn desired_ministry_keys_entry(existing: Option<KeysEntry>) -> KeysEntry {
    let admin_address = env::var("DECMED_MINISTRY_ADMIN_ADDRESS")
        .unwrap_or_else(|_| DEFAULT_MINISTRY_ADMIN_ADDRESS.to_string());
    let admin_secret_key = env::var("DECMED_MINISTRY_ADMIN_SECRET_KEY")
        .ok()
        .or_else(|| {
            let alias = env::var("DECMED_MINISTRY_ADMIN_ALIAS")
                .unwrap_or_else(|_| DEFAULT_MINISTRY_ADMIN_ALIAS.to_string());
            export_iota_private_key(&alias)
        })
        .or_else(|| existing.as_ref().and_then(|keys| keys.admin_secret_key.clone()));

    KeysEntry {
        admin_address: Some(admin_address),
        admin_secret_key,
        admin_pre_seed: existing
            .map(|keys| keys.admin_pre_seed)
            .unwrap_or_else(|| DEFAULT_MINISTRY_PRE_SEED.to_string()),
    }
}

fn setup(app: &mut tauri::App) -> std::result::Result<(), Box<dyn std::error::Error>> {
    let _ = dotenv::dotenv();

    let keys_entry = Entry::new("decmed_ministry_keys", "decmed_ministry")?;
    let decmed_package = DecmedPackage {
        package_id: ObjectID::from_str(DECMED_PACKAGE_ID)?,
        module_hospital_personnel: Identifier::from_str(DECMED_MODULE_HOSPITAL_PERSONNEL)?,
        module_admin: Identifier::from_str(DECMED_MODULE_ADMIN)?,

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

        global_admin_cap_id: ObjectID::from_str(DECMED_GLOBAL_ADMIN_CAP_ID)?,
    };
    let move_call = MoveCall {
        decmed_package: decmed_package.clone(),
    };

    match keys_entry.get_secret() {
        Ok(secret) => {
            let existing = serde_json::from_slice::<KeysEntry>(&secret).ok();
            let desired = desired_ministry_keys_entry(existing);
            let desired_address = desired.admin_address.clone();
            keys_entry.set_secret(&serde_json::to_vec(&desired)?)?;
            println!("Ministry admin signer synchronized to {desired_address:?}");
        }
        Err(keyring::Error::NoEntry) => {
            let desired = desired_ministry_keys_entry(None);
            let desired_address = desired.admin_address.clone();
            keys_entry.set_secret(&serde_json::to_vec(&desired)?)?;
            println!("Ministry admin signer initialized to {desired_address:?}");
        }
        Err(err) => {
            println!("{:#?}", err);
        }
    }

    app.manage(Mutex::new(AppState {
        keys_entry,
        move_call,
    }));

    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(setup)
        .invoke_handler(tauri::generate_handler![
            activation::create_activation_key,
            activation::generate_pre_seed,
            activation::update_activation_key,
            hospital::get_hospitals,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
