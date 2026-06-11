mod constants;
mod handlers;
mod macros;
mod middlewares;
mod move_call;
mod proxy_error;
mod tes_error;
mod types;
mod utils;

use std::{env, str::FromStr, sync::Arc, time::Duration};

use anyhow::{Context, Result};
use axum::{
    middleware,
    routing::{get, post, put},
    Router,
};
use constants::{
    DECMED_ADDRESS_ID_OBJECT_ID, DECMED_ADDRESS_ID_OBJECT_VERSION, DECMED_GLOBAL_ADMIN_CAP_ID,
    DECMED_HOSPITAL_ID_METADATA_OBJECT_ID, DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION,
    DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID,
    DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION, DECMED_MODULE_ADMIN, DECMED_MODULE_PROXY,
    DECMED_PACKAGE_ID, DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID,
    DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION,
};
use handlers::Handlers;
use iota_types::{base_types::ObjectID, Identifier};
use move_call::MoveCall;
use tower::ServiceBuilder;
use types::{AppState, CacheStore, DecmedPackage};

#[tokio::main]
async fn main() -> Result<()> {
    // Load envs from .env file when present; containerized deployments inject env vars directly.
    let _ = dotenvy::dotenv();

    // Envs
    let cache_backend = env::var("CACHE_BACKEND").unwrap_or_else(|_| "redis".to_string());
    let port = env::var("PORT").context("missing PORT")?;
    let global_admin_iota_address =
        env::var("GLOBAL_ADMIN_IOTA_ADDRESS").context("missing GLOBAL_ADMIN_IOTA_ADDRESS")?;
    let global_admin_iota_key_pair =
        env::var("GLOBAL_ADMIN_IOTA_KEY_PAIR").context("missing GLOBAL_ADMIN_IOTA_KEY_PAIR")?;
    let proxy_iota_address =
        env::var("PROXY_IOTA_ADDRESS").context("missing PROXY_IOTA_ADDRESS")?;
    let proxy_iota_key_pair =
        env::var("PROXY_IOTA_KEY_PAIR").context("missing PROXY_IOTA_KEY_PAIR")?;
    let jwt_ecdsa_key_pair =
        env::var("JWT_ECDSA_KEY_PAIR").context("missing JWT_ECDSA_KEY_PAIR")?;
    let jwt_ecdsa_pub_key = env::var("JWT_ECDSA_PUB_KEY").context("missing JWT_ECDSA_PUB_KEY")?;

    // Cache store for nonce and temporary access-key material.
    let cache_store = if cache_backend.eq_ignore_ascii_case("memory") {
        eprintln!(
            "using in-memory cache store; use CACHE_BACKEND=redis for multi-process deployments"
        );
        CacheStore::memory()
    } else {
        let redis_connection_url =
            env::var("REDIS_CONNECTION_URL_DEV").context("missing REDIS_CONNECTION_URL_DEV")?;
        let redis_client =
            redis::Client::open(redis_connection_url.as_str()).with_context(|| {
                format!("invalid Redis URL in REDIS_CONNECTION_URL_DEV: {redis_connection_url}")
            })?;
        let redis_pool = r2d2::Pool::builder()
            .connection_timeout(Duration::from_secs(3))
            .build(redis_client)
            .with_context(|| {
                format!(
                    "failed to connect to Redis at {redis_connection_url}; start local Redis, set CACHE_BACKEND=memory for local single-process development, or update REDIS_CONNECTION_URL_DEV"
                )
            })?;
        CacheStore::redis(redis_pool)
    };

    // App state
    let decmed_package = DecmedPackage {
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

        global_admin_cap_id: ObjectID::from_str(DECMED_GLOBAL_ADMIN_CAP_ID)?,
    };
    let move_call = MoveCall { decmed_package };
    let shared_state = Arc::new(AppState {
        global_admin_iota_address,
        global_admin_iota_key_pair,
        jwt_ecdsa_key_pair,
        jwt_ecdsa_pub_key,
        move_call,
        proxy_iota_address,
        proxy_iota_key_pair,
        cache_store,
    });

    let protected_routes = Router::new()
        .route("/", get(|| async { "Hello, world!" }))
        .route("/medical-record", get(Handlers::get_medical_record))
        .route("/medical-record", post(Handlers::create_medical_record))
        .route("/medical-record", put(Handlers::update_medical_record))
        .route("/pghd", get(Handlers::get_pghd_list))
        .route("/pghd/invalidate", post(Handlers::invalidate_pghd))
        .route("/pghd/{index}", get(Handlers::get_pghd))
        .route(
            "/medical-record-update",
            get(Handlers::get_medical_record_update),
        )
        .route("/administrative", get(Handlers::get_administrative_data))
        .layer(ServiceBuilder::new().layer(middleware::from_fn_with_state(
            shared_state.clone(),
            middlewares::auth_middleware,
        )));

    // .../gen/...
    let gen_routes = Router::new()
        .route("/jwt", get(Handlers::generate_jwt_handler))
        .route("/sig", get(Handlers::generate_signature))
        .route(
            "/proxy-address",
            get(Handlers::generate_and_register_proxy_address),
        );

    let public_routes = Router::new()
        .route("/health", get(|| async { "OK" }))
        .route("/nonce", post(Handlers::get_nonce_handler))
        .route("/keys", post(Handlers::store_keys))
        .route("/keys/revoke", post(Handlers::revoke_keys))
        .route("/pghd/patient", post(Handlers::register_pghd_patient))
        .route("/pghd/submit", post(Handlers::submit_pghd));

    let api_routes = Router::new()
        .nest("/gen", gen_routes)
        .merge(protected_routes)
        .merge(public_routes);

    let api_v1_routes = Router::new().nest("/api/v1", api_routes);

    // App
    let app = Router::new()
        .route("/health", get(|| async { "OK" }))
        .merge(api_v1_routes)
        .with_state(shared_state);

    let bind_addr = format!("0.0.0.0:{}", port);
    let listener = tokio::net::TcpListener::bind(&bind_addr)
        .await
        .with_context(|| format!("failed to bind backend listener at {bind_addr}"))?;
    println!("proxy-reencryption backend listening on {bind_addr}");
    axum::serve(listener, app)
        .await
        .context("backend server stopped unexpectedly")?;

    Ok(())
}
