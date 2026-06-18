use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::path::PathBuf;
use std::time::Duration;

use anyhow::Context;
use rand::RngCore;

#[derive(Clone)]
pub struct Config {
    pub data_dir: PathBuf,
    pub static_dir: PathBuf,
    pub bind_addr: SocketAddr,
    pub cookie_signing_key: [u8; 64],
    pub session_idle: Duration,
    pub cookie_secure: bool,
    pub insecure_relay_tls: Option<bool>,
}

impl Config {
    pub fn from_env() -> anyhow::Result<Self> {
        let data_dir = std::env::var("ERV_DATA_DIR")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from("./data"));
        let static_dir = std::env::var("ERV_STATIC_DIR")
            .map(PathBuf::from)
            .unwrap_or_else(|_| {
                // `cargo run` from apps/web/server → assets in ../web/dist
                let web_dist = PathBuf::from("../web/dist");
                if web_dist.join("index.html").is_file() {
                    web_dist
                } else {
                    PathBuf::from("./dist")
                }
            });
        let port: u16 = std::env::var("ERV_PORT")
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(3000);
        let bind = std::env::var("ERV_BIND")
            .ok()
            .and_then(|s| s.parse::<IpAddr>().ok())
            .unwrap_or(IpAddr::V4(Ipv4Addr::UNSPECIFIED));
        let bind_addr = SocketAddr::new(bind, port);

        let idle_minutes: u64 = std::env::var("ERV_IDLE_MINUTES")
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(15);
        let cookie_secure = std::env::var("ERV_COOKIE_SECURE")
            .ok()
            .and_then(|s| match s.to_ascii_lowercase().as_str() {
                "1" | "true" | "yes" | "on" => Some(true),
                "0" | "false" | "no" | "off" => Some(false),
                _ => None,
            })
            .unwrap_or(true);

        let insecure_relay_tls = std::env::var("ERV_INSECURE_RELAY_TLS")
            .ok()
            .and_then(|s| match s.to_ascii_lowercase().as_str() {
                "1" | "true" | "yes" | "on" => Some(true),
                "0" | "false" | "no" | "off" => Some(false),
                _ => None,
            });

        std::fs::create_dir_all(&data_dir)
            .with_context(|| format!("create_dir_all {}", data_dir.display()))?;
        let cookie_signing_key = load_or_create_cookie_key(&data_dir)?;

        Ok(Self {
            data_dir,
            static_dir,
            bind_addr,
            cookie_signing_key,
            session_idle: Duration::from_secs(idle_minutes * 60),
            cookie_secure,
            insecure_relay_tls,
        })
    }

    pub fn ensure_dirs(&self) -> anyhow::Result<()> {
        std::fs::create_dir_all(&self.data_dir)
            .with_context(|| format!("create_dir_all {}", self.data_dir.display()))?;
        Ok(())
    }

    pub fn state_path(&self) -> PathBuf {
        self.data_dir.join("state.json")
    }

    pub fn relay_connect_options(&self, relay_url: &str) -> crate::relay_raw::RelayConnectOptions {
        let resolved = resolve_relay_url(relay_url);
        crate::relay_raw::RelayConnectOptions {
            insecure_tls: self
                .insecure_relay_tls
                .unwrap_or_else(|| should_auto_insecure_relay_tls(&resolved)),
        }
    }
}

pub const DETECTED_RELAY_LABEL: &str = "Nostr RS Relay";

pub fn detected_relay_label() -> Option<String> {
    std::env::var("ERV_DETECTED_RELAY_LABEL")
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

pub fn detected_relay_url() -> Option<String> {
    let raw = std::env::var("ERV_INTERNAL_RELAY_URL")
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())?;
    let normalized = normalize_relay_url(&raw);
    if normalized.starts_with("ws://") && normalized.contains(".startos") {
        Some(normalized)
    } else {
        None
    }
}

/// LAN `wss://` URL from the linked nostr-rs-relay interface — used to pre-fill setup.
pub fn suggested_relay_url() -> Option<String> {
    let raw = std::env::var("ERV_SUGGESTED_RELAY_URL")
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())?;
    let normalized = normalize_relay_url(&raw);
    if normalized.starts_with("wss://") {
        Some(normalized)
    } else {
        None
    }
}

pub fn relay_prefill_url() -> Option<String> {
    suggested_relay_url().or_else(detected_relay_url)
}

pub fn resolve_relay_url(url: &str) -> String {
    normalize_relay_url(&resolve_relay_url_with(
        url,
        std::env::var("ERV_INTERNAL_RELAY_URL")
            .ok()
            .filter(|s| !s.trim().is_empty())
            .as_deref(),
        std::env::var("ERV_RELAY_HOST")
            .ok()
            .filter(|s| !s.trim().is_empty())
            .as_deref(),
    ))
}

fn resolve_relay_url_with(
    url: &str,
    internal_relay_url: Option<&str>,
    relay_host_override: Option<&str>,
) -> String {
    use nostr::Url;

    if let Some(internal) = internal_relay_url {
        if let Ok(parsed) = Url::parse(url) {
            if parsed
                .host_str()
                .is_some_and(is_startos_lan_relay_host)
            {
                tracing::info!(
                    configured = %url,
                    internal = %internal,
                    "using StartOS internal relay URL for LAN-configured relay"
                );
                return internal.to_string();
            }
        }
    }

    if let Some(custom) = relay_host_override {
        if let Ok(mut parsed) = Url::parse(url) {
            if parsed.set_host(Some(custom)).is_ok() {
                return parsed.to_string();
            }
        }
    }

    url.to_string()
}

/// Hostnames/IPs used for StartOS LAN proxy URLs (`wss://*.local:64644`, `wss://10.x:64644`).
/// Sibling containers cannot use these; they must use `ws://<package>.startos:<port>`.
fn is_startos_lan_relay_host(host: &str) -> bool {
    if host.ends_with(".local") {
        return true;
    }
    if let Ok(ip) = host.parse::<IpAddr>() {
        return match ip {
            IpAddr::V4(v4) => v4.is_private() || v4.is_loopback() || v4.is_link_local(),
            IpAddr::V6(v6) => {
                v6.is_loopback() || v6.is_unique_local() || v6.is_unicast_link_local()
            }
        };
    }
    false
}

pub fn normalize_relay_url(url: &str) -> String {
    use nostr::Url;

    let trimmed = url.trim();
    let Ok(mut parsed) = Url::parse(trimmed) else {
        return trimmed.to_string();
    };
    if parsed.scheme() == "wss" {
        if parsed.host_str().is_some_and(|host| host.ends_with(".startos")) {
            let _ = parsed.set_scheme("ws");
            return parsed.to_string();
        }
    }
    trimmed.to_string()
}

fn should_auto_insecure_relay_tls(relay_url: &str) -> bool {
    use nostr::Url;
    use std::net::IpAddr;

    let Ok(parsed) = Url::parse(relay_url) else {
        return false;
    };
    let Some(host) = parsed.host_str() else {
        return false;
    };
    if matches!(host, "127.0.0.1" | "localhost" | "::1" | "[::1]") {
        return true;
    }
    if host.ends_with(".local") || host.ends_with(".startos") {
        return true;
    }
    if let Ok(ip) = host.parse::<IpAddr>() {
        return match ip {
            IpAddr::V4(v4) => v4.is_private() || v4.is_loopback(),
            IpAddr::V6(v6) => v6.is_loopback() || v6.is_unique_local(),
        };
    }
    false
}

fn load_or_create_cookie_key(data_dir: &std::path::Path) -> anyhow::Result<[u8; 64]> {
    let path = data_dir.join("cookie.key");
    if path.exists() {
        let bytes = std::fs::read(&path).with_context(|| format!("read {}", path.display()))?;
        if bytes.len() == 64 {
            let mut out = [0u8; 64];
            out.copy_from_slice(&bytes);
            return Ok(out);
        }
        tracing::warn!("{} has unexpected length, regenerating", path.display());
    }
    let mut out = [0u8; 64];
    rand::rngs::OsRng.fill_bytes(&mut out);
    std::fs::write(&path, &out[..]).with_context(|| format!("write {}", path.display()))?;
    set_owner_only(&path)?;
    Ok(out)
}

#[cfg(unix)]
fn set_owner_only(path: &std::path::Path) -> anyhow::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    let perm = std::fs::Permissions::from_mode(0o600);
    std::fs::set_permissions(path, perm)
        .with_context(|| format!("chmod 0600 {}", path.display()))?;
    Ok(())
}

#[cfg(not(unix))]
fn set_owner_only(_path: &std::path::Path) -> anyhow::Result<()> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Mutex, MutexGuard};

    static ENV_LOCK: Mutex<()> = Mutex::new(());

    struct EnvGuard {
        _lock: MutexGuard<'static, ()>,
        keys: Vec<String>,
    }

    impl EnvGuard {
        fn new() -> Self {
            Self {
                _lock: ENV_LOCK.lock().unwrap_or_else(|e| e.into_inner()),
                keys: Vec::new(),
            }
        }

        fn set(&mut self, key: &str, value: &str) {
            std::env::set_var(key, value);
            self.keys.push(key.to_string());
        }
    }

    impl Drop for EnvGuard {
        fn drop(&mut self) {
            for key in &self.keys {
                std::env::remove_var(key);
            }
        }
    }

    #[test]
    fn detected_relay_url_unset_without_internal_env() {
        let _guard = EnvGuard::new();
        assert_eq!(detected_relay_url(), None);
    }

    #[test]
    fn detected_relay_url_reads_internal_startos_ws_url() {
        let mut guard = EnvGuard::new();
        guard.set("ERV_INTERNAL_RELAY_URL", "ws://haven.startos:3355");
        assert_eq!(
            detected_relay_url(),
            Some("ws://haven.startos:3355".into())
        );
    }

    #[test]
    fn detected_relay_label_reads_env() {
        let mut guard = EnvGuard::new();
        guard.set("ERV_DETECTED_RELAY_LABEL", "Haven");
        assert_eq!(detected_relay_label(), Some("Haven".into()));
    }

    #[test]
    fn resolve_relay_url_rewrites_private_ip_when_internal_configured() {
        assert_eq!(
            resolve_relay_url_with(
                "wss://10.0.0.47:64644",
                Some("ws://nostr-rs-relay.startos:8080"),
                None,
            ),
            "ws://nostr-rs-relay.startos:8080"
        );
    }

    #[test]
    fn resolve_relay_url_rewrites_local_hostname_when_internal_configured() {
        assert_eq!(
            resolve_relay_url_with(
                "wss://embassy-fasting-gangs.local:64644",
                Some("ws://nostr-rs-relay.startos:8080"),
                None,
            ),
            "ws://nostr-rs-relay.startos:8080"
        );
    }

    #[test]
    fn resolve_relay_url_leaves_public_urls_unchanged() {
        assert_eq!(
            resolve_relay_url_with(
                "wss://relay.damus.io",
                Some("ws://nostr-rs-relay.startos:8080"),
                None,
            ),
            "wss://relay.damus.io"
        );
    }

    #[test]
    fn relay_prefill_prefers_suggested_over_internal() {
        let mut guard = EnvGuard::new();
        guard.set("ERV_INTERNAL_RELAY_URL", "ws://nostr-rs-relay.startos:8080");
        guard.set(
            "ERV_SUGGESTED_RELAY_URL",
            "wss://nostr-rs-relay.local:443",
        );
        assert_eq!(
            relay_prefill_url(),
            Some("wss://nostr-rs-relay.local:443".into())
        );
    }
}
