//! On-disk state. Single JSON file at `$ERV_DATA_DIR/state.json` containing:
//!
//! - the sealed nsec blob (`salt`, `nonce`, `ciphertext`, all base64) when a
//!   user passphrase is set,
//! - the plaintext nsec when the operator skipped a passphrase,
//! - the configured relay URL list (if any).
//!
//! Existing installs that only have `sealed` stay locked until unlock, nsec
//! recovery, or wipe — this loader never decrypts a sealed blob on its own.
//!
//! Atomically written via temp-file + rename so a crash mid-write can't leave
//! a partial blob behind.

use std::path::Path;

use anyhow::{anyhow, Context};
use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use serde::{Deserialize, Serialize};

use crate::crypto::SealedBlob;

const STATE_VERSION: u32 = 1;

#[derive(Clone, Serialize, Deserialize)]
pub struct PersistentState {
    pub v: u32,
    pub sealed: Option<SealedRecord>,
    /// Plaintext nsec when the operator skipped a user passphrase.
    /// Never honored while `sealed` is present (existing locked installs
    /// must not be silently unsealed).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub nsec: Option<String>,
    /// Primary relay (legacy). Kept in sync with the first entry of `relay_urls`.
    pub relay_url: Option<String>,
    /// Ordered relay list: first is primary (reads merge all; writes go to every URL).
    #[serde(default)]
    pub relay_urls: Vec<String>,
    /// Cached npub derived once at setup, so /api/auth/status can show it
    /// without holding the secret key. The npub is public, so persisting
    /// it in plaintext is fine.
    pub npub: Option<String>,
}

impl std::fmt::Debug for PersistentState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("PersistentState")
            .field("v", &self.v)
            .field("sealed", &self.sealed)
            .field("nsec", &self.nsec.as_ref().map(|_| "[redacted]"))
            .field("relay_url", &self.relay_url)
            .field("relay_urls", &self.relay_urls)
            .field("npub", &self.npub)
            .finish()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SealedRecord {
    pub salt_b64: String,
    pub nonce_b64: String,
    pub ciphertext_b64: String,
}

impl Default for PersistentState {
    fn default() -> Self {
        Self {
            v: STATE_VERSION,
            sealed: None,
            nsec: None,
            relay_url: None,
            relay_urls: Vec::new(),
            npub: None,
        }
    }
}

impl PersistentState {
    /// Normalize relay list after load (migrate legacy single `relay_url` field).
    pub fn normalize_relays(&mut self) {
        if self.relay_urls.is_empty() {
            if let Some(url) = self.relay_url.clone().filter(|u| !u.is_empty()) {
                self.relay_urls = vec![url];
            }
        } else {
            self.relay_urls = dedupe_relay_urls(std::mem::take(&mut self.relay_urls));
            self.relay_url = self.relay_urls.first().cloned();
        }
    }

    pub fn relay_urls(&self) -> &[String] {
        &self.relay_urls
    }

    pub fn primary_relay_url(&self) -> Option<&str> {
        self.relay_urls.first().map(String::as_str)
    }

    pub fn set_relay_urls(&mut self, urls: Vec<String>) {
        self.relay_urls = dedupe_relay_urls(urls);
        self.relay_url = self.relay_urls.first().cloned();
    }

    pub fn has_state(&self) -> bool {
        self.sealed.is_some() || self.plain_nsec().is_some()
    }

    pub fn passphrase_set(&self) -> bool {
        self.sealed.is_some()
    }

    /// Plaintext nsec only when there is no sealed blob.
    pub fn plain_nsec(&self) -> Option<&str> {
        if self.sealed.is_some() {
            return None;
        }
        self.nsec.as_deref().filter(|s| !s.is_empty())
    }

    pub fn store_plain_nsec(&mut self, nsec: String) {
        self.sealed = None;
        self.nsec = Some(nsec);
    }

    pub fn store_sealed(&mut self, sealed: SealedRecord) {
        self.sealed = Some(sealed);
        self.nsec = None;
    }

    pub fn load(path: &Path) -> anyhow::Result<Self> {
        if !path.exists() {
            return Ok(Self::default());
        }
        let bytes = std::fs::read(path).with_context(|| format!("read {}", path.display()))?;
        let mut state: PersistentState =
            serde_json::from_slice(&bytes).with_context(|| format!("parse {}", path.display()))?;
        if state.v != STATE_VERSION {
            return Err(anyhow!(
                "unexpected state.json version {}; expected {}",
                state.v,
                STATE_VERSION
            ));
        }
        state.normalize_relays();
        Ok(state)
    }

    pub fn save(&self, path: &Path) -> anyhow::Result<()> {
        let parent = path
            .parent()
            .ok_or_else(|| anyhow!("state path has no parent"))?;
        std::fs::create_dir_all(parent)?;
        let tmp = path.with_extension("json.tmp");
        let mut to_write = self.clone();
        if to_write.sealed.is_some() {
            to_write.nsec = None;
        }
        let json = serde_json::to_vec_pretty(&to_write)?;
        std::fs::write(&tmp, &json).with_context(|| format!("write {}", tmp.display()))?;
        set_owner_only(&tmp)?;
        std::fs::rename(&tmp, path)
            .with_context(|| format!("rename {} -> {}", tmp.display(), path.display()))?;
        Ok(())
    }
}

pub fn dedupe_relay_urls(urls: Vec<String>) -> Vec<String> {
    let mut out = Vec::new();
    for url in urls {
        let trimmed = url.trim().to_string();
        if trimmed.is_empty() {
            continue;
        }
        if !out.iter().any(|existing| existing == &trimmed) {
            out.push(trimmed);
        }
    }
    out
}

impl SealedRecord {
    pub fn from_blob(blob: &SealedBlob) -> Self {
        Self {
            salt_b64: B64.encode(blob.salt),
            nonce_b64: B64.encode(blob.nonce),
            ciphertext_b64: B64.encode(&blob.ciphertext),
        }
    }

    pub fn to_blob(&self) -> anyhow::Result<SealedBlob> {
        let salt = B64.decode(&self.salt_b64).context("decode salt")?;
        let nonce = B64.decode(&self.nonce_b64).context("decode nonce")?;
        let ciphertext = B64
            .decode(&self.ciphertext_b64)
            .context("decode ciphertext")?;
        if salt.len() != 16 {
            return Err(anyhow!("salt length {} != 16", salt.len()));
        }
        if nonce.len() != 24 {
            return Err(anyhow!("nonce length {} != 24", nonce.len()));
        }
        let mut salt_arr = [0u8; 16];
        salt_arr.copy_from_slice(&salt);
        let mut nonce_arr = [0u8; 24];
        nonce_arr.copy_from_slice(&nonce);
        Ok(SealedBlob {
            salt: salt_arr,
            nonce: nonce_arr,
            ciphertext,
        })
    }
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

    #[test]
    fn load_legacy_sealed_json_stays_locked() {
        let dir = std::env::temp_dir().join(format!("erv-state-legacy-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("state.json");
        std::fs::write(
            &path,
            r#"{
  "v": 1,
  "sealed": {
    "salt_b64": "AAAAAAAAAAAAAAAAAAAAAA==",
    "nonce_b64": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    "ciphertext_b64": "AQID"
  },
  "relay_url": "wss://relay.example.com",
  "relay_urls": ["wss://relay.example.com"],
  "npub": "npub1example"
}"#,
        )
        .unwrap();
        let state = PersistentState::load(&path).unwrap();
        assert!(state.has_state());
        assert!(state.passphrase_set());
        assert!(state.plain_nsec().is_none());
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn sealed_present_ignores_plaintext_nsec() {
        let mut state = PersistentState::default();
        state.nsec = Some("nsec1shouldbeignored".into());
        state.sealed = Some(SealedRecord {
            salt_b64: "AAAAAAAAAAAAAAAAAAAAAA==".into(),
            nonce_b64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".into(),
            ciphertext_b64: "AQID".into(),
        });
        assert!(state.passphrase_set());
        assert!(state.plain_nsec().is_none());
        assert!(state.has_state());
    }

    #[test]
    fn save_drops_plaintext_when_sealed() {
        let dir = std::env::temp_dir().join(format!("erv-state-save-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("state.json");
        let mut state = PersistentState::default();
        state.store_sealed(SealedRecord {
            salt_b64: "AAAAAAAAAAAAAAAAAAAAAA==".into(),
            nonce_b64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".into(),
            ciphertext_b64: "AQID".into(),
        });
        state.nsec = Some("nsec1mustnotpersist".into());
        state.save(&path).unwrap();
        let loaded = PersistentState::load(&path).unwrap();
        assert!(loaded.nsec.is_none());
        assert!(loaded.sealed.is_some());
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn debug_redacts_plaintext_nsec() {
        let mut state = PersistentState::default();
        state.store_plain_nsec("nsec1secretvalue".into());
        let rendered = format!("{state:?}");
        assert!(!rendered.contains("nsec1secretvalue"));
        assert!(rendered.contains("[redacted]"));
    }
}
