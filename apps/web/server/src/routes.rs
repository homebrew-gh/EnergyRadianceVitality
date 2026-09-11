use std::sync::Arc;
use std::time::Duration;

use axum::{
    extract::{Query, State},
    http::{header, HeaderValue, StatusCode, Uri},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use axum_extra::extract::{
    cookie::{Cookie, Key, SameSite},
    SignedCookieJar,
};
use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;
use tower_http::services::ServeDir;
use tower_http::set_header::SetResponseHeaderLayer;
use tower_http::trace::TraceLayer;

use crate::blossom::{
    allowed_blossom_origins, blossom_origin_from_relay_url, check_blossom_status,
    fetch_blossom_blob, is_allowed_blossom_blob_url,
};
use crate::config::{
    detected_relay_label, detected_relay_url, detected_relays, normalize_relay_url,
    relay_prefill_url, suggested_relay_url, Config, DetectedRelay, DETECTED_RELAY_LABEL,
};
use crate::crypto::{open as crypto_open, seal};
use crate::error::{AppError, AppResult};
use crate::erv_tags::{is_erv_d_tag, is_erv_publishable_d_tag};
use crate::nostr_support::{
    build_app_data_event, decrypt_from_self, encrypt_to_self, fetch_decrypted_app_data, parse_nsec,
    KeyIdentity,
};
use crate::outbox::{Outbox, OutboxStatus};
use crate::rate_limit::AttemptLimiter;
use crate::session::{SessionStore, SESSION_COOKIE};
use crate::state::{dedupe_relay_urls, PersistentState, SealedRecord};

#[derive(Clone)]
pub struct AppState {
    pub cfg: Config,
    pub sessions: SessionStore,
    pub persistent: Arc<Mutex<PersistentState>>,
    pub cookie_key: Key,
    pub outbox: Outbox,
    pub recovery_limiter: AttemptLimiter,
}

pub async fn build_router(cfg: Config) -> anyhow::Result<Router> {
    let persistent = PersistentState::load(&cfg.state_path())?;
    let cookie_key = Key::from(&cfg.cookie_signing_key);
    let sessions = SessionStore::new(cfg.session_idle);

    let state = AppState {
        cfg: cfg.clone(),
        sessions,
        persistent: Arc::new(Mutex::new(persistent)),
        cookie_key,
        outbox: Outbox::new(),
        recovery_limiter: AttemptLimiter::new(5, Duration::from_secs(15 * 60)),
    };

    let assets_service = ServeDir::new(cfg.static_dir.join("assets"));

    let api = Router::new()
        .route("/health", get(health))
        .route("/auth/status", get(auth_status))
        .route("/auth/setup", post(auth_setup))
        .route("/auth/unlock", post(auth_unlock))
        .route("/auth/recover", post(auth_recover))
        .route("/auth/passphrase", post(auth_passphrase))
        .route("/auth/lock", post(auth_lock))
        .route("/auth/wipe", post(auth_wipe))
        .route("/settings/relay", get(get_relay).put(put_relay))
        .route("/crypto/nip44/encrypt-self", post(nip44_encrypt_self))
        .route("/crypto/nip44/decrypt-self", post(nip44_decrypt_self))
        .route("/nostr/app-data", get(list_app_data).post(publish_app_data))
        .route("/nostr/connection", get(relay_connection))
        .route("/nostr/outbox", get(outbox_status))
        .route("/nostr/outbox/retry", post(outbox_retry))
        .route("/nostr/outbox/clear", post(outbox_clear))
        .route("/media/blossom-status", get(blossom_status))
        .route("/media/blob", get(blossom_blob));

    let app = Router::new()
        .nest("/api", api)
        .nest_service("/assets", assets_service)
        .fallback(spa_fallback)
        .with_state(state)
        .layer(SetResponseHeaderLayer::if_not_present(
            header::CACHE_CONTROL,
            HeaderValue::from_static("no-store"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::X_CONTENT_TYPE_OPTIONS,
            HeaderValue::from_static("nosniff"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::REFERRER_POLICY,
            HeaderValue::from_static("no-referrer"),
        ))
        .layer(TraceLayer::new_for_http());

    Ok(app)
}

#[derive(Serialize)]
struct Health {
    ok: bool,
}

async fn health() -> Json<Health> {
    Json(Health { ok: true })
}

#[derive(Serialize)]
pub struct AuthStatus {
    has_state: bool,
    unlocked: bool,
    passphrase_set: bool,
    npub: Option<String>,
    relay_url: Option<String>,
    relay_urls: Vec<String>,
    detected_relay_url: Option<String>,
    detected_relay_label: Option<String>,
    detected_relays: Vec<DetectedRelay>,
    suggested_relay_url: Option<String>,
    relay_prefill_url: Option<String>,
}

fn auth_status_from(p: &PersistentState, unlocked: bool) -> AuthStatus {
    let detected = detected_relay_url();
    let suggested = suggested_relay_url();
    let prefill = relay_prefill_url();
    let relays = detected_relays();
    AuthStatus {
        has_state: p.has_state(),
        unlocked,
        passphrase_set: p.passphrase_set(),
        npub: p.npub.clone(),
        relay_url: p.primary_relay_url().map(str::to_string),
        relay_urls: p.relay_urls().to_vec(),
        detected_relay_label: detected_relay_label().or_else(|| {
            if detected.is_some() || suggested.is_some() {
                Some(DETECTED_RELAY_LABEL.to_string())
            } else {
                None
            }
        }),
        detected_relay_url: detected,
        detected_relays: relays,
        suggested_relay_url: suggested,
        relay_prefill_url: prefill,
    }
}

async fn auth_status(State(s): State<AppState>, jar: SignedCookieJar) -> Json<AuthStatus> {
    let p = s.persistent.lock().await;
    let unlocked = session_unlocked(&s, &jar).await || p.plain_nsec().is_some();
    Json(auth_status_from(&p, unlocked))
}

#[derive(Deserialize)]
pub struct SetupBody {
    nsec: String,
    #[serde(default)]
    passphrase: String,
    #[serde(default)]
    relay_url: String,
}

async fn auth_setup(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<SetupBody>,
) -> AppResult<(SignedCookieJar, Json<AuthStatus>)> {
    let (_, identity) = parse_nsec(&body.nsec).map_err(|e| AppError::BadRequest(e.to_string()))?;
    let passphrase = optional_passphrase(&body.passphrase)?;
    let relay_url = resolve_setup_relay_url(&body.relay_url)?;

    let mut p = s.persistent.lock().await;
    if p.has_state() {
        return Err(AppError::Conflict(
            "state already initialized; unlock or wipe to reset".into(),
        ));
    }

    persist_nsec(&mut p, &body.nsec, passphrase, &identity)?;
    p.set_relay_urls(vec![relay_url]);
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;

    let secret = zeroize::Zeroizing::new(body.nsec.clone().into_bytes());
    let sid = s.sessions.open(secret, identity).await;
    let jar = jar.add(session_cookie(sid, s.cfg.cookie_secure));

    Ok((jar, Json(auth_status_from(&p, true))))
}

#[derive(Deserialize)]
pub struct UnlockBody {
    passphrase: String,
}

async fn auth_unlock(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<UnlockBody>,
) -> AppResult<(SignedCookieJar, Json<AuthStatus>)> {
    let p = s.persistent.lock().await;
    if let Some(nsec) = p.plain_nsec() {
        let nsec = nsec.to_string();
        let (_, identity) =
            parse_nsec(&nsec).map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?;
        drop(p);
        let secret = zeroize::Zeroizing::new(nsec.into_bytes());
        let sid = s.sessions.open(secret, identity).await;
        let jar = jar.add(session_cookie(sid, s.cfg.cookie_secure));
        let p = s.persistent.lock().await;
        return Ok((jar, Json(auth_status_from(&p, true))));
    }
    let sealed = p
        .sealed
        .as_ref()
        .ok_or_else(|| AppError::BadRequest("no state to unlock; run setup first".into()))?;
    let blob = sealed.to_blob().map_err(AppError::Internal)?;
    drop(p);

    let plaintext = crypto_open(&body.passphrase, &blob).map_err(|_| AppError::Unauthorized)?;
    let nsec = std::str::from_utf8(plaintext.as_slice())
        .map_err(|e| AppError::Internal(anyhow::anyhow!("stored nsec is not utf-8: {e}")))?;
    let (_, identity) = parse_nsec(nsec).map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?;
    let sid = s.sessions.open(plaintext, identity).await;
    let jar = jar.add(session_cookie(sid, s.cfg.cookie_secure));

    let p = s.persistent.lock().await;
    Ok((jar, Json(auth_status_from(&p, true))))
}

#[derive(Deserialize)]
pub struct RecoverBody {
    nsec: String,
    #[serde(default)]
    passphrase: String,
}

async fn auth_recover(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<RecoverBody>,
) -> AppResult<(SignedCookieJar, Json<AuthStatus>)> {
    if !s.recovery_limiter.allow() {
        return Err(AppError::TooManyRequests);
    }

    let identity = match parse_nsec(&body.nsec) {
        Ok((_, identity)) => identity,
        Err(_) => {
            s.recovery_limiter.record_failure();
            return Err(AppError::BadRequest(
                "secret key must be an nsec1... string".into(),
            ));
        }
    };
    let passphrase = match optional_passphrase(&body.passphrase) {
        Ok(p) => p,
        Err(e) => {
            s.recovery_limiter.record_failure();
            return Err(e);
        }
    };

    let mut p = s.persistent.lock().await;
    if !p.has_state() {
        return Err(AppError::BadRequest(
            "no state to recover; run setup first".into(),
        ));
    }
    if let Some(existing) = p.npub.as_deref() {
        if existing != identity.npub {
            s.recovery_limiter.record_failure();
            tracing::warn!("nsec recovery failed: identity mismatch");
            return Err(AppError::BadRequest(
                "nsec does not match this companion".into(),
            ));
        }
    }

    persist_nsec(&mut p, &body.nsec, passphrase, &identity)?;
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;
    s.recovery_limiter.reset();

    let secret = zeroize::Zeroizing::new(body.nsec.clone().into_bytes());
    let sid = s.sessions.open(secret, identity).await;
    let jar = jar.add(session_cookie(sid, s.cfg.cookie_secure));
    Ok((jar, Json(auth_status_from(&p, true))))
}

#[derive(Deserialize)]
pub struct PassphraseBody {
    #[serde(default)]
    passphrase: String,
}

async fn auth_passphrase(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<PassphraseBody>,
) -> AppResult<Json<AuthStatus>> {
    let (nsec, identity) = require_nsec(&s, &jar).await?;
    let passphrase = optional_passphrase(&body.passphrase)?;
    let nsec_str = std::str::from_utf8(nsec.as_slice())
        .map_err(|e| AppError::Internal(anyhow::anyhow!("session nsec is not utf-8: {e}")))?;

    let mut p = s.persistent.lock().await;
    persist_nsec(&mut p, nsec_str, passphrase, &identity)?;
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;
    Ok(Json(auth_status_from(&p, true)))
}

#[derive(Serialize)]
struct OkBody {
    ok: bool,
}

async fn auth_lock(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> (SignedCookieJar, Json<OkBody>) {
    if let Some(c) = jar.get(SESSION_COOKIE) {
        s.sessions.close(c.value()).await;
    }
    let jar = jar.remove(Cookie::from(SESSION_COOKIE));
    (jar, Json(OkBody { ok: true }))
}

#[derive(Deserialize)]
struct WipeBody {
    #[serde(default)]
    passphrase: Option<String>,
    confirmation: String,
}

async fn auth_wipe(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<WipeBody>,
) -> AppResult<(SignedCookieJar, Json<OkBody>)> {
    let _ = body.passphrase;
    if body.confirmation.trim() != "DELETE" {
        return Err(AppError::BadRequest(
            "type DELETE in the confirmation field to remove the key".into(),
        ));
    }

    let mut p = s.persistent.lock().await;
    *p = PersistentState::default();
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;
    drop(p);

    if let Some(c) = jar.get(SESSION_COOKIE) {
        s.sessions.close(c.value()).await;
    }
    s.sessions.close_all().await;
    s.recovery_limiter.reset();
    let jar = jar.remove(Cookie::from(SESSION_COOKIE));
    Ok((jar, Json(OkBody { ok: true })))
}

#[derive(Serialize, Deserialize)]
pub struct RelayBody {
    #[serde(default)]
    relay_url: String,
    #[serde(default)]
    relay_urls: Vec<String>,
}

async fn get_relay(State(s): State<AppState>, jar: SignedCookieJar) -> AppResult<Json<RelayBody>> {
    require_unlocked(&s, &jar).await?;
    let p = s.persistent.lock().await;
    Ok(Json(RelayBody {
        relay_url: p.primary_relay_url().unwrap_or("").to_string(),
        relay_urls: p.relay_urls().to_vec(),
    }))
}

async fn put_relay(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<RelayBody>,
) -> AppResult<Json<RelayBody>> {
    require_unlocked(&s, &jar).await?;
    let urls = normalize_relay_urls_from_body(&body)?;
    let mut p = s.persistent.lock().await;
    p.set_relay_urls(urls);
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;
    Ok(Json(RelayBody {
        relay_url: p.primary_relay_url().unwrap_or("").to_string(),
        relay_urls: p.relay_urls().to_vec(),
    }))
}

#[derive(Deserialize)]
struct Nip44EncryptBody {
    plaintext: String,
}

#[derive(Serialize)]
struct Nip44EncryptResponse {
    ciphertext: String,
}

async fn nip44_encrypt_self(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<Nip44EncryptBody>,
) -> AppResult<Json<Nip44EncryptResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let ciphertext = encrypt_to_self(&keys, &body.plaintext).map_err(AppError::Internal)?;
    Ok(Json(Nip44EncryptResponse { ciphertext }))
}

#[derive(Deserialize)]
struct Nip44DecryptBody {
    ciphertext: String,
}

#[derive(Serialize)]
struct Nip44DecryptResponse {
    plaintext: String,
}

async fn nip44_decrypt_self(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<Nip44DecryptBody>,
) -> AppResult<Json<Nip44DecryptResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let plaintext = decrypt_from_self(&keys, &body.ciphertext)
        .map_err(|e| AppError::BadRequest(e.to_string()))?;
    Ok(Json(Nip44DecryptResponse { plaintext }))
}

#[derive(Serialize)]
struct RelayConnectionResponse {
    connected: bool,
    message: Option<String>,
}

#[derive(Serialize)]
struct BlossomStatusResponse {
    available: bool,
    auth_verified: bool,
    origin: Option<String>,
    derived_from_relay_url: Option<String>,
    message: String,
}

#[derive(Deserialize)]
struct BlossomBlobQuery {
    url: String,
}

async fn relay_connection(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<RelayConnectionResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let cfg = s.cfg.clone();
    match crate::nostr_support::probe_relay_connection(&keys, &relay_urls, |url| {
        cfg.relay_connect_options(url)
    })
    .await
    {
        Ok(_) => Ok(Json(RelayConnectionResponse {
            connected: true,
            message: None,
        })),
        Err(err) => Ok(Json(RelayConnectionResponse {
            connected: false,
            message: Some(err.to_string()),
        })),
    }
}

async fn blossom_status(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<BlossomStatusResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let relay_url = relay_urls.first().cloned();
    let Some(relay_url) = relay_url else {
        return Ok(Json(BlossomStatusResponse {
            available: false,
            auth_verified: false,
            origin: None,
            derived_from_relay_url: None,
            message: "No relay configured.".into(),
        }));
    };
    let Some(origin) = blossom_origin_from_relay_url(&relay_url) else {
        return Ok(Json(BlossomStatusResponse {
            available: false,
            auth_verified: false,
            origin: None,
            derived_from_relay_url: Some(relay_url),
            message: "Relay URL cannot be mapped to an HTTP Blossom origin.".into(),
        }));
    };

    let probe_origin = origin.clone();
    let accept_invalid_tls = s.cfg.insecure_relay_tls.unwrap_or(false);
    let keys = keys.clone();
    let probe = tokio::task::spawn_blocking(move || {
        check_blossom_status(&probe_origin, accept_invalid_tls, &keys)
    })
    .await
    .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?;

    Ok(Json(BlossomStatusResponse {
        available: probe.available,
        auth_verified: probe.auth_verified,
        origin: Some(origin),
        derived_from_relay_url: Some(relay_url),
        message: probe.message,
    }))
}

async fn blossom_blob(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Query(query): Query<BlossomBlobQuery>,
) -> AppResult<Response> {
    require_unlocked(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let allowed = allowed_blossom_origins(&relay_urls);
    if allowed.is_empty() {
        return Err(AppError::BadRequest(
            "No Blossom origin can be derived from the configured relay.".into(),
        ));
    }
    if !is_allowed_blossom_blob_url(&query.url, &allowed) {
        return Err(AppError::BadRequest(
            "Blob URL is not under the configured Blossom origin.".into(),
        ));
    }

    let blob_url = query.url.clone();
    let accept_invalid_tls = s.cfg.insecure_relay_tls.unwrap_or(false);
    let fetched =
        tokio::task::spawn_blocking(move || fetch_blossom_blob(&blob_url, accept_invalid_tls))
            .await
            .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?
            .map_err(AppError::BadRequest)?;

    let (body, content_type) = fetched;
    let mut response = Response::builder().status(StatusCode::OK);
    if let Some(content_type) = content_type {
        response = response.header(header::CONTENT_TYPE, content_type);
    } else {
        response = response.header(header::CONTENT_TYPE, "application/octet-stream");
    }
    response
        .body(axum::body::Body::from(body))
        .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))
}

async fn list_app_data(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<crate::nostr_support::AppDataListResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let cfg = s.cfg.clone();
    let fetched =
        fetch_decrypted_app_data(&keys, &relay_urls, |url| cfg.relay_connect_options(url))
            .await
            .map_err(|e| AppError::BadRequest(format!("relay fetch failed: {e}")))?;

    let records: Vec<_> = fetched
        .records
        .into_iter()
        .filter(|r| r.d_tag.as_deref().map(is_erv_d_tag).unwrap_or(false))
        .collect();

    Ok(Json(crate::nostr_support::AppDataListResponse {
        records,
        meta: fetched.meta,
    }))
}

#[derive(Deserialize)]
struct PublishAppDataBody {
    d_tag: String,
    plaintext: String,
}

#[derive(Serialize)]
struct PublishAppDataResponse {
    event_id: String,
}

async fn publish_app_data(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<PublishAppDataBody>,
) -> AppResult<Json<PublishAppDataResponse>> {
    if !is_erv_publishable_d_tag(&body.d_tag) {
        return Err(AppError::BadRequest(
            "d_tag is not publishable in this ERV companion build".into(),
        ));
    }
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let event = build_app_data_event(&keys, &body.d_tag, &body.plaintext)
        .map_err(|e| AppError::BadRequest(format!("sign failed: {e}")))?;
    let event_id = event.id.to_string();
    s.outbox
        .enqueue(s.cfg.clone(), keys, event, relay_urls, body.d_tag.clone())
        .await;
    Ok(Json(PublishAppDataResponse { event_id }))
}

#[derive(Serialize)]
struct OutboxStatusResponse {
    pending: usize,
    failed: usize,
    failed_items: Vec<OutboxItemView>,
}

#[derive(Serialize)]
struct OutboxItemView {
    id: u64,
    label: String,
    error: String,
}

impl From<OutboxStatus> for OutboxStatusResponse {
    fn from(s: OutboxStatus) -> Self {
        OutboxStatusResponse {
            pending: s.pending,
            failed: s.failed,
            failed_items: s
                .failed_items
                .into_iter()
                .map(|f| OutboxItemView {
                    id: f.id,
                    label: f.label,
                    error: f.error,
                })
                .collect(),
        }
    }
}

async fn outbox_status(State(s): State<AppState>) -> Json<OutboxStatusResponse> {
    Json(s.outbox.status().await.into())
}

async fn outbox_retry(State(s): State<AppState>) -> Json<OutboxStatusResponse> {
    s.outbox.retry_failed().await;
    Json(s.outbox.status().await.into())
}

async fn outbox_clear(State(s): State<AppState>) -> Json<OutboxStatusResponse> {
    s.outbox.clear_failed().await;
    Json(s.outbox.status().await.into())
}

async fn require_unlocked(s: &AppState, jar: &SignedCookieJar) -> AppResult<()> {
    if session_unlocked(s, jar).await {
        return Ok(());
    }
    let p = s.persistent.lock().await;
    if p.plain_nsec().is_some() {
        return Ok(());
    }
    Err(AppError::Unauthorized)
}

async fn session_unlocked(s: &AppState, jar: &SignedCookieJar) -> bool {
    match jar.get(SESSION_COOKIE) {
        Some(c) => s.sessions.touch_unlocked(c.value()).await,
        None => false,
    }
}

async fn require_keys(
    s: &AppState,
    jar: &SignedCookieJar,
) -> AppResult<(nostr::Keys, crate::nostr_support::KeyIdentity)> {
    if let Some(sid) = jar.get(SESSION_COOKIE) {
        if let Some(keys) = s
            .sessions
            .keys_for(sid.value())
            .await
            .map_err(AppError::Internal)?
        {
            return Ok(keys);
        }
    }
    let nsec = {
        let p = s.persistent.lock().await;
        p.plain_nsec().map(str::to_string)
    };
    if let Some(nsec) = nsec {
        return parse_nsec(&nsec).map_err(AppError::Internal);
    }
    Err(AppError::Unauthorized)
}

async fn require_nsec(
    s: &AppState,
    jar: &SignedCookieJar,
) -> AppResult<(zeroize::Zeroizing<Vec<u8>>, KeyIdentity)> {
    if let Some(sid) = jar.get(SESSION_COOKIE) {
        if let Some(pair) = s.sessions.nsec_for(sid.value()).await {
            return Ok(pair);
        }
    }
    let nsec = {
        let p = s.persistent.lock().await;
        p.plain_nsec().map(str::to_string)
    };
    if let Some(nsec) = nsec {
        let (_, identity) = parse_nsec(&nsec).map_err(AppError::Internal)?;
        return Ok((zeroize::Zeroizing::new(nsec.into_bytes()), identity));
    }
    Err(AppError::Unauthorized)
}

fn optional_passphrase(value: &str) -> AppResult<Option<&str>> {
    if value.is_empty() {
        Ok(None)
    } else if value.len() < 8 {
        Err(AppError::BadRequest(
            "passphrase must be at least 8 characters".into(),
        ))
    } else {
        Ok(Some(value))
    }
}

fn persist_nsec(
    p: &mut PersistentState,
    nsec: &str,
    passphrase: Option<&str>,
    identity: &KeyIdentity,
) -> AppResult<()> {
    match passphrase {
        None => p.store_plain_nsec(nsec.to_string()),
        Some(pass) => {
            let blob = seal(pass, nsec.as_bytes()).map_err(AppError::Internal)?;
            p.store_sealed(SealedRecord::from_blob(&blob));
        }
    }
    p.npub = Some(identity.npub.clone());
    Ok(())
}

async fn configured_relay_urls(s: &AppState) -> AppResult<Vec<String>> {
    let p = s.persistent.lock().await;
    let urls = stored_or_detected_relay_urls(p.relay_urls());
    if urls.is_empty() {
        return Err(AppError::BadRequest("relay url is not configured".into()));
    }
    Ok(urls)
}

fn stored_or_detected_relay_urls(stored: &[String]) -> Vec<String> {
    let urls: Vec<String> = stored
        .iter()
        .filter(|url| is_allowed_relay_url(url))
        .cloned()
        .collect();
    if !urls.is_empty() {
        return urls;
    }
    detected_relay_url().into_iter().collect()
}

fn resolve_setup_relay_url(user_url: &str) -> AppResult<String> {
    let trimmed = user_url.trim();
    if !trimmed.is_empty() {
        if !is_allowed_relay_url(trimmed) {
            return Err(AppError::BadRequest(relay_url_policy_message()));
        }
        return Ok(normalize_relay_url(trimmed));
    }
    detected_relay_url().ok_or_else(|| AppError::BadRequest(relay_url_policy_message()))
}

fn normalize_relay_urls_from_body(body: &RelayBody) -> AppResult<Vec<String>> {
    let mut urls = if !body.relay_urls.is_empty() {
        body.relay_urls.clone()
    } else if !body.relay_url.trim().is_empty() {
        vec![body.relay_url.clone()]
    } else {
        Vec::new()
    };
    urls = dedupe_relay_urls(urls);
    if urls.is_empty() {
        if let Some(detected) = detected_relay_url() {
            return Ok(vec![detected]);
        }
        return Err(AppError::BadRequest(
            "at least one relay url is required".into(),
        ));
    }
    for url in &urls {
        if !is_allowed_relay_url(url) {
            return Err(AppError::BadRequest(relay_url_policy_message()));
        }
    }
    Ok(urls.into_iter().map(|u| normalize_relay_url(&u)).collect())
}

fn is_allowed_relay_url(url: &str) -> bool {
    if url.starts_with("wss://") {
        return true;
    }
    let Some(rest) = url.strip_prefix("ws://") else {
        return false;
    };
    let host_port_path = rest.split('/').next().unwrap_or_default();
    let host_with_port = host_port_path
        .rsplit_once('@')
        .map(|(_, host)| host)
        .unwrap_or(host_port_path);
    let host = if let Some(rest) = host_with_port.strip_prefix('[') {
        rest.split(']').next().unwrap_or_default()
    } else {
        host_with_port.split(':').next().unwrap_or_default()
    };
    if host.ends_with(".startos") {
        return true;
    }
    matches!(host, "127.0.0.1" | "localhost" | "[::1]" | "::1")
}

fn relay_url_policy_message() -> String {
    "relay url must be wss://, ws://<package>.startos (StartOS internal relay), or ws://127.0.0.1 / ws://localhost".into()
}

fn session_cookie(sid: String, secure: bool) -> Cookie<'static> {
    let mut c = Cookie::new(SESSION_COOKIE, sid);
    c.set_http_only(true);
    c.set_same_site(SameSite::Strict);
    c.set_path("/");
    c.set_secure(secure);
    c
}

impl axum::extract::FromRef<AppState> for Key {
    fn from_ref(state: &AppState) -> Self {
        state.cookie_key.clone()
    }
}

async fn spa_fallback(State(s): State<AppState>, uri: Uri) -> Response {
    if uri.path().starts_with("/api/") {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"error":"not found"})),
        )
            .into_response();
    }
    let index = s.cfg.static_dir.join("index.html");
    match tokio::fs::read(&index).await {
        Ok(bytes) => ([(header::CONTENT_TYPE, "text/html; charset=utf-8")], bytes).into_response(),
        Err(_) => (
            StatusCode::NOT_FOUND,
            "frontend assets missing; build apps/web/web/ and set ERV_STATIC_DIR",
        )
            .into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use nostr::{Keys, ToBech32};
    use rand::RngCore;
    use serde_json::{json, Value};
    use tower::ServiceExt;

    fn test_nsec() -> String {
        Keys::generate()
            .secret_key()
            .to_bech32()
            .expect("nsec bech32")
    }

    fn test_config() -> (Config, tempfile_dir::Guard) {
        let guard = tempfile_dir::Guard::new();
        let mut cookie_signing_key = [0u8; 64];
        rand::rngs::OsRng.fill_bytes(&mut cookie_signing_key);
        let cfg = Config {
            data_dir: guard.path.clone(),
            static_dir: guard.path.clone(),
            bind_addr: "127.0.0.1:0".parse().unwrap(),
            cookie_signing_key,
            session_idle: Duration::from_secs(60),
            cookie_secure: false,
            insecure_relay_tls: None,
        };
        (cfg, guard)
    }

    mod tempfile_dir {
        use std::path::PathBuf;
        use std::sync::atomic::{AtomicU64, Ordering};

        static SEQ: AtomicU64 = AtomicU64::new(0);

        pub struct Guard {
            pub path: PathBuf,
        }

        impl Guard {
            pub fn new() -> Self {
                let path = std::env::temp_dir().join(format!(
                    "erv-auth-{}-{}",
                    std::process::id(),
                    SEQ.fetch_add(1, Ordering::Relaxed)
                ));
                std::fs::create_dir_all(&path).unwrap();
                Self { path }
            }
        }

        impl Drop for Guard {
            fn drop(&mut self) {
                let _ = std::fs::remove_dir_all(&self.path);
            }
        }
    }

    async fn json_body(res: Response) -> Value {
        let bytes = axum::body::to_bytes(res.into_body(), 1024 * 1024)
            .await
            .unwrap();
        serde_json::from_slice(&bytes).unwrap()
    }

    async fn send(app: &Router, req: Request<Body>) -> Response {
        app.clone().oneshot(req).await.unwrap()
    }

    fn post_json(uri: &str, body: Value) -> Request<Body> {
        Request::builder()
            .method("POST")
            .uri(uri)
            .header(header::CONTENT_TYPE, "application/json")
            .body(Body::from(body.to_string()))
            .unwrap()
    }

    #[tokio::test]
    async fn setup_without_passphrase_is_unlocked_and_has_no_seal() {
        let (cfg, _guard) = test_config();
        let state_path = cfg.state_path();
        let app = build_router(cfg).await.unwrap();
        let nsec = test_nsec();

        let res = send(
            &app,
            post_json(
                "/api/auth/setup",
                json!({
                    "nsec": nsec,
                    "relay_url": "wss://relay.example.com"
                }),
            ),
        )
        .await;
        assert_eq!(res.status(), StatusCode::OK);
        let body = json_body(res).await;
        assert_eq!(body["unlocked"], true);
        assert_eq!(body["passphrase_set"], false);
        assert_eq!(body["has_state"], true);

        let stored: PersistentState =
            serde_json::from_slice(&std::fs::read(&state_path).unwrap()).unwrap();
        assert!(stored.sealed.is_none());
        assert_eq!(stored.nsec.as_deref(), Some(nsec.as_str()));

        let status = send(
            &app,
            Request::builder()
                .uri("/api/auth/status")
                .body(Body::empty())
                .unwrap(),
        )
        .await;
        let status_body = json_body(status).await;
        assert_eq!(status_body["unlocked"], true);
        assert_eq!(status_body["passphrase_set"], false);
    }

    #[tokio::test]
    async fn wipe_requires_delete_only() {
        let (cfg, _guard) = test_config();
        let app = build_router(cfg).await.unwrap();
        let nsec = test_nsec();
        let setup = send(
            &app,
            post_json(
                "/api/auth/setup",
                json!({
                    "nsec": nsec,
                    "relay_url": "wss://relay.example.com"
                }),
            ),
        )
        .await;
        assert_eq!(setup.status(), StatusCode::OK);

        let denied = send(
            &app,
            post_json("/api/auth/wipe", json!({ "confirmation": "please" })),
        )
        .await;
        assert_eq!(denied.status(), StatusCode::BAD_REQUEST);

        let wiped = send(
            &app,
            post_json("/api/auth/wipe", json!({ "confirmation": "DELETE" })),
        )
        .await;
        assert_eq!(wiped.status(), StatusCode::OK);
        let status = json_body(
            send(
                &app,
                Request::builder()
                    .uri("/api/auth/status")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await,
        )
        .await;
        assert_eq!(status["has_state"], false);
    }

    #[tokio::test]
    async fn recover_same_identity_can_set_or_clear_passphrase() {
        let (cfg, _guard) = test_config();
        let app = build_router(cfg).await.unwrap();
        let nsec = test_nsec();
        let setup = send(
            &app,
            post_json(
                "/api/auth/setup",
                json!({
                    "nsec": nsec,
                    "passphrase": "long-enough-pass",
                    "relay_url": "wss://relay.example.com"
                }),
            ),
        )
        .await;
        assert_eq!(setup.status(), StatusCode::OK);
        let _ = send(
            &app,
            Request::builder()
                .method("POST")
                .uri("/api/auth/lock")
                .body(Body::empty())
                .unwrap(),
        )
        .await;

        let wrong = send(
            &app,
            post_json("/api/auth/recover", json!({ "nsec": test_nsec() })),
        )
        .await;
        assert_eq!(wrong.status(), StatusCode::BAD_REQUEST);

        let recovered = send(
            &app,
            post_json("/api/auth/recover", json!({ "nsec": nsec })),
        )
        .await;
        assert_eq!(recovered.status(), StatusCode::OK);
        let body = json_body(recovered).await;
        assert_eq!(body["unlocked"], true);
        assert_eq!(body["passphrase_set"], false);
    }

    #[tokio::test]
    async fn recover_is_rate_limited() {
        let (cfg, _guard) = test_config();
        let app_state_cfg = cfg.clone();
        let app = {
            let persistent = PersistentState::load(&app_state_cfg.state_path()).unwrap();
            let nsec = test_nsec();
            let (_, identity) = parse_nsec(&nsec).unwrap();
            let mut p = persistent;
            persist_nsec(&mut p, &nsec, Some("long-enough-pass"), &identity).unwrap();
            p.save(&app_state_cfg.state_path()).unwrap();
            let sessions = SessionStore::new(Duration::from_secs(60));
            let cookie_key = Key::from(&app_state_cfg.cookie_signing_key);
            let state = AppState {
                cfg: app_state_cfg.clone(),
                sessions,
                persistent: Arc::new(Mutex::new(p)),
                cookie_key,
                outbox: Outbox::new(),
                recovery_limiter: AttemptLimiter::new(2, Duration::from_secs(60)),
            };
            Router::new()
                .nest(
                    "/api",
                    Router::new().route("/auth/recover", post(auth_recover)),
                )
                .with_state(state)
        };

        let first = send(
            &app,
            post_json("/api/auth/recover", json!({ "nsec": test_nsec() })),
        )
        .await;
        assert_eq!(first.status(), StatusCode::BAD_REQUEST);
        let second = send(
            &app,
            post_json("/api/auth/recover", json!({ "nsec": test_nsec() })),
        )
        .await;
        assert_eq!(second.status(), StatusCode::BAD_REQUEST);
        let third = send(
            &app,
            post_json("/api/auth/recover", json!({ "nsec": test_nsec() })),
        )
        .await;
        assert_eq!(third.status(), StatusCode::TOO_MANY_REQUESTS);
    }

    #[tokio::test]
    async fn existing_sealed_blob_is_not_auto_unlocked() {
        let (cfg, _guard) = test_config();
        let nsec = test_nsec();
        let (_, identity) = parse_nsec(&nsec).unwrap();
        let mut p = PersistentState::default();
        persist_nsec(&mut p, &nsec, Some("long-enough-pass"), &identity).unwrap();
        p.save(&cfg.state_path()).unwrap();

        let app = build_router(cfg).await.unwrap();
        let status = json_body(
            send(
                &app,
                Request::builder()
                    .uri("/api/auth/status")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await,
        )
        .await;
        assert_eq!(status["has_state"], true);
        assert_eq!(status["passphrase_set"], true);
        assert_eq!(status["unlocked"], false);
    }
}
