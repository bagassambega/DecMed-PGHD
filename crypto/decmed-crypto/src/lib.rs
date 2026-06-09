use std::ffi::{c_char, CStr, CString};
use std::ptr;

use anyhow::{anyhow, Context};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use rand::Rng;
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use umbral_pre::{
    decrypt_original, decrypt_reencrypted, encrypt, generate_kfrags, reencrypt, Capsule,
    CapsuleFrag, KeyFrag, PublicKey, SecretKey, SecretKeyFactory, Signer,
};

#[derive(Debug, Deserialize, Serialize)]
pub struct PreKeyPair {
    pub public_key: String,
    pub secret_seed: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct PreEncryptedPayload {
    pub capsule: String,
    pub ciphertext: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct KfragPayload {
    pub k_frag: String,
    pub signer_pre_public_key: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct CfragPayload {
    pub c_frag: String,
}

pub fn generate_pre_keypair() -> anyhow::Result<PreKeyPair> {
    let seed = random_seed();
    let (_, public_key) = pre_keys_from_seed(&seed)?;

    Ok(PreKeyPair {
        public_key: serialize_to_base64(&public_key)?,
        secret_seed: STANDARD.encode(seed),
    })
}

pub fn public_key_from_seed(secret_seed_base64: &str) -> anyhow::Result<String> {
    let seed = STANDARD
        .decode(secret_seed_base64)
        .context("invalid PRE seed base64")?;
    let (_, public_key) = pre_keys_from_seed(&seed)?;
    serialize_to_base64(&public_key)
}

pub fn encrypt_for_public_key(
    public_key_base64: &str,
    plaintext_base64: &str,
) -> anyhow::Result<PreEncryptedPayload> {
    let public_key: PublicKey = deserialize_from_base64(public_key_base64)?;
    let plaintext = STANDARD
        .decode(plaintext_base64)
        .context("invalid plaintext base64")?;
    let (capsule, ciphertext) =
        encrypt(&public_key, &plaintext).map_err(|e| anyhow!(e.to_string()))?;

    Ok(PreEncryptedPayload {
        capsule: serialize_to_base64(&capsule)?,
        ciphertext: STANDARD.encode(ciphertext),
    })
}

pub fn decrypt_original_with_seed(
    secret_seed_base64: &str,
    capsule_base64: &str,
    ciphertext_base64: &str,
) -> anyhow::Result<String> {
    let seed = STANDARD
        .decode(secret_seed_base64)
        .context("invalid PRE seed base64")?;
    let (secret_key, _) = pre_keys_from_seed(&seed)?;
    let capsule: Capsule = deserialize_from_base64(capsule_base64)?;
    let ciphertext = STANDARD
        .decode(ciphertext_base64)
        .context("invalid ciphertext base64")?;
    let plaintext = decrypt_original(&secret_key, &capsule, ciphertext)
        .map_err(|e| anyhow!(e.to_string()))?;
    Ok(STANDARD.encode(plaintext))
}

pub fn generate_kfrag_for_delegate(
    delegating_secret_seed_base64: &str,
    receiving_public_key_base64: &str,
) -> anyhow::Result<KfragPayload> {
    let delegating_seed = STANDARD
        .decode(delegating_secret_seed_base64)
        .context("invalid delegating PRE seed base64")?;
    let (delegating_secret_key, _) = pre_keys_from_seed(&delegating_seed)?;
    let receiving_public_key: PublicKey = deserialize_from_base64(receiving_public_key_base64)?;
    let signer = Signer::new(SecretKey::random());
    let signer_pre_public_key = signer.verifying_key();
    let k_frags = generate_kfrags(
        &delegating_secret_key,
        &receiving_public_key,
        &signer,
        1,
        1,
        true,
        true,
    );
    let k_frag = k_frags
        .iter()
        .next()
        .ok_or_else(|| anyhow!("failed to generate kfrag"))?
        .clone()
        .unverify();

    Ok(KfragPayload {
        k_frag: serialize_to_base64(&k_frag)?,
        signer_pre_public_key: serialize_to_base64(&signer_pre_public_key)?,
    })
}

pub fn reencrypt_for_delegate(
    capsule_base64: &str,
    k_frag_base64: &str,
    signer_public_key_base64: &str,
    delegating_public_key_base64: &str,
    receiving_public_key_base64: &str,
) -> anyhow::Result<CfragPayload> {
    let capsule: Capsule = deserialize_from_base64(capsule_base64)?;
    let k_frag: KeyFrag = deserialize_from_base64(k_frag_base64)?;
    let signer_public_key: PublicKey = deserialize_from_base64(signer_public_key_base64)?;
    let delegating_public_key: PublicKey = deserialize_from_base64(delegating_public_key_base64)?;
    let receiving_public_key: PublicKey = deserialize_from_base64(receiving_public_key_base64)?;
    let verified_kfrag = k_frag
        .verify(
            &signer_public_key,
            Some(&delegating_public_key),
            Some(&receiving_public_key),
        )
        .map_err(|e| anyhow!(e.0.to_string()))?;
    let c_frag = reencrypt(&capsule, verified_kfrag).unverify();

    Ok(CfragPayload {
        c_frag: serialize_to_base64(&c_frag)?,
    })
}

pub fn decrypt_reencrypted_with_seed(
    receiving_secret_seed_base64: &str,
    delegating_public_key_base64: &str,
    receiving_public_key_base64: &str,
    signer_public_key_base64: &str,
    capsule_base64: &str,
    c_frag_base64: &str,
    ciphertext_base64: &str,
) -> anyhow::Result<String> {
    let receiving_seed = STANDARD
        .decode(receiving_secret_seed_base64)
        .context("invalid receiving PRE seed base64")?;
    let (receiving_secret_key, _) = pre_keys_from_seed(&receiving_seed)?;
    let delegating_public_key: PublicKey = deserialize_from_base64(delegating_public_key_base64)?;
    let receiving_public_key: PublicKey = deserialize_from_base64(receiving_public_key_base64)?;
    let signer_public_key: PublicKey = deserialize_from_base64(signer_public_key_base64)?;
    let capsule: Capsule = deserialize_from_base64(capsule_base64)?;
    let c_frag: CapsuleFrag = deserialize_from_base64(c_frag_base64)?;
    let verified_cfrag = c_frag
        .verify(
            &capsule,
            &signer_public_key,
            &delegating_public_key,
            &receiving_public_key,
        )
        .map_err(|e| anyhow!(e.0.to_string()))?;
    let ciphertext = STANDARD
        .decode(ciphertext_base64)
        .context("invalid ciphertext base64")?;
    let plaintext = decrypt_reencrypted(
        &receiving_secret_key,
        &delegating_public_key,
        &capsule,
        [verified_cfrag],
        ciphertext,
    )
    .map_err(|e| anyhow!(e.to_string()))?;

    Ok(STANDARD.encode(plaintext))
}

fn pre_keys_from_seed(seed: &[u8]) -> anyhow::Result<(SecretKey, PublicKey)> {
    let secret_key = SecretKeyFactory::from_secure_randomness(seed)
        .map_err(|e| anyhow!(e.to_string()))?
        .make_key(b"decmed-pre-v1");
    let public_key = secret_key.public_key();
    Ok((secret_key, public_key))
}

fn random_seed() -> [u8; 32] {
    let mut seed = [0u8; 32];
    rand::rng().fill(&mut seed);
    seed
}

fn serialize_to_base64<T: Serialize>(value: &T) -> anyhow::Result<String> {
    Ok(STANDARD.encode(serde_json::to_vec(value)?))
}

fn deserialize_from_base64<T: DeserializeOwned>(value: &str) -> anyhow::Result<T> {
    let bytes = STANDARD.decode(value).context("invalid serialized base64")?;
    Ok(serde_json::from_slice(&bytes)?)
}

#[no_mangle]
pub extern "C" fn decmed_crypto_free_string(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        drop(CString::from_raw(ptr));
    }
}

#[no_mangle]
pub extern "C" fn decmed_crypto_generate_pre_keypair_json() -> *mut c_char {
    ffi_result(|| generate_pre_keypair())
}

#[no_mangle]
pub extern "C" fn decmed_crypto_public_key_from_seed_json(seed: *const c_char) -> *mut c_char {
    ffi_result(|| public_key_from_seed(read_cstr(seed)?))
}

#[no_mangle]
pub extern "C" fn decmed_crypto_encrypt_for_public_key_json(
    public_key: *const c_char,
    plaintext: *const c_char,
) -> *mut c_char {
    ffi_result(|| encrypt_for_public_key(read_cstr(public_key)?, read_cstr(plaintext)?))
}

#[no_mangle]
pub extern "C" fn decmed_crypto_generate_kfrag_json(
    delegating_secret_seed: *const c_char,
    receiving_public_key: *const c_char,
) -> *mut c_char {
    ffi_result(|| {
        generate_kfrag_for_delegate(
            read_cstr(delegating_secret_seed)?,
            read_cstr(receiving_public_key)?,
        )
    })
}

fn ffi_result<T, F>(f: F) -> *mut c_char
where
    T: Serialize,
    F: FnOnce() -> anyhow::Result<T>,
{
    let body = match f() {
        Ok(value) => serde_json::json!({ "ok": true, "data": value }),
        Err(err) => serde_json::json!({ "ok": false, "error": err.to_string() }),
    };
    match CString::new(body.to_string()) {
        Ok(value) => value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn read_cstr<'a>(ptr: *const c_char) -> anyhow::Result<&'a str> {
    if ptr.is_null() {
        return Err(anyhow!("null pointer"));
    }
    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .context("invalid UTF-8 input")
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_crypto_DecmedCryptoNative_generatePreKeypairJson(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    jni_result(env, |_| generate_pre_keypair())
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_crypto_DecmedCryptoNative_publicKeyFromSeedJson(
    env: JNIEnv,
    _class: JClass,
    seed: JString,
) -> jstring {
    jni_result(env, |env| {
        let seed = jstring_to_string(env, seed)?;
        public_key_from_seed(&seed)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_crypto_DecmedCryptoNative_encryptForPublicKeyJson(
    env: JNIEnv,
    _class: JClass,
    public_key: JString,
    plaintext: JString,
) -> jstring {
    jni_result(env, |env| {
        let public_key = jstring_to_string(env, public_key)?;
        let plaintext = jstring_to_string(env, plaintext)?;
        encrypt_for_public_key(&public_key, &plaintext)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_hackastic_decmed_crypto_DecmedCryptoNative_generateKfragJson(
    env: JNIEnv,
    _class: JClass,
    delegating_secret_seed: JString,
    receiving_public_key: JString,
) -> jstring {
    jni_result(env, |env| {
        let delegating_secret_seed = jstring_to_string(env, delegating_secret_seed)?;
        let receiving_public_key = jstring_to_string(env, receiving_public_key)?;
        generate_kfrag_for_delegate(&delegating_secret_seed, &receiving_public_key)
    })
}

fn jni_result<T, F>(mut env: JNIEnv, f: F) -> jstring
where
    T: Serialize,
    F: FnOnce(&mut JNIEnv) -> anyhow::Result<T>,
{
    let body = match f(&mut env) {
        Ok(value) => serde_json::json!({ "ok": true, "data": value }),
        Err(err) => serde_json::json!({ "ok": false, "error": err.to_string() }),
    };
    match env.new_string(body.to_string()) {
        Ok(value) => value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn jstring_to_string(env: &mut JNIEnv, value: JString) -> anyhow::Result<String> {
    Ok(env.get_string(&value)?.into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn umbral_round_trip_decrypts_original_payload() {
        let keys = generate_pre_keypair().unwrap();
        let plaintext = STANDARD.encode(b"key-nonce-material");
        let encrypted = encrypt_for_public_key(&keys.public_key, &plaintext).unwrap();
        let decrypted =
            decrypt_original_with_seed(&keys.secret_seed, &encrypted.capsule, &encrypted.ciphertext)
                .unwrap();

        assert_eq!(STANDARD.decode(decrypted).unwrap(), b"key-nonce-material");
    }
}
