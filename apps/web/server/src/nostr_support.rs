//! Nostr key, NIP-44, and relay helpers for the ERV web backend.
//!
//! Kind **30078** application-specific replaceable events, NIP-44 encrypt-to-self,
//! same dialect as the Android app.

use std::collections::HashMap;
use std::time::Duration;

use anyhow::{anyhow, Context};
use nostr::message::RelayMessage;
use nostr::nips::nip44;
use nostr::{Event, EventBuilder, Filter, Keys, Kind, Tag, Timestamp, ToBech32};
use nostr_sdk::Client;
use serde::Serialize;

use crate::config::resolve_relay_url;
use crate::erv_tags::{cardio_day_log_date, is_training_day_log_d_tag, weight_day_log_date};
use crate::relay_raw::{self, RelayConnectOptions};

pub const KIND_APP_DATA: u16 = 30078;

/// Match Android `fetchLatestKind30078ByDTag` subscription limit.
pub const KIND_APP_DATA_FETCH_LIMIT: usize = 2500;

const RELAY_CONNECT_TIMEOUT: Duration = Duration::from_secs(30);
const RELAY_AUTH_SETTLE: Duration = Duration::from_secs(3);
const RELAY_PROBE_LIMIT: usize = 1;
const RELAY_PROBE_TIMEOUT: Duration = Duration::from_secs(8);

#[derive(Debug, Clone, Serialize)]
pub struct KeyIdentity {
    pub npub: String,
    pub public_key_hex: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppDataFetchMeta {
    pub relay_events_fetched: usize,
    pub relay_fetch_limit: usize,
    pub relay_fetch_possibly_truncated: bool,
    pub relay_urls_queried: Vec<String>,
    pub training_weight_day_logs_on_relay: usize,
    pub training_cardio_day_logs_on_relay: usize,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppDataListResponse {
    pub records: Vec<AppDataRecord>,
    pub meta: AppDataFetchMeta,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppDataRecord {
    pub event_id: String,
    pub d_tag: Option<String>,
    pub ciphertext: String,
    pub plaintext: Option<String>,
    pub decrypt_error: Option<String>,
    pub tags: Vec<Vec<String>>,
}

pub fn parse_nsec(nsec: &str) -> anyhow::Result<(Keys, KeyIdentity)> {
    let nsec = nsec.trim();
    if !nsec.starts_with("nsec1") {
        return Err(anyhow!("secret key must be an nsec1... string"));
    }
    let keys = Keys::parse(nsec).map_err(|e| anyhow!("invalid nsec: {e}"))?;
    let public_key = keys.public_key();
    let identity = KeyIdentity {
        npub: public_key
            .to_bech32()
            .map_err(|e| anyhow!("encode npub: {e}"))?,
        public_key_hex: public_key.to_string(),
    };
    Ok((keys, identity))
}

pub fn keys_from_nsec_bytes(secret: &[u8]) -> anyhow::Result<Keys> {
    let nsec = std::str::from_utf8(secret).context("session secret is not utf-8")?;
    let (keys, _) = parse_nsec(nsec)?;
    Ok(keys)
}

pub fn encrypt_to_self(keys: &Keys, plaintext: &str) -> anyhow::Result<String> {
    nip44::encrypt(
        keys.secret_key(),
        &keys.public_key(),
        plaintext,
        nip44::Version::V2,
    )
    .map_err(|e| anyhow!("nip44 encrypt: {e}"))
}

pub fn decrypt_from_self(keys: &Keys, ciphertext: &str) -> anyhow::Result<String> {
    nip44::decrypt(keys.secret_key(), &keys.public_key(), ciphertext)
        .map_err(|e| anyhow!("nip44 decrypt: {e}"))
}

/// Decrypt kind-30078 content encrypted to self; accept legacy plaintext JSON payloads.
pub fn decrypt_app_content(keys: &Keys, content: &str) -> Result<String, String> {
    if content.trim().is_empty() {
        return Err("tombstone".into());
    }
    match decrypt_from_self(keys, content) {
        Ok(plain) if plain.trim().is_empty() => Err("tombstone".into()),
        Ok(plain) => Ok(plain),
        Err(first_err) => {
            let trimmed = content.trim();
            if (trimmed.starts_with('{') || trimmed.starts_with('['))
                && serde_json::from_str::<serde_json::Value>(trimmed).is_ok()
            {
                return Ok(trimmed.to_string());
            }
            Err(first_err.to_string())
        }
    }
}

fn is_tombstone_decrypt_error(err: &str) -> bool {
    err == "tombstone" || err.contains("message empty")
}

fn try_decrypt_event_plaintext(keys: &Keys, content: &str) -> Result<Option<String>, String> {
    match decrypt_app_content(keys, content) {
        Ok(plain) => Ok(Some(plain)),
        Err(err) if is_tombstone_decrypt_error(&err) => Ok(None),
        Err(err) => Err(err),
    }
}

pub async fn fetch_kind_events(
    keys: &Keys,
    relay_url: &str,
    kind: u16,
    opts: RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    let relay_url = resolve_relay_url(relay_url);
    if opts.insecure_tls && relay_url.starts_with("wss://") {
        let filter = Filter::new()
            .author(keys.public_key())
            .kind(Kind::Custom(kind))
            .since(fetch_since_timestamp())
            .limit(KIND_APP_DATA_FETCH_LIMIT);
        return relay_raw::fetch_events(keys, &relay_url, filter, opts).await;
    }

    let client = prepare_relay_client(keys, &relay_url).await?;

    let filter = Filter::new()
        .author(keys.public_key())
        .kind(Kind::Custom(kind))
        .since(fetch_since_timestamp())
        .limit(KIND_APP_DATA_FETCH_LIMIT);

    let sub_output = client.subscribe_to([&relay_url], filter, None).await?;
    let sub_id = sub_output.val;
    let mut notifications = client.notifications();
    let mut events = Vec::new();
    let deadline = tokio::time::Instant::now() + Duration::from_secs(25);

    while tokio::time::Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        let Ok(notification) = tokio::time::timeout(remaining, notifications.recv()).await else {
            break;
        };
        match notification {
            Ok(nostr_sdk::RelayPoolNotification::Event {
                subscription_id,
                event,
                ..
            }) if subscription_id == sub_id => events.push(*event),
            Ok(nostr_sdk::RelayPoolNotification::Message { message, .. }) => {
                if let RelayMessage::EndOfStoredEvents(eose_id) = &message {
                    if eose_id.as_ref() == &sub_id {
                        break;
                    }
                }
            }
            Ok(nostr_sdk::RelayPoolNotification::Shutdown) => break,
            Err(_) => break,
            _ => {}
        }
    }

    client.unsubscribe(&sub_id).await;
    client.shutdown().await;
    Ok(events)
}

/// Lightweight reachability check: one websocket connect + REQ with `limit(1)`.
/// Used by the status banner — not a full library/history fetch.
pub async fn probe_relay_connection(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<()> {
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let mut errors = Vec::new();
    for relay_url in relay_urls {
        let connect_url = resolve_relay_url(relay_url);
        let opts = relay_opts(&connect_url);
        match probe_kind_events(keys, relay_url, KIND_APP_DATA, opts).await {
            Ok(()) => return Ok(()),
            Err(err) => errors.push(format!("{connect_url}: {err}")),
        }
    }
    Err(anyhow!("all relay probes failed: {}", errors.join("; ")))
}

async fn probe_kind_events(
    keys: &Keys,
    relay_url: &str,
    kind: u16,
    opts: RelayConnectOptions,
) -> anyhow::Result<()> {
    let relay_url = resolve_relay_url(relay_url);
    let filter = Filter::new()
        .author(keys.public_key())
        .kind(Kind::Custom(kind))
        .limit(RELAY_PROBE_LIMIT);

    if opts.insecure_tls && relay_url.starts_with("wss://") {
        relay_raw::fetch_events(keys, &relay_url, filter, opts).await?;
        return Ok(());
    }

    let client = prepare_relay_client(keys, &relay_url).await?;
    let sub_output = client.subscribe_to([&relay_url], filter, None).await?;
    let sub_id = sub_output.val;
    let mut notifications = client.notifications();
    let deadline = tokio::time::Instant::now() + RELAY_PROBE_TIMEOUT;

    while tokio::time::Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        let Ok(notification) = tokio::time::timeout(remaining, notifications.recv()).await else {
            break;
        };
        match notification {
            Ok(nostr_sdk::RelayPoolNotification::Message { message, .. }) => {
                if let RelayMessage::EndOfStoredEvents(eose_id) = &message {
                    if eose_id.as_ref() == &sub_id {
                        break;
                    }
                }
            }
            Ok(nostr_sdk::RelayPoolNotification::Shutdown) => break,
            Err(_) => break,
            _ => {}
        }
    }

    client.unsubscribe(&sub_id).await;
    client.shutdown().await;
    Ok(())
}

pub async fn fetch_app_data_events(
    keys: &Keys,
    relay_url: &str,
    opts: RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    fetch_kind_events(keys, relay_url, KIND_APP_DATA, opts).await
}

/// Fetch kind-30078 events from every configured relay (no per-`#d` merge).
pub async fn fetch_raw_app_data_events_from_relays(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<(Vec<Event>, AppDataFetchMeta)> {
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let mut all_events = Vec::new();
    let mut errors = Vec::new();
    let mut possibly_truncated = false;
    for relay_url in relay_urls {
        let connect_url = resolve_relay_url(relay_url);
        if connect_url != *relay_url {
            tracing::info!(
                configured = %relay_url,
                resolved = %connect_url,
                "relay host rewritten for StartOS container DNS"
            );
        }
        let opts = relay_opts(&connect_url);
        match fetch_app_data_events(keys, relay_url, opts).await {
            Ok(events) => {
                tracing::debug!(%connect_url, count = events.len(), "fetched app-data events");
                if events.len() >= KIND_APP_DATA_FETCH_LIMIT {
                    possibly_truncated = true;
                    tracing::warn!(
                        %connect_url,
                        count = events.len(),
                        limit = KIND_APP_DATA_FETCH_LIMIT,
                        "relay app-data fetch hit limit; older day logs may be missing on web"
                    );
                }
                all_events.extend(events);
            }
            Err(err) => {
                tracing::warn!(configured = %relay_url, resolved = %connect_url, ?err, "relay fetch failed");
                errors.push(format!("{connect_url}: {err}"));
            }
        }
    }

    if all_events.is_empty() && !errors.is_empty() {
        return Err(anyhow!("all relay fetches failed: {}", errors.join("; ")));
    }

    let meta = AppDataFetchMeta {
        relay_events_fetched: all_events.len(),
        relay_fetch_limit: KIND_APP_DATA_FETCH_LIMIT,
        relay_fetch_possibly_truncated: possibly_truncated,
        relay_urls_queried: Vec::new(),
        training_weight_day_logs_on_relay: 0,
        training_cardio_day_logs_on_relay: 0,
    };
    Ok((all_events, meta))
}

/// Ignore very old replaceable rows so recent day-log republishes stay inside the relay limit window.
fn fetch_since_timestamp() -> Timestamp {
    const LOOKBACK_SECS: u64 = 400 * 86_400;
    Timestamp::now()
        .as_secs()
        .checked_sub(LOOKBACK_SECS)
        .map(Timestamp::from_secs)
        .unwrap_or(Timestamp::from_secs(0))
}

fn decrypt_app_data_records(keys: &Keys, all_events: Vec<Event>) -> Vec<AppDataRecord> {
    let mut by_d_tag: HashMap<String, Vec<Event>> = HashMap::new();
    let mut without_d_tag = Vec::new();
    for event in all_events {
        match event.tags.identifier() {
            Some(d_tag) => by_d_tag.entry(d_tag.to_string()).or_default().push(event),
            None => without_d_tag.push(event),
        }
    }

    let mut records = Vec::new();
    for (_d_tag, events) in by_d_tag {
        if events.is_empty() {
            continue;
        }
        records.push(best_app_data_record_for_events(keys, events));
    }
    for event in without_d_tag {
        records.push(event_to_app_data_record(keys, event));
    }
    records
}

/// Fetch from every relay and merge kind-30078 replaceable events by `#d` tag
/// (newest `created_at` wins — matches Android read-primary / multi-write semantics).
pub async fn fetch_app_data_events_from_relays(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    let (all_events, _) =
        fetch_raw_app_data_events_from_relays(keys, relay_urls, relay_opts).await?;
    Ok(merge_replaceable_app_data_events(all_events))
}

fn event_to_app_data_record(keys: &Keys, event: Event) -> AppDataRecord {
    let (plaintext, decrypt_error) = match try_decrypt_event_plaintext(keys, &event.content) {
        Ok(Some(plain)) => (Some(plain), None),
        Ok(None) => (None, None),
        Err(err) => (None, Some(err)),
    };
    AppDataRecord {
        event_id: event.id.to_string(),
        d_tag: event.tags.identifier().map(str::to_owned),
        ciphertext: event.content,
        plaintext,
        decrypt_error,
        tags: event.tags.iter().map(|t| t.clone().to_vec()).collect(),
    }
}

fn training_day_log_has_content(d_tag: &str, plain: &str) -> bool {
    if !is_training_day_log_d_tag(d_tag) {
        return true;
    }
    let Ok(value) = serde_json::from_str::<serde_json::Value>(plain) else {
        return false;
    };
    if weight_day_log_date(d_tag).is_some() {
        return value
            .get("workouts")
            .and_then(|w| w.as_array())
            .is_some_and(|workouts| !workouts.is_empty());
    }
    if cardio_day_log_date(d_tag).is_some() {
        return value
            .get("sessions")
            .and_then(|s| s.as_array())
            .is_some_and(|sessions| !sessions.is_empty());
    }
    true
}

/// Prefer the newest decryptable event when multiple relay copies exist for one `#d` tag.
/// Skips NIP-33 tombstones (empty content or NIP-44 payload decrypting to empty).
/// For weight/cardio day logs, also skips cleared payloads (`workouts:[]` / `sessions:[]`)
/// so an older copy with sessions can win (common when tombstones or clears stacked on relay).
fn best_app_data_record_for_events(keys: &Keys, mut events: Vec<Event>) -> AppDataRecord {
    events.sort_by_key(|e| std::cmp::Reverse(e.created_at));
    let d_tag = events
        .first()
        .and_then(|e| e.tags.identifier())
        .unwrap_or("");
    let mut last_failed: Option<AppDataRecord> = None;
    let mut last_empty_day_log: Option<AppDataRecord> = None;
    for event in &events {
        match try_decrypt_event_plaintext(keys, &event.content) {
            Ok(Some(plain)) => {
                let record = AppDataRecord {
                    event_id: event.id.to_string(),
                    d_tag: event.tags.identifier().map(str::to_owned),
                    ciphertext: event.content.clone(),
                    plaintext: Some(plain.clone()),
                    decrypt_error: None,
                    tags: event.tags.iter().map(|t| t.clone().to_vec()).collect(),
                };
                if is_training_day_log_d_tag(d_tag) && !training_day_log_has_content(d_tag, &plain) {
                    if last_empty_day_log.is_none() {
                        last_empty_day_log = Some(record);
                    }
                    continue;
                }
                return record;
            }
            Ok(None) => continue,
            Err(_) => {
                last_failed = Some(event_to_app_data_record(keys, event.clone()));
            }
        }
    }
    if let Some(empty) = last_empty_day_log {
        return empty;
    }
    if let Some(failed) = last_failed {
        return failed;
    }
    event_to_app_data_record(keys, events[0].clone())
}

/// For each `#d` identifier, keep the newest event (NIP-33 replaceable semantics).
pub fn merge_replaceable_app_data_events(events: Vec<Event>) -> Vec<Event> {
    let mut by_d_tag: HashMap<String, Event> = HashMap::new();
    let mut without_d_tag = Vec::new();

    for event in events {
        let Some(d_tag) = event.tags.identifier().map(str::to_string) else {
            without_d_tag.push(event);
            continue;
        };
        match by_d_tag.get(&d_tag) {
            None => {
                by_d_tag.insert(d_tag, event);
            }
            Some(existing) if event.created_at > existing.created_at => {
                by_d_tag.insert(d_tag, event);
            }
            _ => {}
        }
    }

    let mut merged: Vec<Event> = by_d_tag.into_values().collect();
    merged.extend(without_d_tag);
    merged
}

async fn send_signed_event(
    keys: &Keys,
    relay_url: &str,
    event: &Event,
    opts: RelayConnectOptions,
) -> anyhow::Result<()> {
    let relay_url = resolve_relay_url(relay_url);
    if opts.insecure_tls && relay_url.starts_with("wss://") {
        relay_raw::send_event(keys, &relay_url, event, opts).await?;
        return Ok(());
    }

    let client = prepare_relay_client(keys, &relay_url).await?;
    client.send_event(event).await?;
    tokio::time::sleep(Duration::from_millis(300)).await;
    client.shutdown().await;
    Ok(())
}

async fn prepare_relay_client(keys: &Keys, relay_url: &str) -> anyhow::Result<Client> {
    let client = Client::new(keys.clone());
    client.add_relay(relay_url).await?;
    client
        .try_connect_relay(relay_url, RELAY_CONNECT_TIMEOUT)
        .await?;
    tokio::time::sleep(RELAY_AUTH_SETTLE).await;
    Ok(client)
}

pub async fn fetch_decrypted_app_data(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<AppDataListResponse> {
    let (all_events, mut meta) =
        fetch_raw_app_data_events_from_relays(keys, relay_urls, relay_opts).await?;
    let records = decrypt_app_data_records(keys, all_events);
    meta.relay_urls_queried = relay_urls.to_vec();
    meta.training_weight_day_logs_on_relay = records
        .iter()
        .filter(|r| {
            r.d_tag
                .as_deref()
                .and_then(weight_day_log_date)
                .is_some()
                && r.plaintext
                    .as_deref()
                    .is_some_and(|plain| training_day_log_has_content(r.d_tag.as_deref().unwrap_or(""), plain))
                && r.decrypt_error.is_none()
        })
        .count();
    meta.training_cardio_day_logs_on_relay = records
        .iter()
        .filter(|r| {
            r.d_tag
                .as_deref()
                .and_then(cardio_day_log_date)
                .is_some()
                && r.plaintext
                    .as_deref()
                    .is_some_and(|plain| training_day_log_has_content(r.d_tag.as_deref().unwrap_or(""), plain))
                && r.decrypt_error.is_none()
        })
        .count();
    Ok(AppDataListResponse { records, meta })
}

/// Sign (and encrypt) a kind-30078 app-data event without sending it.
pub fn build_app_data_event(
    keys: &Keys,
    d_tag: &str,
    plaintext: &str,
) -> anyhow::Result<Event> {
    if d_tag.trim().is_empty() {
        return Err(anyhow!("d_tag cannot be empty"));
    }
    let ciphertext = encrypt_to_self(keys, plaintext)?;
    EventBuilder::new(Kind::Custom(KIND_APP_DATA), ciphertext)
        .tag(Tag::identifier(d_tag))
        .sign_with_keys(keys)
        .map_err(|e| anyhow!("sign event: {e}"))
}

/// Send an already-signed event to every relay. Succeeds if any relay accepts.
pub async fn send_event_to_relays(
    keys: &Keys,
    relay_urls: &[String],
    event: &Event,
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<()> {
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let mut errors = Vec::new();
    let mut any_ok = false;
    for relay_url in relay_urls {
        let opts = relay_opts(relay_url);
        match send_signed_event(keys, relay_url, event, opts).await {
            Ok(()) => {
                tracing::debug!(%relay_url, "published event to relay");
                any_ok = true;
            }
            Err(err) => {
                tracing::warn!(%relay_url, ?err, "relay publish failed");
                errors.push(format!("{relay_url}: {err}"));
            }
        }
    }

    if !any_ok {
        return Err(anyhow!(
            "failed to publish to any relay: {}",
            errors.join("; ")
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nip44_roundtrip_to_self() {
        let keys = Keys::generate();
        let plaintext = r#"{"v":1,"items":[]}"#;

        let ciphertext = encrypt_to_self(&keys, plaintext).expect("encrypt");
        assert_ne!(ciphertext, plaintext);

        let decrypted = decrypt_from_self(&keys, &ciphertext).expect("decrypt");
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn parse_nsec_rejects_non_nsec_input() {
        let err = parse_nsec("not-a-secret").expect_err("invalid secret");
        assert!(err.to_string().contains("nsec1"));
    }

    #[test]
    fn merge_replaceable_dedupes_same_d_tag() {
        let keys = Keys::generate();
        let event = EventBuilder::new(Kind::Custom(KIND_APP_DATA), "payload")
            .tag(Tag::identifier("erv/weight/routines"))
            .sign_with_keys(&keys)
            .expect("sign");
        let merged = merge_replaceable_app_data_events(vec![event.clone(), event]);
        assert_eq!(merged.len(), 1);
    }

    #[test]
    fn decrypt_app_content_accepts_plaintext_json() {
        let keys = Keys::generate();
        let plain = r#"{"date":"2026-06-01","workouts":[]}"#;
        let got = decrypt_app_content(&keys, plain).expect("plaintext json");
        assert_eq!(got, plain);
    }

    #[test]
    fn best_record_prefers_decryptable_over_newer_corrupt() {
        let keys = Keys::generate();
        let d_tag = "erv/weight/2026-06-01";
        let good_plain = r#"{"date":"2026-06-01","workouts":[]}"#;
        let good_cipher = encrypt_to_self(&keys, good_plain).expect("encrypt");
        let older = EventBuilder::new(Kind::Custom(KIND_APP_DATA), good_cipher)
            .tag(Tag::identifier(d_tag))
            .custom_created_at(100.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let newer = EventBuilder::new(Kind::Custom(KIND_APP_DATA), "not-valid-nip44")
            .tag(Tag::identifier(d_tag))
            .custom_created_at(200.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let record = best_app_data_record_for_events(&keys, vec![newer, older]);
        assert!(record.decrypt_error.is_none());
        assert_eq!(record.plaintext.as_deref(), Some(good_plain));
    }

    #[test]
    fn best_record_skips_newer_cleared_day_log() {
        let keys = Keys::generate();
        let d_tag = "erv/cardio/2026-06-17";
        let good_plain = r#"{"date":"2026-06-17","sessions":[{"id":"s1","activity":{"displayLabel":"Run"},"durationMinutes":30}]}"#;
        let good_cipher = encrypt_to_self(&keys, good_plain).expect("encrypt");
        let older = EventBuilder::new(Kind::Custom(KIND_APP_DATA), good_cipher)
            .tag(Tag::identifier(d_tag))
            .custom_created_at(100.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let cleared = r#"{"date":"2026-06-17","sessions":[]}"#;
        let cleared_cipher = encrypt_to_self(&keys, cleared).expect("encrypt");
        let newer = EventBuilder::new(Kind::Custom(KIND_APP_DATA), cleared_cipher)
            .tag(Tag::identifier(d_tag))
            .custom_created_at(200.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let record = best_app_data_record_for_events(&keys, vec![newer, older]);
        assert!(record.decrypt_error.is_none());
        assert_eq!(record.plaintext.as_deref(), Some(good_plain));
    }

    #[test]
    fn best_record_skips_newer_cleared_cardio_day_log() {
        let keys = Keys::generate();
        let d_tag = "erv/cardio/2026-06-18";
        let good_plain = r#"{"date":"2026-06-18","sessions":[{"id":"s1","activity":{"displayLabel":"Stationary Bike"},"durationMinutes":35}]}"#;
        let good_cipher = encrypt_to_self(&keys, good_plain).expect("encrypt");
        let older = EventBuilder::new(Kind::Custom(KIND_APP_DATA), good_cipher)
            .tag(Tag::identifier(d_tag))
            .custom_created_at(100.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let cleared = r#"{"date":"2026-06-18","sessions":[]}"#;
        let cleared_cipher = encrypt_to_self(&keys, cleared).expect("encrypt");
        let newer = EventBuilder::new(Kind::Custom(KIND_APP_DATA), cleared_cipher)
            .tag(Tag::identifier(d_tag))
            .custom_created_at(200.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let record = best_app_data_record_for_events(&keys, vec![newer, older]);
        assert!(record.decrypt_error.is_none());
        assert_eq!(record.plaintext.as_deref(), Some(good_plain));
    }

    #[test]
    fn best_record_skips_newer_empty_tombstone() {
        let keys = Keys::generate();
        let d_tag = "erv/weight/2026-06-16";
        let good_plain = r#"{"date":"2026-06-16","workouts":[{"id":"w1","source":"LIVE","entries":[{"exerciseId":"ex1","sets":[{"reps":5,"weightKg":100}]}]}]}"#;
        let good_cipher = encrypt_to_self(&keys, good_plain).expect("encrypt");
        let older = EventBuilder::new(Kind::Custom(KIND_APP_DATA), good_cipher)
            .tag(Tag::identifier(d_tag))
            .custom_created_at(100.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let newer = EventBuilder::new(Kind::Custom(KIND_APP_DATA), "")
            .tag(Tag::identifier(d_tag))
            .custom_created_at(200.into())
            .sign_with_keys(&keys)
            .expect("sign");
        let record = best_app_data_record_for_events(&keys, vec![newer, older]);
        assert!(record.decrypt_error.is_none());
        assert_eq!(record.plaintext.as_deref(), Some(good_plain));
    }
}
