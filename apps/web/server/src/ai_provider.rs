//! OpenAI-compatible chat client for Maple Proxy and other local servers.
//! Authorization is attached only when the caller supplies a key (ERV_SEALED).

use std::time::Duration;

use serde_json::{json, Value};

#[derive(Debug, Clone)]
pub struct ChatCall {
    pub base_url: String,
    pub api_key: Option<String>,
    pub model: String,
    pub system: String,
    pub user: String,
    pub json_object: bool,
    pub timeout: Duration,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ChatCompletion {
    pub content: String,
    pub model: String,
    pub prompt_tokens: Option<u64>,
    pub completion_tokens: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UpstreamFailure {
    Down,
    KeyRejected,
    PlanGated,
    Status(u16),
    BadResponse,
}

impl UpstreamFailure {
    pub fn message(&self) -> String {
        match self {
            Self::Down => "proxy not running — start Maple Proxy on StartOS".into(),
            Self::KeyRejected => "key missing or rejected".into(),
            Self::PlanGated => "Maple credits exhausted / plan-gated model".into(),
            Self::Status(code) => format!("Maple Proxy returned {code}"),
            Self::BadResponse => "Maple Proxy returned a completion ERV could not read.".into(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum StreamPiece {
    Text(String),
    Done,
}

#[derive(Default)]
pub struct SseDecoder {
    buffer: String,
}

impl SseDecoder {
    pub fn push(&mut self, chunk: &str) -> Vec<StreamPiece> {
        self.buffer.push_str(chunk);
        self.buffer = self.buffer.replace("\r\n", "\n");
        let mut pieces = Vec::new();
        while let Some(split) = self.buffer.find("\n\n") {
            let raw = self.buffer[..split].to_string();
            self.buffer.drain(..split + 2);
            if let Some(piece) = parse_sse_event(&raw) {
                pieces.push(piece);
            }
        }
        pieces
    }
}

pub async fn chat_complete(
    client: &reqwest::Client,
    call: ChatCall,
) -> Result<ChatCompletion, UpstreamFailure> {
    match post_chat(client, &call, false).await {
        Ok(completion) => Ok(completion),
        Err(UpstreamFailure::Status(400)) if call.json_object => {
            let mut retry = call;
            retry.json_object = false;
            post_chat(client, &retry, false).await
        }
        Err(other) => Err(other),
    }
}

pub async fn open_chat_stream(
    client: &reqwest::Client,
    call: ChatCall,
) -> Result<reqwest::Response, UpstreamFailure> {
    send_chat(client, &call, true).await
}

pub async fn chat_stream_text(
    client: &reqwest::Client,
    call: ChatCall,
) -> Result<String, UpstreamFailure> {
    let response = send_chat(client, &call, true).await?;
    let body = response
        .text()
        .await
        .map_err(|_| UpstreamFailure::BadResponse)?;
    let mut decoder = SseDecoder::default();
    let mut text = String::new();
    for piece in decoder.push(&body) {
        if let StreamPiece::Text(delta) = piece {
            text.push_str(&delta);
        }
    }
    Ok(text)
}

async fn post_chat(
    client: &reqwest::Client,
    call: &ChatCall,
    stream: bool,
) -> Result<ChatCompletion, UpstreamFailure> {
    let response = send_chat(client, call, stream).await?;
    let body: Value = response
        .json()
        .await
        .map_err(|_| UpstreamFailure::BadResponse)?;
    let content = body
        .pointer("/choices/0/message/content")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    if content.is_empty() {
        return Err(UpstreamFailure::BadResponse);
    }
    let model = body
        .get("model")
        .and_then(Value::as_str)
        .unwrap_or(call.model.as_str())
        .to_string();
    Ok(ChatCompletion {
        content,
        model,
        prompt_tokens: body.pointer("/usage/prompt_tokens").and_then(Value::as_u64),
        completion_tokens: body
            .pointer("/usage/completion_tokens")
            .and_then(Value::as_u64),
    })
}

async fn send_chat(
    client: &reqwest::Client,
    call: &ChatCall,
    stream: bool,
) -> Result<reqwest::Response, UpstreamFailure> {
    let url = format!(
        "{}/v1/chat/completions",
        call.base_url.trim_end_matches('/')
    );
    let mut body = json!({
        "model": call.model,
        "messages": [
            {"role": "system", "content": call.system},
            {"role": "user", "content": call.user}
        ],
        "stream": stream
    });
    if call.json_object {
        body["response_format"] = json!({"type": "json_object"});
    }
    let mut request = client.post(url).timeout(call.timeout).json(&body);
    if let Some(key) = call.api_key.as_deref().filter(|key| !key.is_empty()) {
        request = request.bearer_auth(key);
    }
    let response = request.send().await.map_err(|_| UpstreamFailure::Down)?;
    let status = response.status().as_u16();
    if (200..300).contains(&status) {
        return Ok(response);
    }
    Err(match status {
        401 | 403 => UpstreamFailure::KeyRejected,
        402 => UpstreamFailure::PlanGated,
        other => UpstreamFailure::Status(other),
    })
}

fn parse_sse_event(raw: &str) -> Option<StreamPiece> {
    let mut data = String::new();
    for line in raw.lines() {
        let Some(rest) = line.strip_prefix("data:") else {
            continue;
        };
        if !data.is_empty() {
            data.push('\n');
        }
        data.push_str(rest.trim_start());
    }
    if data.is_empty() {
        return None;
    }
    if data.trim() == "[DONE]" {
        return Some(StreamPiece::Done);
    }
    let parsed: Value = serde_json::from_str(&data).ok()?;
    let text = parsed
        .pointer("/choices/0/delta/content")
        .and_then(Value::as_str)
        .unwrap_or("");
    if text.is_empty() {
        None
    } else {
        Some(StreamPiece::Text(text.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{routing::post, Json, Router};
    use tokio::net::TcpListener;

    #[test]
    fn sse_decoder_joins_split_chunks() {
        let mut decoder = SseDecoder::default();
        let first = decoder.push("data: {\"choices\":[{\"delta\":{\"content\":\"Hel");
        assert!(first.is_empty());
        let second = decoder.push("lo\"}}]}\n\ndata: [DONE]\n\n");
        assert_eq!(
            second,
            vec![StreamPiece::Text("Hello".into()), StreamPiece::Done]
        );
    }

    #[tokio::test]
    async fn fake_server_streams_and_completes() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let app = Router::new().route(
            "/v1/chat/completions",
            post(|Json(body): Json<Value>| async move {
                if body.get("stream").and_then(Value::as_bool) == Some(true) {
                    return (
                        [(axum::http::header::CONTENT_TYPE, "text/event-stream")],
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\ndata: [DONE]\n\n"
                            .to_string(),
                    )
                        .into_response();
                }
                Json(json!({
                    "model": "test-model",
                    "choices": [{"message": {"role": "assistant", "content": "Hello athlete"}}],
                    "usage": {"prompt_tokens": 4, "completion_tokens": 2}
                }))
                .into_response()
            }),
        );
        tokio::spawn(async move {
            let _ = axum::serve(listener, app).await;
        });
        let client = reqwest::Client::new();
        let call = ChatCall {
            base_url: format!("http://{addr}"),
            api_key: None,
            model: "test-model".into(),
            system: "system".into(),
            user: "user".into(),
            json_object: false,
            timeout: Duration::from_secs(2),
        };
        let completion = chat_complete(&client, call.clone()).await.unwrap();
        assert_eq!(completion.content, "Hello athlete");
        assert_eq!(completion.model, "test-model");
        assert_eq!(completion.completion_tokens, Some(2));
        let streamed = chat_stream_text(&client, call).await.unwrap();
        assert_eq!(streamed, "Hi");
    }

    use axum::response::IntoResponse;
}
