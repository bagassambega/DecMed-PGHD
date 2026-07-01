use std::{env, process};

use anyhow::anyhow;
use decmed_crypto::{encrypt_for_public_key, generate_pre_keypair, public_key_from_seed};
use serde_json::json;

fn print_json(value: serde_json::Value) {
    println!("{}", serde_json::to_string_pretty(&value).unwrap());
}

fn arg(args: &[String], index: usize, name: &str) -> anyhow::Result<String> {
    args.get(index)
        .cloned()
        .ok_or_else(|| anyhow!("missing argument {name}"))
}

fn run() -> anyhow::Result<()> {
    let args = env::args().collect::<Vec<_>>();
    let command = arg(&args, 1, "command")?;

    match command.as_str() {
        "generate-pre-keypair" => {
            print_json(json!({ "ok": true, "data": generate_pre_keypair()? }));
        }
        "public-key-from-seed" => {
            let seed = arg(&args, 2, "secret_seed_base64")?;
            print_json(json!({ "ok": true, "data": public_key_from_seed(&seed)? }));
        }
        "encrypt-for-public-key" => {
            let public_key = arg(&args, 2, "public_key_base64")?;
            let plaintext = arg(&args, 3, "plaintext_base64")?;
            print_json(json!({ "ok": true, "data": encrypt_for_public_key(&public_key, &plaintext)? }));
        }
        _ => {
            return Err(anyhow!(
                "unknown command {command}. Commands: generate-pre-keypair, public-key-from-seed, encrypt-for-public-key"
            ));
        }
    }

    Ok(())
}

fn main() {
    if let Err(err) = run() {
        print_json(json!({ "ok": false, "error": err.to_string() }));
        process::exit(1);
    }
}
