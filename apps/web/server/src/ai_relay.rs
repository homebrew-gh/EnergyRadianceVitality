//! Chat relay. The browser posts context; the server adds the system prompt and calls Maple.

use std::time::{Duration, Instant};

use axum::extract::State;
use axum::response::sse::{Event, Sse};
use axum::Json;
use axum_extra::extract::SignedCookieJar;
use futures_util::{Stream, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::ai_prompts::{system_prompt, user_message, PROMPT_VERSION};
use crate::ai_provider::{
    chat_complete, open_chat_stream, ChatCall, SseDecoder, StreamPiece, UpstreamFailure,
};
use crate::ai_queue::Flight;
use crate::ai_routes::{open_stored_key, remember_models, require_secret};
use crate::error::{AppError, AppResult};
use crate::routes::AppState;
use crate::state::AiSettings;

const MAX_CONTEXT_BYTES: usize = 64 * 1024;
const MAX_SUBJECT_BYTES: usize = 64 * 1024;
const MAX_USER_PROMPT_CHARS: usize = 2_000;

const CONTEXT_KEYS: &[&str] = &[
    "ervTrainingContextVersion",
    "exportedAtEpochSeconds",
    "profile",
    "progressionGuardrails",
    "snapshot",
    "equipment",
    "savedWorkouts",
    "savedWeightRoutines",
    "savedCardioRoutineNames",
    "savedStretchRoutineNames",
    "customExerciseNames",
    "contextLevel",
    "periodWeeks",
    "recentSessionSummaries",
    "recentExerciseRows",
];

#[derive(Debug, Deserialize)]
pub struct AiCompletionRequest {
    pub task: String,
    #[serde(default)]
    pub model_id: String,
    pub context: Value,
    #[serde(default)]
    pub subject: Option<Value>,
    #[serde(default)]
    pub user_prompt: String,
    #[serde(default)]
    pub response_format: String,
}

#[derive(Debug, Serialize)]
pub struct AiCompletionResponse {
    pub content: String,
    pub model: String,
    pub prompt_version: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub usage: Option<AiUsage>,
}

#[derive(Debug, Serialize)]
pub struct AiUsage {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub prompt_tokens: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub completion_tokens: Option<u64>,
}

#[derive(Debug, Serialize)]
pub struct AiQueueStatusResponse {
    pub busy: bool,
    pub queued: u32,
    pub last_error: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct CancelResponse {
    cancelled: bool,
}

pub async fn complete_ai(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<AiCompletionRequest>,
) -> AppResult<Json<AiCompletionResponse>> {
    let prepared = prepare_call(&s, &jar, &body).await?;
    let mut flight = begin_flight(&s).await?;
    let started = Instant::now();
    let logged_model = prepared.call.model.clone();
    let result = tokio::select! {
        biased;
        _ = flight.cancelled() => Err(UpstreamFailure::Status(499)),
        completion = chat_complete(&s.http, prepared.call) => completion,
    };
    let elapsed_ms = started.elapsed().as_millis() as u64;
    match result {
        Ok(completion) => {
            tracing::info!(
                task = %prepared.task,
                model = %completion.model,
                elapsed_ms,
                outcome = "ok",
                "ai completion"
            );
            s.ai_queue.finish(flight.generation, None).await;
            Ok(Json(AiCompletionResponse {
                content: completion.content,
                model: completion.model,
                prompt_version: PROMPT_VERSION,
                usage: Some(AiUsage {
                    prompt_tokens: completion.prompt_tokens,
                    completion_tokens: completion.completion_tokens,
                }),
            }))
        }
        Err(failure) => {
            let message = if matches!(failure, UpstreamFailure::Status(499)) {
                "Request cancelled.".to_string()
            } else {
                failure.message()
            };
            tracing::info!(
                task = %prepared.task,
                model = %logged_model,
                elapsed_ms,
                outcome = "error",
                "ai completion"
            );
            s.ai_queue
                .finish(flight.generation, Some(message.clone()))
                .await;
            Err(AppError::BadRequest(message))
        }
    }
}

pub async fn stream_ai(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<AiCompletionRequest>,
) -> AppResult<Sse<impl Stream<Item = Result<Event, std::convert::Infallible>>>> {
    let prepared = prepare_call(&s, &jar, &body).await?;
    let flight = begin_flight(&s).await?;
    let (tx, rx) = tokio::sync::mpsc::channel(16);
    let queue = s.ai_queue.clone();
    let http = s.http.clone();
    let generation = flight.generation;
    let cancel = flight;
    let task = prepared.task.clone();
    let model = prepared.call.model.clone();
    tokio::spawn(async move {
        let started = Instant::now();
        let mut last_error = None;
        match open_chat_stream(&http, prepared.call).await {
            Ok(response) => {
                let mut decoder = SseDecoder::default();
                let mut body = response.bytes_stream();
                while let Some(chunk) = body.next().await {
                    if cancel.is_cancelled() {
                        last_error = Some("Request cancelled.".to_string());
                        break;
                    }
                    let bytes = match chunk {
                        Ok(bytes) => bytes,
                        Err(_) => {
                            last_error = Some(UpstreamFailure::Down.message());
                            break;
                        }
                    };
                    let text = String::from_utf8_lossy(&bytes);
                    let mut finished = false;
                    for piece in decoder.push(&text) {
                        match piece {
                            StreamPiece::Text(delta) => {
                                if tx
                                    .send(Event::default().event("delta").data(delta))
                                    .await
                                    .is_err()
                                {
                                    finished = true;
                                    break;
                                }
                            }
                            StreamPiece::Done => {
                                finished = true;
                                break;
                            }
                        }
                    }
                    if finished {
                        break;
                    }
                }
            }
            Err(failure) => {
                last_error = Some(failure.message());
            }
        }
        if let Some(message) = last_error.clone() {
            let _ = tx.send(Event::default().event("error").data(message)).await;
        } else {
            let _ = tx
                .send(
                    Event::default().event("done").data(
                        serde_json::json!({
                            "model": model,
                            "promptVersion": PROMPT_VERSION,
                        })
                        .to_string(),
                    ),
                )
                .await;
        }
        tracing::info!(
            task = %task,
            elapsed_ms = started.elapsed().as_millis() as u64,
            outcome = if last_error.is_none() { "ok" } else { "error" },
            "ai stream"
        );
        queue.finish(generation, last_error).await;
    });
    let stream = futures_util::stream::unfold(rx, |mut rx| async move {
        let event = rx.recv().await?;
        Some((Ok(event), rx))
    });
    Ok(Sse::new(stream))
}

pub async fn cancel_ai(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<CancelResponse>> {
    require_secret(&s, &jar).await?;
    Ok(Json(CancelResponse {
        cancelled: s.ai_queue.cancel().await,
    }))
}

pub async fn ai_status(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<AiQueueStatusResponse>> {
    require_secret(&s, &jar).await?;
    let status = s.ai_queue.status().await;
    Ok(Json(AiQueueStatusResponse {
        busy: status.busy,
        queued: status.queued,
        last_error: status.last_error,
    }))
}

struct PreparedCall {
    task: String,
    call: ChatCall,
}

async fn prepare_call(
    s: &AppState,
    jar: &SignedCookieJar,
    body: &AiCompletionRequest,
) -> AppResult<PreparedCall> {
    let nsec = require_secret(s, jar).await?;
    let settings = s.persistent.lock().await.ai.clone();
    if !settings.enabled {
        return Err(AppError::BadRequest(
            "Enable the AI coach in Settings before sending a request.".into(),
        ));
    }
    let task = parse_task(&body.task)?;
    let json_object = parse_response_format(&body.response_format, task)?;
    validate_context(&body.context).map_err(AppError::BadRequest)?;
    validate_subject(task, body.subject.as_ref())?;
    validate_user_prompt(&body.user_prompt)?;
    let model = resolve_model(&settings, task, &body.model_id);
    if model.is_empty() {
        return Err(AppError::BadRequest(
            "Choose an analysis or generation model in Settings.".into(),
        ));
    }
    ensure_model_allowed(s, &settings, nsec.as_slice(), &model).await?;
    let base_url = crate::ai_routes::normalize_ai_base_url(&settings.base_url)
        .map_err(AppError::BadRequest)?;
    let api_key = if settings.key_mode == "ERV_SEALED" {
        Some(
            open_stored_key(&settings, nsec.as_slice())
                .ok_or_else(|| AppError::BadRequest("key missing or rejected".into()))?,
        )
    } else {
        None
    };
    let timeout = Duration::from_secs(settings.timeout_seconds.clamp(10, 180));
    Ok(PreparedCall {
        task: task.to_string(),
        call: ChatCall {
            base_url,
            api_key,
            model,
            system: system_prompt(task, json_object),
            user: user_message(
                &body.context,
                body.subject.as_ref(),
                body.user_prompt.trim(),
            ),
            json_object,
            timeout,
        },
    })
}

async fn begin_flight(s: &AppState) -> AppResult<Flight> {
    s.ai_queue
        .try_begin()
        .await
        .map_err(|_| AppError::Conflict("AI coach is busy with another request.".into()))
}

async fn ensure_model_allowed(
    s: &AppState,
    settings: &AiSettings,
    nsec: &[u8],
    model: &str,
) -> AppResult<()> {
    if model_cached(s, model).await {
        return Ok(());
    }
    let base_url = crate::ai_routes::normalize_ai_base_url(&settings.base_url)
        .map_err(AppError::BadRequest)?;
    let key = if settings.key_mode == "ERV_SEALED" {
        open_stored_key(settings, nsec)
    } else {
        None
    };
    let report = crate::ai_routes::probe_ai_endpoint(
        &s.http,
        &base_url,
        &settings.key_mode,
        key.as_deref(),
        Duration::from_secs(8),
    )
    .await;
    if !report.models.is_empty() {
        remember_models(s, &report.models).await;
    }
    if report.models.iter().any(|id| id == model) {
        return Ok(());
    }
    if let Some(error) = report.error {
        return Err(AppError::BadRequest(error));
    }
    Err(AppError::BadRequest(
        "That model is not available from the proxy. Test the connection and pick a listed model."
            .into(),
    ))
}

async fn model_cached(s: &AppState, model: &str) -> bool {
    s.model_cache.lock().await.iter().any(|id| id == model)
}

pub fn validate_context(context: &Value) -> Result<(), String> {
    let Some(object) = context.as_object() else {
        return Err("Context must be a JSON object.".into());
    };
    reject_unknown_keys(object, CONTEXT_KEYS, "context")?;
    match object
        .get("ervTrainingContextVersion")
        .and_then(Value::as_u64)
    {
        Some(1) => {}
        _ => return Err("Context must set ervTrainingContextVersion to 1.".into()),
    }
    let bytes =
        serde_json::to_vec(context).map_err(|_| "Context could not be measured.".to_string())?;
    if bytes.len() > MAX_CONTEXT_BYTES {
        return Err(format!(
            "Context is {} bytes. The limit is {MAX_CONTEXT_BYTES}.",
            bytes.len()
        ));
    }
    redact_value(context)?;
    Ok(())
}

fn validate_subject(task: &str, subject: Option<&Value>) -> Result<(), AppError> {
    if task == "workout_suggest_changes" && subject.is_none() {
        return Err(AppError::BadRequest(
            "Suggesting changes needs the current workout in subject.".into(),
        ));
    }
    let Some(subject) = subject else {
        return Ok(());
    };
    let Some(object) = subject.as_object() else {
        return Err(AppError::BadRequest(
            "Subject must be a JSON object.".into(),
        ));
    };
    reject_unknown_keys(object, &["workout"], "subject").map_err(AppError::BadRequest)?;
    let bytes = serde_json::to_vec(subject)
        .map(|value| value.len())
        .unwrap_or(usize::MAX);
    if bytes > MAX_SUBJECT_BYTES {
        return Err(AppError::BadRequest(format!(
            "Subject is {bytes} bytes. The limit is {MAX_SUBJECT_BYTES}."
        )));
    }
    redact_value(subject).map_err(AppError::BadRequest)
}

fn validate_user_prompt(prompt: &str) -> Result<(), AppError> {
    if prompt.chars().count() > MAX_USER_PROMPT_CHARS {
        return Err(AppError::BadRequest(format!(
            "The message is longer than {MAX_USER_PROMPT_CHARS} characters."
        )));
    }
    redact_text(prompt).map_err(AppError::BadRequest)
}

fn reject_unknown_keys(
    object: &Map<String, Value>,
    allowed: &[&str],
    label: &str,
) -> Result<(), String> {
    for key in object.keys() {
        if !allowed.contains(&key.as_str()) {
            return Err(format!("Unknown {label} field \"{key}\"."));
        }
    }
    Ok(())
}

fn redact_value(value: &Value) -> Result<(), String> {
    match value {
        Value::String(text) => redact_text(text),
        Value::Array(items) => {
            for item in items {
                redact_value(item)?;
            }
            Ok(())
        }
        Value::Object(map) => {
            for child in map.values() {
                redact_value(child)?;
            }
            Ok(())
        }
        _ => Ok(()),
    }
}

pub fn redact_text(text: &str) -> Result<(), String> {
    let lower = text.to_ascii_lowercase();
    if lower.contains("nsec1") {
        return Err("That text includes a secret key and was not sent.".into());
    }
    if lower.contains("npub1") {
        return Err("That text includes a public key and was not sent.".into());
    }
    if lower.contains("wss://") || lower.contains("ws://") {
        return Err("That text includes a relay URL and was not sent.".into());
    }
    if contains_media_url(&lower) {
        return Err("That text includes a media URL and was not sent.".into());
    }
    Ok(())
}

fn contains_media_url(lower: &str) -> bool {
    for marker in ["http://", "https://"] {
        let mut rest = lower;
        while let Some(index) = rest.find(marker) {
            let url = &rest[index..];
            let end = url
                .find(|ch: char| ch.is_whitespace() || ch == '"' || ch == '\'')
                .unwrap_or(url.len());
            if looks_like_media(&url[..end]) {
                return true;
            }
            rest = &rest[index + marker.len()..];
        }
    }
    false
}

fn looks_like_media(url: &str) -> bool {
    let path = url.split('?').next().unwrap_or(url);
    const EXTENSIONS: &[&str] = &[
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp4", ".mov", ".webm", ".m4a", ".mp3", ".wav",
    ];
    EXTENSIONS.iter().any(|ext| path.ends_with(ext))
        || url.contains("/blossom")
        || url.contains("/blob/")
}

fn parse_task(task: &str) -> Result<&str, AppError> {
    match task.trim() {
        "analysis" | "workout_suggest_changes" | "workout_generate" | "plan_suggest" => {
            Ok(task.trim())
        }
        _ => Err(AppError::BadRequest("Unknown AI task.".into())),
    }
}

fn parse_response_format(raw: &str, task: &str) -> Result<bool, AppError> {
    let format = if raw.trim().is_empty() {
        if task == "analysis" {
            "markdown"
        } else {
            "json"
        }
    } else {
        raw.trim()
    };
    match format {
        "markdown" => Ok(false),
        "json" => Ok(true),
        _ => Err(AppError::BadRequest(
            "response_format must be markdown or json.".into(),
        )),
    }
}

fn resolve_model(settings: &AiSettings, task: &str, override_id: &str) -> String {
    let override_id = override_id.trim();
    if !override_id.is_empty() {
        return override_id.to_string();
    }
    if task == "analysis" {
        settings.analysis_model_id.trim().to_string()
    } else {
        settings.generation_model_id.trim().to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn rejects_nsec_in_context() {
        let context = json!({
            "ervTrainingContextVersion": 1,
            "profile": {"notes": "nsec1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"}
        });
        let err = validate_context(&context).unwrap_err();
        assert!(err.contains("secret key"), "{err}");
    }

    #[test]
    fn rejects_oversized_context() {
        let huge = "a".repeat(MAX_CONTEXT_BYTES);
        let context = json!({
            "ervTrainingContextVersion": 1,
            "profile": {"notes": huge}
        });
        let err = validate_context(&context).unwrap_err();
        assert!(err.contains("limit"));
    }

    #[test]
    fn rejects_unknown_context_field_and_media_url() {
        assert!(validate_context(&json!({"ervTrainingContextVersion": 1, "nsec": "no"})).is_err());
        assert!(redact_text("see https://blossom.example/blob/photo.jpg").is_err());
        assert!(validate_context(&json!({"ervTrainingContextVersion": 1})).is_ok());
    }
}
