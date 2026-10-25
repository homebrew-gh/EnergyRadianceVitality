//! AI coach settings and Maple Proxy connection test.
//!
//! The browser never talks to Maple. These routes store settings and probe
//! `{base}/health` plus `{base}/v1/models`. Chat completions are a later milestone.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use axum::{extract::State, Json};
use axum_extra::extract::SignedCookieJar;
use serde::{Deserialize, Serialize};

use crate::config::detected_ai_endpoints;
use crate::crypto::{mask_secret, open_ai_key, seal_ai_key};
use crate::error::{AppError, AppResult};
use crate::routes::AppState;
use crate::session::SESSION_COOKIE;
use crate::state::{AiSettings, SealedAiKey};

const PROBE_TIMEOUT: Duration = Duration::from_secs(8);
const PROXY_DOWN: &str = "proxy not running — start Maple Proxy on StartOS";
const KEY_REJECTED: &str = "key missing or rejected";
const PLAN_GATED: &str = "Maple credits exhausted / plan-gated model";

#[derive(Debug, Clone, Serialize)]
pub struct AiSettingsResponse {
    pub enabled: bool,
    pub provider: String,
    pub base_url: String,
    pub key_mode: String,
    pub api_key_masked: Option<String>,
    pub has_sealed_api_key: bool,
    pub analysis_model_id: String,
    pub generation_model_id: String,
    pub context_level: String,
    pub always_show_context_preview: bool,
    pub timeout_seconds: u64,
    pub detected_endpoints: Vec<crate::config::DetectedAiEndpoint>,
}

#[derive(Debug, Deserialize)]
pub struct AiSettingsUpdate {
    pub enabled: bool,
    pub provider: String,
    #[serde(default)]
    pub base_url: String,
    pub key_mode: String,
    /// Write-only. Empty means keep the stored key.
    #[serde(default)]
    pub api_key: String,
    #[serde(default)]
    pub clear_api_key: bool,
    #[serde(default)]
    pub analysis_model_id: String,
    #[serde(default)]
    pub generation_model_id: String,
    pub context_level: String,
    pub always_show_context_preview: bool,
}

#[derive(Debug, Deserialize)]
pub struct AiTestRequest {
    pub base_url: String,
    pub key_mode: String,
    /// Used when non-empty. Otherwise the stored sealed key is opened.
    #[serde(default)]
    pub api_key: String,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct AiTestResponse {
    pub reachable: bool,
    pub health_ok: bool,
    pub auth_ok: bool,
    pub models: Vec<String>,
    pub error: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct AiModelsResponse {
    pub models: Vec<String>,
    pub error: Option<String>,
}

pub async fn get_ai_settings(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<AiSettingsResponse>> {
    let nsec = require_secret(&s, &jar).await?;
    let settings = s.persistent.lock().await.ai.clone();
    Ok(Json(settings_response(&settings, Some(nsec.as_slice()))))
}

pub async fn put_ai_settings(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<AiSettingsUpdate>,
) -> AppResult<Json<AiSettingsResponse>> {
    let nsec = require_secret(&s, &jar).await?;
    let mut next = validate_update(&body)?;
    let mut persistent = s.persistent.lock().await;
    next.timeout_seconds = persistent.ai.timeout_seconds;
    next.last_models_refresh_epoch_seconds = persistent.ai.last_models_refresh_epoch_seconds;
    apply_key_update(&mut next, &persistent.ai, nsec.as_slice(), &body)?;
    persistent.ai = next;
    persistent
        .save(&s.cfg.state_path())
        .map_err(AppError::Internal)?;
    let response = settings_response(&persistent.ai, Some(nsec.as_slice()));
    drop(persistent);
    Ok(Json(response))
}

pub async fn test_ai_connection(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<AiTestRequest>,
) -> AppResult<Json<AiTestResponse>> {
    let nsec = require_secret(&s, &jar).await?;
    let base_url = normalize_ai_base_url(&body.base_url).map_err(AppError::BadRequest)?;
    let key_mode = parse_key_mode(&body.key_mode).map_err(AppError::BadRequest)?;
    let stored_key = if body.api_key.trim().is_empty() && key_mode == "ERV_SEALED" {
        let settings = s.persistent.lock().await.ai.clone();
        open_stored_key(&settings, nsec.as_slice())
    } else {
        None
    };
    let explicit = body.api_key.trim();
    let key = if explicit.is_empty() {
        stored_key.as_deref()
    } else {
        Some(explicit)
    };
    if key_mode == "ERV_SEALED" && key.unwrap_or("").is_empty() {
        return Ok(Json(AiTestResponse {
            reachable: false,
            health_ok: false,
            auth_ok: false,
            models: Vec::new(),
            error: Some(KEY_REJECTED.to_string()),
        }));
    }
    let report = probe_ai_endpoint(&s.http, &base_url, &key_mode, key, PROBE_TIMEOUT).await;
    if report.auth_ok {
        remember_models(&s, &report.models).await;
        let mut persistent = s.persistent.lock().await;
        persistent.ai.last_models_refresh_epoch_seconds = epoch_now();
        let _ = persistent.save(&s.cfg.state_path());
    }
    Ok(Json(report))
}

pub async fn get_ai_models(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<AiModelsResponse>> {
    let nsec = require_secret(&s, &jar).await?;
    let settings = s.persistent.lock().await.ai.clone();
    let base_url = normalize_ai_base_url(&settings.base_url).map_err(AppError::BadRequest)?;
    let key = if settings.key_mode == "ERV_SEALED" {
        open_stored_key(&settings, nsec.as_slice())
    } else {
        None
    };
    let report = probe_ai_endpoint(
        &s.http,
        &base_url,
        &settings.key_mode,
        key.as_deref(),
        PROBE_TIMEOUT,
    )
    .await;
    if report.auth_ok {
        remember_models(&s, &report.models).await;
        let mut persistent = s.persistent.lock().await;
        persistent.ai.last_models_refresh_epoch_seconds = epoch_now();
        let _ = persistent.save(&s.cfg.state_path());
    }
    Ok(Json(AiModelsResponse {
        models: report.models,
        error: report.error,
    }))
}

fn settings_response(settings: &AiSettings, nsec: Option<&[u8]>) -> AiSettingsResponse {
    let opened = nsec.and_then(|secret| open_stored_key(settings, secret));
    AiSettingsResponse {
        enabled: settings.enabled,
        provider: settings.provider.clone(),
        base_url: settings.base_url.clone(),
        key_mode: settings.key_mode.clone(),
        api_key_masked: opened.as_deref().map(mask_secret),
        has_sealed_api_key: settings.sealed_api_key.is_some(),
        analysis_model_id: settings.analysis_model_id.clone(),
        generation_model_id: settings.generation_model_id.clone(),
        context_level: settings.context_level.clone(),
        always_show_context_preview: settings.always_show_context_preview,
        timeout_seconds: settings.timeout_seconds,
        detected_endpoints: detected_ai_endpoints(),
    }
}

fn apply_key_update(
    next: &mut AiSettings,
    previous: &AiSettings,
    nsec: &[u8],
    body: &AiSettingsUpdate,
) -> AppResult<()> {
    if body.clear_api_key || next.key_mode == "PROXY_HELD" {
        next.sealed_api_key = None;
        return Ok(());
    }
    let submitted = body.api_key.trim();
    if !submitted.is_empty() {
        reject_secret_key(submitted)?;
        let seal = seal_ai_key(nsec, submitted).map_err(AppError::Internal)?;
        next.sealed_api_key = Some(SealedAiKey::from_seal(&seal));
        return Ok(());
    }
    next.sealed_api_key = previous.sealed_api_key.clone();
    if next.enabled && next.sealed_api_key.is_none() {
        return Err(AppError::BadRequest(
            "Enter an API key, or leave the key in Maple Proxy.".into(),
        ));
    }
    Ok(())
}

fn validate_update(body: &AiSettingsUpdate) -> AppResult<AiSettings> {
    let provider = parse_provider(&body.provider)?;
    let key_mode = parse_key_mode(&body.key_mode).map_err(AppError::BadRequest)?;
    let context_level = parse_context_level(&body.context_level)?;
    let base_url = if body.base_url.trim().is_empty() {
        String::new()
    } else {
        normalize_ai_base_url(&body.base_url).map_err(AppError::BadRequest)?
    };
    if body.enabled && base_url.is_empty() {
        return Err(AppError::BadRequest(
            "Choose Maple Proxy or enter a server URL before enabling the coach.".into(),
        ));
    }
    if body.enabled && provider == "OFF" {
        return Err(AppError::BadRequest(
            "Choose Maple Proxy or a custom server before enabling the coach.".into(),
        ));
    }
    Ok(AiSettings {
        enabled: body.enabled,
        provider,
        base_url,
        key_mode,
        sealed_api_key: None,
        analysis_model_id: clean_model_id(&body.analysis_model_id)?,
        generation_model_id: clean_model_id(&body.generation_model_id)?,
        context_level,
        always_show_context_preview: body.always_show_context_preview,
        timeout_seconds: 120,
        last_models_refresh_epoch_seconds: 0,
    })
}

pub fn normalize_ai_base_url(raw: &str) -> Result<String, String> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err("Enter an http or https URL.".into());
    }
    let url =
        reqwest::Url::parse(trimmed).map_err(|_| "Enter an http or https URL.".to_string())?;
    if url.scheme() != "http" && url.scheme() != "https" {
        return Err("Enter an http or https URL.".into());
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err("Put the API key in the key field, not the URL.".into());
    }
    if url.host_str().unwrap_or("").is_empty() {
        return Err("Enter an http or https URL.".into());
    }
    let mut normalized = url;
    let path = normalized.path().trim_end_matches('/').to_string();
    if path == "/v1" {
        normalized.set_path("");
    }
    let mut text = normalized.to_string();
    if text.ends_with('/') {
        text.pop();
    }
    Ok(text)
}

fn parse_provider(raw: &str) -> Result<String, AppError> {
    match raw.trim() {
        "OFF" | "MAPLE" | "OPENAI_COMPAT" => Ok(raw.trim().to_string()),
        _ => Err(AppError::BadRequest("Unknown AI provider.".into())),
    }
}

fn parse_key_mode(raw: &str) -> Result<String, String> {
    match raw.trim() {
        "PROXY_HELD" | "ERV_SEALED" => Ok(raw.trim().to_string()),
        _ => Err("Unknown API key mode.".into()),
    }
}

fn parse_context_level(raw: &str) -> Result<String, AppError> {
    match raw.trim() {
        "MINIMAL" | "STANDARD" | "FULL" => Ok(raw.trim().to_string()),
        _ => Err(AppError::BadRequest("Unknown context level.".into())),
    }
}

fn clean_model_id(raw: &str) -> Result<String, AppError> {
    let trimmed = raw.trim();
    if trimmed
        .chars()
        .any(|ch| ch.is_control() || ch.is_whitespace())
    {
        return Err(AppError::BadRequest(
            "Model id cannot contain spaces.".into(),
        ));
    }
    if trimmed.len() > 128 {
        return Err(AppError::BadRequest("Model id is too long.".into()));
    }
    Ok(trimmed.to_string())
}

fn reject_secret_key(api_key: &str) -> AppResult<()> {
    if api_key.starts_with("nsec1") || api_key.starts_with("npub1") {
        return Err(AppError::BadRequest(
            "That value looks like a Nostr key. Enter the Maple API key instead.".into(),
        ));
    }
    if api_key.len() > 512 {
        return Err(AppError::BadRequest("API key is too long.".into()));
    }
    Ok(())
}

pub(crate) fn open_stored_key(settings: &AiSettings, nsec: &[u8]) -> Option<String> {
    let sealed = settings.sealed_api_key.as_ref()?;
    let seal = sealed.to_seal().ok()?;
    open_ai_key(nsec, &seal).ok().map(|value| value.to_string())
}

pub async fn probe_ai_endpoint(
    client: &reqwest::Client,
    base_url: &str,
    key_mode: &str,
    api_key: Option<&str>,
    timeout: Duration,
) -> AiTestResponse {
    let health_url = format!("{base_url}/health");
    let health = client.get(&health_url).timeout(timeout).send().await;
    let health_status = match health {
        Ok(response) => response.status().as_u16(),
        Err(_) => {
            return AiTestResponse {
                reachable: false,
                health_ok: false,
                auth_ok: false,
                models: Vec::new(),
                error: Some(PROXY_DOWN.to_string()),
            };
        }
    };
    if !(200..300).contains(&health_status) {
        return AiTestResponse {
            reachable: true,
            health_ok: false,
            auth_ok: false,
            models: Vec::new(),
            error: Some(status_message(health_status)),
        };
    }

    if key_mode == "ERV_SEALED" && api_key.unwrap_or("").trim().is_empty() {
        return AiTestResponse {
            reachable: true,
            health_ok: true,
            auth_ok: false,
            models: Vec::new(),
            error: Some(KEY_REJECTED.to_string()),
        };
    }

    let models_url = format!("{base_url}/v1/models");
    let mut request = client.get(&models_url).timeout(timeout);
    if key_mode == "ERV_SEALED" {
        if let Some(key) = api_key {
            request = request.bearer_auth(key);
        }
    }
    match request.send().await {
        Ok(response) => {
            let status = response.status().as_u16();
            if !(200..300).contains(&status) {
                return AiTestResponse {
                    reachable: true,
                    health_ok: true,
                    auth_ok: false,
                    models: Vec::new(),
                    error: Some(status_message(status)),
                };
            }
            match response.json::<ModelsBody>().await {
                Ok(body) => AiTestResponse {
                    reachable: true,
                    health_ok: true,
                    auth_ok: true,
                    models: body
                        .data
                        .into_iter()
                        .map(|row| row.id)
                        .filter(|id| !id.is_empty())
                        .collect(),
                    error: None,
                },
                Err(_) => AiTestResponse {
                    reachable: true,
                    health_ok: true,
                    auth_ok: false,
                    models: Vec::new(),
                    error: Some("Maple Proxy returned a model list ERV could not read.".into()),
                },
            }
        }
        Err(_) => AiTestResponse {
            reachable: true,
            health_ok: true,
            auth_ok: false,
            models: Vec::new(),
            error: Some(PROXY_DOWN.to_string()),
        },
    }
}

pub fn status_message(status: u16) -> String {
    match status {
        401 | 403 => KEY_REJECTED.to_string(),
        402 => PLAN_GATED.to_string(),
        other => format!("Maple Proxy returned {other}"),
    }
}

#[derive(Deserialize)]
struct ModelsBody {
    #[serde(default)]
    data: Vec<ModelRow>,
}

#[derive(Deserialize)]
struct ModelRow {
    id: String,
}

pub(crate) async fn require_secret(
    s: &AppState,
    jar: &SignedCookieJar,
) -> AppResult<zeroize::Zeroizing<Vec<u8>>> {
    let sid = jar.get(SESSION_COOKIE).ok_or(AppError::Unauthorized)?;
    s.sessions
        .with_secret(sid.value(), |bytes| zeroize::Zeroizing::new(bytes.to_vec()))
        .await
        .ok_or(AppError::Unauthorized)
}

pub(crate) async fn remember_models(s: &AppState, models: &[String]) {
    if models.is_empty() {
        return;
    }
    *s.model_cache.lock().await = models.to_vec();
}

fn epoch_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::{open_ai_key, seal_ai_key};
    use axum::{routing::get, Router};
    use tokio::net::TcpListener;

    #[test]
    fn masks_only_the_last_four() {
        assert_eq!(mask_secret("maple-secret-key-abcd"), "…abcd");
        assert_eq!(mask_secret("ab"), "••••");
    }

    #[test]
    fn sealed_key_round_trip_uses_nsec() {
        let nsec = b"nsec1example-session-secret";
        let seal = seal_ai_key(nsec, "maple-secret-key-abcd").unwrap();
        let opened = open_ai_key(nsec, &seal).unwrap();
        assert_eq!(opened.as_str(), "maple-secret-key-abcd");
        assert!(open_ai_key(b"other-secret", &seal).is_err());
    }

    #[test]
    fn status_messages_match_maple_errors() {
        assert_eq!(status_message(401), KEY_REJECTED);
        assert_eq!(status_message(403), KEY_REJECTED);
        assert_eq!(status_message(402), PLAN_GATED);
    }

    #[test]
    fn strips_v1_suffix_from_base_url() {
        assert_eq!(
            normalize_ai_base_url("http://maple-proxy.startos:8080/v1/").unwrap(),
            "http://maple-proxy.startos:8080"
        );
    }

    #[tokio::test]
    async fn probe_maps_unauthorized_and_payment_required() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let app = Router::new()
            .route("/health", get(|| async { axum::http::StatusCode::OK }))
            .route(
                "/v1/models",
                get(|| async { axum::http::StatusCode::UNAUTHORIZED }),
            );
        tokio::spawn(async move {
            let _ = axum::serve(listener, app).await;
        });
        let client = reqwest::Client::new();
        let denied = probe_ai_endpoint(
            &client,
            &format!("http://{addr}"),
            "PROXY_HELD",
            None,
            Duration::from_secs(2),
        )
        .await;
        assert!(denied.reachable && denied.health_ok && !denied.auth_ok);
        assert_eq!(denied.error.as_deref(), Some(KEY_REJECTED));

        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let app = Router::new()
            .route("/health", get(|| async { axum::http::StatusCode::OK }))
            .route(
                "/v1/models",
                get(|| async { axum::http::StatusCode::PAYMENT_REQUIRED }),
            );
        tokio::spawn(async move {
            let _ = axum::serve(listener, app).await;
        });
        let gated = probe_ai_endpoint(
            &client,
            &format!("http://{addr}"),
            "PROXY_HELD",
            None,
            Duration::from_secs(2),
        )
        .await;
        assert_eq!(gated.error.as_deref(), Some(PLAN_GATED));
    }

    #[tokio::test]
    async fn probe_maps_connection_failure() {
        let client = reqwest::Client::new();
        let report = probe_ai_endpoint(
            &client,
            "http://127.0.0.1:1",
            "PROXY_HELD",
            None,
            Duration::from_millis(300),
        )
        .await;
        assert!(!report.reachable);
        assert_eq!(report.error.as_deref(), Some(PROXY_DOWN));
    }

    #[test]
    fn ai_settings_round_trip_on_disk() {
        let dir = std::env::temp_dir().join(format!("erv-ai-settings-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("state.json");
        let mut state = crate::state::PersistentState::default();
        state.ai.enabled = true;
        state.ai.provider = "MAPLE".into();
        state.ai.base_url = "http://maple-proxy.startos:8080".into();
        state.ai.analysis_model_id = "gemma4-31b".into();
        state.ai.generation_model_id = "llama3-3-70b".into();
        state.save(&path).unwrap();
        let loaded = crate::state::PersistentState::load(&path).unwrap();
        assert!(loaded.ai.enabled);
        assert_eq!(loaded.ai.provider, "MAPLE");
        assert_eq!(loaded.ai.base_url, "http://maple-proxy.startos:8080");
        assert_eq!(loaded.ai.analysis_model_id, "gemma4-31b");
        assert_eq!(loaded.ai.generation_model_id, "llama3-3-70b");
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn older_state_without_ai_defaults_off() {
        let raw = r#"{"v":1,"sealed":null,"relay_url":null,"npub":null}"#;
        let state: crate::state::PersistentState = serde_json::from_str(raw).unwrap();
        assert!(!state.ai.enabled);
        assert_eq!(state.ai.provider, "OFF");
        assert!(state.ai.always_show_context_preview);
    }
}
