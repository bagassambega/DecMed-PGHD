use std::{env, process};

use anyhow::{anyhow, Context};
use decmed_iota::{
    derive_iota_identity, generate_mnemonic, signup_and_publish_pghd_key, AndroidIotaConfig,
};
use serde_json::json;

fn print_json(value: serde_json::Value) {
    println!("{}", serde_json::to_string_pretty(&value).unwrap());
}

fn arg(args: &[String], index: usize, name: &str) -> anyhow::Result<String> {
    args.get(index)
        .cloned()
        .ok_or_else(|| anyhow!("missing argument {name}"))
}

fn parse_config(config_json: &str) -> anyhow::Result<AndroidIotaConfig> {
    serde_json::from_str(config_json).context("invalid Android IOTA config JSON")
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

fn run() -> anyhow::Result<()> {
    let args = env::args().collect::<Vec<_>>();
    let command = arg(&args, 1, "command")?;

    match command.as_str() {
        "generate-mnemonic" => {
            print_json(json!({ "ok": true, "data": generate_mnemonic()? }));
        }
        "derive" => {
            let seed_words = arg(&args, 2, "seed_words")?;
            let patient_id = arg(&args, 3, "patient_id")?;
            print_json(json!({ "ok": true, "data": derive_iota_identity(&seed_words, &patient_id)? }));
        }
        "signup-and-publish-pghd-key" => {
            let config = parse_config(&arg(&args, 2, "config_json")?)?;
            let patient_id_hash = arg(&args, 3, "patient_id_hash")?;
            let private_metadata = arg(&args, 4, "private_metadata")?;
            let pghd_public_key = arg(&args, 5, "pghd_public_key")?;
            let sender_address = arg(&args, 6, "sender_address")?;
            let sender_key_pair = arg(&args, 7, "sender_key_pair")?;
            block_on(signup_and_publish_pghd_key(
                config,
                patient_id_hash,
                private_metadata,
                pghd_public_key,
                sender_address,
                sender_key_pair,
            ))?;
            print_json(json!({ "ok": true, "data": null }));
        }
        _ => {
            return Err(anyhow!(
                "unknown command {command}. Commands: generate-mnemonic, derive, signup-and-publish-pghd-key"
            ));
        }
    }

    Ok(())
}

fn main() {
    if let Err(err) = run() {
        print_json(json!({
            "ok": false,
            "error": err.chain().map(|cause| cause.to_string()).collect::<Vec<_>>().join("\n")
        }));
        process::exit(1);
    }
}
