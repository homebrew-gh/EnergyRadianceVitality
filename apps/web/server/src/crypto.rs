//! At-rest encryption for the user's nsec.
//!
//! - KEK = Argon2id(passphrase, salt) into 32 bytes.
//! - Ciphertext = XChaCha20-Poly1305(KEK, nonce, plaintext).
//!
//! The sealed blob is the only secret material persisted to disk. The
//! plaintext nsec only exists in memory inside [`Zeroizing`] containers, and
//! is wiped on lock / idle timeout / drop.

use anyhow::anyhow;
use argon2::{Algorithm, Argon2, Params, Version};
use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use chacha20poly1305::{
    aead::{Aead, KeyInit},
    XChaCha20Poly1305, XNonce,
};
use hkdf::Hkdf;
use rand::RngCore;
use sha2::Sha256;
use zeroize::Zeroizing;

/// Argon2id parameters. Tuned for "modern desktop-class server" — 64 MiB,
/// 3 iterations, single lane. Worth revisiting before public release; for
/// now it's a sane default that matches the threat model in
/// `docs/WEB_APP.md`.
const ARGON2_M_KIB: u32 = 64 * 1024;
const ARGON2_T: u32 = 3;
const ARGON2_P: u32 = 1;

const SALT_LEN: usize = 16;
const NONCE_LEN: usize = 24;
const KEY_LEN: usize = 32;

#[derive(Debug, Clone)]
pub struct SealedBlob {
    pub salt: [u8; SALT_LEN],
    pub nonce: [u8; NONCE_LEN],
    pub ciphertext: Vec<u8>,
}

pub fn random_salt() -> [u8; SALT_LEN] {
    let mut s = [0u8; SALT_LEN];
    rand::rngs::OsRng.fill_bytes(&mut s);
    s
}

pub fn random_nonce() -> [u8; NONCE_LEN] {
    let mut n = [0u8; NONCE_LEN];
    rand::rngs::OsRng.fill_bytes(&mut n);
    n
}

/// Derive a 32-byte KEK from `passphrase` and `salt`. Returned as a
/// [`Zeroizing`] buffer so the cipher key doesn't linger after we drop it.
pub fn derive_kek(
    passphrase: &str,
    salt: &[u8; SALT_LEN],
) -> anyhow::Result<Zeroizing<[u8; KEY_LEN]>> {
    let params = Params::new(ARGON2_M_KIB, ARGON2_T, ARGON2_P, Some(KEY_LEN))
        .map_err(|e| anyhow!("argon2 params: {e}"))?;
    let argon = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    let mut out = Zeroizing::new([0u8; KEY_LEN]);
    argon
        .hash_password_into(passphrase.as_bytes(), salt, out.as_mut())
        .map_err(|e| anyhow!("argon2 derive: {e}"))?;
    Ok(out)
}

pub fn seal(passphrase: &str, plaintext: &[u8]) -> anyhow::Result<SealedBlob> {
    let salt = random_salt();
    let nonce = random_nonce();
    let kek = derive_kek(passphrase, &salt)?;
    let cipher = XChaCha20Poly1305::new(kek.as_slice().into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce), plaintext)
        .map_err(|e| anyhow!("encrypt: {e}"))?;
    Ok(SealedBlob {
        salt,
        nonce,
        ciphertext,
    })
}

/// Domain separation for the AI API key seal. The KEK comes from the nsec,
/// so the key survives passphrase changes and stays usable only while unlocked.
const AI_KEY_INFO: &[u8] = b"erv-ai-key-v1";

pub fn derive_ai_kek(nsec: &[u8]) -> anyhow::Result<Zeroizing<[u8; KEY_LEN]>> {
    if nsec.is_empty() {
        return Err(anyhow!("missing session secret"));
    }
    let hk = Hkdf::<Sha256>::new(None, nsec);
    let mut out = Zeroizing::new([0u8; KEY_LEN]);
    hk.expand(AI_KEY_INFO, out.as_mut())
        .map_err(|_| anyhow!("hkdf expand"))?;
    Ok(out)
}

#[derive(Debug, Clone)]
pub struct AiKeySeal {
    pub nonce: [u8; NONCE_LEN],
    pub ciphertext: Vec<u8>,
}

pub fn seal_ai_key(nsec: &[u8], api_key: &str) -> anyhow::Result<AiKeySeal> {
    let kek = derive_ai_kek(nsec)?;
    let nonce = random_nonce();
    let cipher = XChaCha20Poly1305::new(kek.as_slice().into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce), api_key.as_bytes())
        .map_err(|e| anyhow!("encrypt ai key: {e}"))?;
    Ok(AiKeySeal { nonce, ciphertext })
}

pub fn open_ai_key(nsec: &[u8], seal: &AiKeySeal) -> anyhow::Result<Zeroizing<String>> {
    let kek = derive_ai_kek(nsec)?;
    let cipher = XChaCha20Poly1305::new(kek.as_slice().into());
    let plaintext = cipher
        .decrypt(XNonce::from_slice(&seal.nonce), seal.ciphertext.as_ref())
        .map_err(|_| anyhow!("decrypt ai key failed"))?;
    let text = String::from_utf8(plaintext).map_err(|_| anyhow!("ai key is not utf-8"))?;
    Ok(Zeroizing::new(text))
}

pub fn encode_ai_key_seal(seal: &AiKeySeal) -> (String, String) {
    (B64.encode(seal.nonce), B64.encode(&seal.ciphertext))
}

pub fn decode_ai_key_seal(nonce_b64: &str, ciphertext_b64: &str) -> anyhow::Result<AiKeySeal> {
    let nonce = B64.decode(nonce_b64).map_err(context_decode("nonce"))?;
    let ciphertext = B64
        .decode(ciphertext_b64)
        .map_err(context_decode("ciphertext"))?;
    if nonce.len() != NONCE_LEN {
        return Err(anyhow!(
            "ai key nonce length {} != {NONCE_LEN}",
            nonce.len()
        ));
    }
    let mut nonce_arr = [0u8; NONCE_LEN];
    nonce_arr.copy_from_slice(&nonce);
    Ok(AiKeySeal {
        nonce: nonce_arr,
        ciphertext,
    })
}

fn context_decode(label: &'static str) -> impl Fn(base64::DecodeError) -> anyhow::Error {
    move |err| anyhow!("decode ai key {label}: {err}")
}

/// Show only the last four characters. Short keys stay fully hidden.
pub fn mask_secret(secret: &str) -> String {
    let trimmed = secret.trim();
    let chars: Vec<char> = trimmed.chars().collect();
    if chars.len() <= 4 {
        return "••••".to_string();
    }
    let tail: String = chars[chars.len() - 4..].iter().collect();
    format!("…{tail}")
}

pub fn open(passphrase: &str, blob: &SealedBlob) -> anyhow::Result<Zeroizing<Vec<u8>>> {
    let kek = derive_kek(passphrase, &blob.salt)?;
    let cipher = XChaCha20Poly1305::new(kek.as_slice().into());
    let plaintext = cipher
        .decrypt(XNonce::from_slice(&blob.nonce), blob.ciphertext.as_ref())
        .map_err(|_| anyhow!("decrypt failed (likely wrong passphrase)"))?;
    Ok(Zeroizing::new(plaintext))
}
