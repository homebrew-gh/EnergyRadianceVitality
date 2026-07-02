//! Blossom media endpoint probing and blob fetch for the Start9 companion.
//!
//! Android uploads encrypted blobs via authenticated `PUT /upload` (kind 24242).
//! The web gallery reads them through a server-side proxy so LAN self-signed TLS
//! and browser CORS limits do not block decryption in the SPA.

use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::Duration;

use base64::Engine;
use nostr::{EventBuilder, JsonUtil, Keys, Kind, Tag, Timestamp};

const KIND_BLOSSOM_AUTH: u16 = 24242;
const EMPTY_BODY_SHA256_HEX: &str =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
const MAX_BLOB_BYTES: usize = 32 * 1024 * 1024;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlossomCheckResult {
    pub available: bool,
    pub auth_verified: bool,
    pub message: String,
}

/// Map `wss://host:port` → `https://host:port` (or `ws` → `http`).
pub fn blossom_origin_from_relay_url(relay_url: &str) -> Option<String> {
    let trimmed = relay_url.trim();
    let (scheme, rest) = if let Some(rest) = trimmed.strip_prefix("wss://") {
        ("https", rest)
    } else if let Some(rest) = trimmed.strip_prefix("ws://") {
        ("http", rest)
    } else {
        return None;
    };
    let authority = rest
        .split(['/', '?', '#'])
        .next()
        .unwrap_or_default()
        .trim();
    if authority.is_empty() {
        return None;
    }
    Some(format!("{scheme}://{authority}"))
}

/// Returns allowed Blossom origins derived from configured relay URLs.
pub fn allowed_blossom_origins(relay_urls: &[String]) -> Vec<String> {
    relay_urls
        .iter()
        .filter_map(|url| blossom_origin_from_relay_url(url))
        .collect()
}

/// Whether `blob_url` is under one of the allowed Blossom HTTPS origins.
pub fn is_allowed_blossom_blob_url(blob_url: &str, allowed_origins: &[String]) -> bool {
    let parsed = match parse_http_url(blob_url) {
        Some(parsed) => parsed,
        None => return false,
    };
    allowed_origins.iter().any(|origin| {
        parse_http_origin(origin)
            .map(|allowed| origins_match(&allowed, &parsed))
            .unwrap_or(false)
    })
}

pub fn check_blossom_status(
    origin: &str,
    accept_invalid_tls: bool,
    keys: &Keys,
) -> BlossomCheckResult {
    let parsed = match parse_http_origin(origin) {
        Some(parsed) => parsed,
        None => {
            return BlossomCheckResult {
                available: false,
                auth_verified: false,
                message: "Invalid Blossom origin.".into(),
            };
        }
    };

    let unauth = match http_request(
        &parsed,
        "HEAD",
        "/upload",
        &[],
        None,
        accept_invalid_tls,
    ) {
        Ok(response) => response,
        Err(message) => {
            return BlossomCheckResult {
                available: false,
                auth_verified: false,
                message,
            };
        }
    };

    if !unauth.status_line.starts_with("HTTP/") {
        return BlossomCheckResult {
            available: false,
            auth_verified: false,
            message: "No HTTP response from derived Blossom endpoint.".into(),
        };
    }

    let auth_header = match build_blossom_upload_auth(keys, EMPTY_BODY_SHA256_HEX) {
        Ok(value) => value,
        Err(err) => {
            return BlossomCheckResult {
                available: true,
                auth_verified: false,
                message: format!(
                    "Blossom endpoint responded ({}) but auth probe could not be signed: {err}",
                    unauth.status_line
                ),
            };
        }
    };

    let auth = match http_request(
        &parsed,
        "PUT",
        "/upload",
        &[
            ("Authorization", &auth_header),
            ("Content-Type", "application/octet-stream"),
            ("Content-Length", "0"),
        ],
        Some(&[]),
        accept_invalid_tls,
    ) {
        Ok(response) => response,
        Err(message) => {
            return BlossomCheckResult {
                available: true,
                auth_verified: false,
                message: format!(
                    "Blossom endpoint responded ({}) but authenticated upload probe failed: {message}",
                    unauth.status_line
                ),
            };
        }
    };

    if auth.status_code == 401 || auth.status_code == 403 {
        return BlossomCheckResult {
            available: true,
            auth_verified: false,
            message: format!(
                "Blossom endpoint reachable at {origin}. Upload auth was rejected — use the same nsec on this companion as on your phone."
            ),
        };
    }

    BlossomCheckResult {
        available: true,
        auth_verified: true,
        message: format!(
            "Upload auth verified at {origin}. Android can back up encrypted media blobs here; this gallery can fetch them through the companion."
        ),
    }
}

pub fn fetch_blossom_blob(
    blob_url: &str,
    accept_invalid_tls: bool,
) -> Result<(Vec<u8>, Option<String>), String> {
    let parsed = parse_http_url(blob_url).ok_or_else(|| "Invalid blob URL.".to_string())?;
    let path = parsed
        .path
        .as_deref()
        .filter(|p| !p.is_empty())
        .unwrap_or("/");
    let origin = ParsedHttpOrigin {
        https: parsed.https,
        host: parsed.host.clone(),
        host_header: parsed.host_header.clone(),
        port: parsed.port,
    };
    let response = http_request(&origin, "GET", path, &[], None, accept_invalid_tls)?;
    if response.status_code == 404 {
        return Err("Blob not found on Blossom server.".into());
    }
    if !(200..300).contains(&response.status_code) {
        return Err(format!(
            "Blossom blob fetch failed: {}",
            response.status_line
        ));
    }
    if response.body.len() > MAX_BLOB_BYTES {
        return Err(format!(
            "Blob exceeds companion size limit ({} bytes).",
            MAX_BLOB_BYTES
        ));
    }
    let content_type = response.content_type();
    Ok((response.body, content_type))
}

fn build_blossom_upload_auth(keys: &Keys, hash_hex: &str) -> Result<String, String> {
    let now = Timestamp::now();
    let exp = Timestamp::from(now.as_secs().saturating_add(60));
    let event = EventBuilder::new(Kind::Custom(KIND_BLOSSOM_AUTH), "blossom stuff")
        .tag(
            Tag::parse(["expiration", &exp.as_secs().to_string()])
                .map_err(|e| format!("tag expiration: {e}"))?,
        )
        .tag(Tag::parse(["t", "upload"]).map_err(|e| format!("tag t: {e}"))?)
        .tag(Tag::parse(["x", hash_hex]).map_err(|e| format!("tag x: {e}"))?)
        .sign_with_keys(keys)
        .map_err(|e| format!("sign blossom auth: {e}"))?;
    let auth_b64 = base64::engine::general_purpose::STANDARD.encode(event.as_json().as_bytes());
    Ok(format!("Nostr {auth_b64}"))
}

struct HttpResponse {
    status_line: String,
    status_code: u16,
    header_lines: Vec<String>,
    body: Vec<u8>,
}

impl HttpResponse {
    fn content_type(&self) -> Option<String> {
        self.header_lines
            .iter()
            .find_map(|line| {
                let (name, value) = line.split_once(':')?;
                if name.trim().eq_ignore_ascii_case("content-type") {
                    Some(value.trim().to_string())
                } else {
                    None
                }
            })
    }
}

fn http_request(
    origin: &ParsedHttpOrigin,
    method: &str,
    path: &str,
    extra_headers: &[(&str, &str)],
    body: Option<&[u8]>,
    accept_invalid_tls: bool,
) -> Result<HttpResponse, String> {
    let port = origin.port.unwrap_or(if origin.https { 443 } else { 80 });
    let mut header_block = format!(
        "{method} {path} HTTP/1.1\r\nHost: {}\r\nUser-Agent: erv-web/blossom\r\nConnection: close\r\n",
        origin.host_header
    );
    for (name, value) in extra_headers {
        header_block.push_str(&format!("{name}: {value}\r\n"));
    }
    if body.is_some() && !extra_headers.iter().any(|(k, _)| k.eq_ignore_ascii_case("content-length"))
    {
        header_block.push_str(&format!("Content-Length: {}\r\n", body.unwrap().len()));
    }
    header_block.push_str("\r\n");

    let addr = format!("{}:{port}", origin.host);
    let mut tcp =
        TcpStream::connect(&addr).map_err(|e| format!("Blossom endpoint unreachable: {e}"))?;
    let timeout = Some(Duration::from_secs(30));
    let _ = tcp.set_read_timeout(timeout);
    let _ = tcp.set_write_timeout(timeout);

    if origin.https {
        let mut builder = native_tls::TlsConnector::builder();
        builder.danger_accept_invalid_certs(accept_invalid_tls);
        let connector = builder
            .build()
            .map_err(|e| format!("TLS setup failed: {e}"))?;
        let mut stream = connector
            .connect(&origin.host, tcp)
            .map_err(|e| format!("TLS connection failed: {e}"))?;
        stream
            .write_all(header_block.as_bytes())
            .map_err(|e| format!("Request failed: {e}"))?;
        if let Some(body) = body {
            stream
                .write_all(body)
                .map_err(|e| format!("Request body failed: {e}"))?;
        }
        read_http_response(&mut stream)
    } else {
        tcp.write_all(header_block.as_bytes())
            .map_err(|e| format!("Request failed: {e}"))?;
        if let Some(body) = body {
            tcp.write_all(body)
                .map_err(|e| format!("Request body failed: {e}"))?;
        }
        read_http_response(&mut tcp)
    }
}

fn read_http_response(stream: &mut dyn Read) -> Result<HttpResponse, String> {
    let mut buffer = Vec::new();
    let mut chunk = [0u8; 4096];
    loop {
        match stream.read(&mut chunk) {
            Ok(0) => break,
            Ok(n) => buffer.extend_from_slice(&chunk[..n]),
            Err(err) if err.kind() == std::io::ErrorKind::WouldBlock => break,
            Err(err) => return Err(format!("Response read failed: {err}")),
        }
        if buffer.len() > MAX_BLOB_BYTES + 8192 {
            return Err("Response too large.".into());
        }
    }

    let split = buffer
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or_else(|| "No HTTP response from derived Blossom endpoint.".to_string())?;
    let header_bytes = &buffer[..split];
    let body_bytes = buffer[(split + 4)..].to_vec();

    let header_text = String::from_utf8_lossy(header_bytes);
    let mut lines = header_text.lines();
    let status_line = lines.next().unwrap_or_default().trim().to_string();
    let status_code = status_line
        .split_whitespace()
        .nth(1)
        .and_then(|code| code.parse::<u16>().ok())
        .unwrap_or(0);
    let header_lines: Vec<String> = lines.map(|line| line.to_string()).collect();

    let content_length = header_lines.iter().find_map(|line| {
        let (name, value) = line.split_once(':')?;
        if name.trim().eq_ignore_ascii_case("content-length") {
            value.trim().parse::<usize>().ok()
        } else {
            None
        }
    });

    let body = if let Some(expected) = content_length {
        if body_bytes.len() >= expected {
            body_bytes[..expected].to_vec()
        } else {
            body_bytes
        }
    } else {
        body_bytes
    };

    Ok(HttpResponse {
        status_line,
        status_code,
        header_lines,
        body,
    })
}

struct ParsedHttpOrigin {
    https: bool,
    host: String,
    host_header: String,
    port: Option<u16>,
}

struct ParsedHttpUrl {
    https: bool,
    host: String,
    host_header: String,
    port: Option<u16>,
    path: Option<String>,
}

fn parse_http_origin(origin: &str) -> Option<ParsedHttpOrigin> {
    let trimmed = origin.trim().trim_end_matches('/');
    let (https, rest) = if let Some(rest) = trimmed.strip_prefix("https://") {
        (true, rest)
    } else if let Some(rest) = trimmed.strip_prefix("http://") {
        (false, rest)
    } else {
        return None;
    };
    let authority = rest.split('/').next().unwrap_or_default();
    if authority.is_empty() {
        return None;
    }
    let (host, port) = parse_host_port(authority)?;
    Some(ParsedHttpOrigin {
        https,
        host,
        host_header: authority.to_string(),
        port,
    })
}

fn parse_http_url(url: &str) -> Option<ParsedHttpUrl> {
    let trimmed = url.trim();
    let (https, rest) = if let Some(rest) = trimmed.strip_prefix("https://") {
        (true, rest)
    } else if let Some(rest) = trimmed.strip_prefix("http://") {
        (false, rest)
    } else {
        return None;
    };
    let slash = rest.find('/').unwrap_or(rest.len());
    let authority = &rest[..slash];
    if authority.is_empty() {
        return None;
    }
    let path = if slash < rest.len() {
        Some(rest[slash..].to_string())
    } else {
        None
    };
    let (host, port) = parse_host_port(authority)?;
    Some(ParsedHttpUrl {
        https,
        host,
        host_header: authority.to_string(),
        port,
        path,
    })
}

fn parse_host_port(authority: &str) -> Option<(String, Option<u16>)> {
    let (host, port) = authority
        .rsplit_once(':')
        .and_then(|(host, raw_port)| {
            raw_port
                .parse::<u16>()
                .ok()
                .map(|port| (host, Some(port)))
        })
        .unwrap_or((authority, None));
    if host.is_empty() {
        return None;
    }
    Some((host.trim_matches(['[', ']']).to_string(), port))
}

fn origins_match(origin: &ParsedHttpOrigin, url: &ParsedHttpUrl) -> bool {
    origin.https == url.https
        && origin.host.eq_ignore_ascii_case(&url.host)
        && origin.port == url.port
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_relay_url_to_blossom_origin() {
        assert_eq!(
            blossom_origin_from_relay_url("wss://10.0.0.47:49748"),
            Some("https://10.0.0.47:49748".into())
        );
        assert_eq!(
            blossom_origin_from_relay_url("ws://erv-relay.startos"),
            Some("http://erv-relay.startos".into())
        );
    }

    #[test]
    fn allowlist_matches_blob_host() {
        let allowed = vec!["https://10.0.0.47:49748".into()];
        assert!(is_allowed_blossom_blob_url(
            "https://10.0.0.47:49748/abc123",
            &allowed
        ));
        assert!(!is_allowed_blossom_blob_url(
            "https://evil.example/abc123",
            &allowed
        ));
    }

    #[test]
    fn builds_blossom_auth_header() {
        let keys = nostr::Keys::generate();
        let header = build_blossom_upload_auth(&keys, EMPTY_BODY_SHA256_HEX).expect("auth");
        assert!(header.starts_with("Nostr "));
    }
}
