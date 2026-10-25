# Cypherpunk privacy checklist

Prioritized engineering work aligned with Eric Hughes’s [Cypherpunk Manifesto](https://www.activism.net/cypherpunk/manifesto.html): **privacy as selective revelation**, **minimum disclosure**, **strong cryptography**, and **systems users run themselves**.

North star: treat every outbound byte like cash — **anonymous and minimal unless the user deliberately signs their name**.

Last updated: July 2026.

**Related:** [Privacy policy](../policies/PRIVACY_POLICY.md) · [SECURITY.md](../../SECURITY.md) · [Zapstore release checklist](../release/ZAPSTORE_RELEASE_CHECKLIST.md) · [PHASES.md](../PHASES.md)

---

## Priority legend

| Tier | When | Meaning |
|------|------|---------|
| **P0** | Now | Wrong defaults or silent trust expansion; fix before wider release |
| **P1** | Phase 2 tail / pre–Phase 3 | UX honesty and metadata clarity |
| **P2** | Phase 3 | Local boundary hardening |
| **P3** | Phase 4+ | AI, graph sharing, advanced network privacy |

---

## P0 — Remove default public relays (Android)

**Status:** Shipped (July 2026).

### Current behavior (remove)

| Location | What it does |
|----------|----------------|
| `KeyManager.DEFAULT_RELAYS` | `damus.io`, `nos.lol`, `nostr.band` |
| `KeyManager.generateKeys()` | Persists all three as data + social relays |
| `KeyManager.populateDefaultRelaysIfStillEmpty()` | Same fallback after login |
| `KeyManager.relayUrlsForPool()` | Connects to defaults when saved list is empty |
| `runPostLoginSetup()` | Uses pool fallback → NIP-65 + `erv/settings` fetch → `populateDefaultRelaysIfStillEmpty()` |
| `Nip65.bootstrapRelays` | Alias of `DEFAULT_RELAYS` (unused directly today; pool fallback covers bootstrap) |

### Target behavior

1. **No persisted public relays** — empty relay list until the user adds one.
2. **No silent pool fallback** — `relayUrlsForPool()` returns `emptyList()` when nothing is saved.
3. **Onboarding requires an explicit relay** — user adds `wss://…` (Start9/Haven, self-hosted, or a relay they trust) before encrypted sync or post-login import runs.
4. **Web companion unchanged** — Start9 setup already prefills via `ERV_SUGGESTED_RELAY_URL` / detected LAN relay (`apps/web/server/src/config.rs`), not public defaults.

### Bootstrap chicken-and-egg (post-login import)

Today, a returning user with `erv/settings` on a **private** relay relied on public defaults to connect long enough to fetch NIP-65 / encrypted settings. Without defaults:

| Step | Change |
|------|--------|
| Fresh key | Relay setup screen → user adds relay → Continue (already gated on `allRelays.isNotEmpty()`) |
| Import nsec (existing user) | Same: add at least one relay they control **before** `runPostLoginSetup` can pull `erv/settings` |
| NIP-65 social import | Optional; only works after user connects to a relay that has their kind 10002 (if they published one) |

**Do not** replace dead public defaults with a new hidden public list. If bootstrap is ever needed, it must be **transient, user-visible, and never persisted** — prefer requiring the user’s relay URL up front.

### Relay UI buttons — what stays vs what goes

| Control | Obsolete? | Notes |
|---------|-----------|-------|
| **Use Data Relay Blossom** | **No** | Derives Blossom HTTPS origin from **user-configured data relays** (`BlossomEndpoints::originFromRelayUrl`). More useful when users run Haven/Start9 on the same host as their relay. |
| **Load from my Nostr profile** (kind 10063) | **No** | Fetches Blossom server list from **connected relays the user configured**. Requires at least one live relay — correct. |
| **Save** (publishes `erv/settings`) | **No** | Encrypted kind 30078 backup of relay roles — core multi-device story. |
| **Force resync** (Sync section) | **No** | Re-publishes current local payloads to data relays. |
| `populateDefaultRelays()` / `populateDefaultRelaysIfStillEmpty()` | **Yes — remove** | Replaced by explicit user relay entry. |
| Empty-state copy: “fetch from network” | **Revise** | There is no manual “fetch relays” button today; import runs automatically **after** login if a relay is connected. Copy should say: add a relay, then sign in / save to import from `erv/settings` or NIP-65. |

Optional follow-up (P1): add an explicit **“Import relay settings from network”** button that runs `SettingsSync.fetchFromNetwork` + NIP-65 fetch against connected relays — makes the empty state honest and gives returning users a manual retry without re-login.

### Implementation checklist

- [x] Remove `DEFAULT_RELAYS` constant and `populateDefaultRelays*`
- [x] `generateKeys()` — do not auto-add relays; land user on relay setup
- [x] `relayUrlsForPool()` — return empty when unset; callers handle gracefully
- [x] `runPostLoginSetup()` — skip network fetch when no relays; do not fall back to public list
- [x] Relay onboarding **Continue** — import `erv/settings` after user adds a relay
- [x] Update onboarding / Settings copy (empty relay state, first-run)
- [x] Unit tests: pool URL resolution, post-login with empty relays (`RelayPublishTargetsTest`)
- [x] Store listing + privacy policy: “you choose relays; ERV does not ship public relay defaults”

---

## P1 — Sync vs share clarity

**Phase:** 2 tail / store polish  
**Status:** Shipped (July 2026).  
**Manifesto:** selective revelation; identity not salient for backup.

- [x] Visually separate **encrypted sync** (data relays, kind 30078) from **public share** (social relays, kind 1) in Settings and post-workout flows
- [x] Default **route image attach** off; plain-language warning before any public Blossom URL is written to a kind 1 note
- [x] Onboarding card: “Encrypted backup ≠ social post” with one example each
- [ ] Consider minimal public summaries (duration + muscle groups) vs full exercise/weight detail in kind 1

---

## P1 — Metadata disclosure

**Phase:** 2 tail  
**Status:** Shipped (July 2026).  
**Manifesto:** minimum necessary disclosure; pseudonymous ≠ anonymous.

- [x] In-app **“What relays can see”** note: pubkey, d-tags, timestamps, event sizes, IP/connection patterns — even when content is NIP-44 encrypted
- [x] Recommend **self-hosted or private relay** in onboarding (Start9/Haven path for companion users)
- [x] Keep **Never publish NIP-65** default; explain tradeoff when user turns it off
- [x] Privacy policy § optional Nostr sync — add metadata paragraph (mirror in-app)

---

## P1 — Manual relay / settings import button (optional)

**Phase:** 2 tail  
**Status:** Shipped (July 2026).  
**Depends on:** P0 default relay removal.

- [x] Settings → Relays: **“Import from network”** — `SettingsSync.fetchFromNetwork` + optional NIP-65 merge when ≥1 relay connected
- [x] Snackbar outcomes: imported / nothing found / not connected
- [x] Replaces misleading passive “fetch from network” empty-state text

---

## P2 — Encrypted export

**Phase:** 3  
**Manifesto:** envelopes, not postcards.  
**Tracks:** [Zapstore checklist § Nice to have](../release/ZAPSTORE_RELEASE_CHECKLIST.md) — optional encrypted export archives.

- [ ] Password-protected export (Argon2id + AEAD, same spirit as web `crypto.rs`)
- [ ] Export dialog: plaintext vs encrypted; default encrypted for full backup
- [ ] Document recovery (passphrase loss = data loss)

---

## P2 — Local data at rest

**Phase:** 3  
**Manifesto:** encrypting signals intent for privacy.

- [ ] Threat model doc: unlocked phone, shared device, backup via ERV export only
- [ ] Encrypt sensitive silos at rest (health logs, body-tracker metadata) — Keystore-wrapped keys
- [ ] Optional **app lock** (biometric / device credential) before open, export, or key display ([Zapstore checklist](../release/ZAPSTORE_RELEASE_CHECKLIST.md))
- [ ] Optional `FLAG_SECURE` on key/export surfaces

---

## P2 — Selective sync (per silo) — declined

**Status:** Won't ship (July 2026).

**Rationale:** ERV's model is encrypted kind 30078 backup to **data relays you choose**. If you run a private relay (Start9, Haven, self-hosted), silo-level “sync this / not that” toggles do not meaningfully reduce disclosure — the relay already sees your pubkey and encrypted event metadata either way. They add settings surface, outbox edge cases, and “why isn’t my cardio on my other phone?” support burden without a clear win.

**Instead:** keep sync **all-or-nothing per relay role** (data vs social). Users who want device-only data can stay **local-only** (no Nostr sign-in / no data relays). Per-silo **delete** and **export** remain for data control without complicating relay publish.

- [x] Declined — per-category “sync to relay” toggles
- [x] Declined — UI for local-only silos vs queued publish

---

## P3 — Phase 4 AI hardening

**Phase:** 4  
**Manifesto:** don’t trust institutions; minimum context.  
**Spec:** [AI_COACH_MAPLE_INTEGRATION.md](AI_COACH_MAPLE_INTEGRATION.md) §8 (controls), [PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) §6, [ATHLETE_CONTEXT_WEB_PREP.md](ATHLETE_CONTEXT_WEB_PREP.md) §10.

- [ ] AI **off** by default; master toggle in web Settings
- [ ] **Context preview** before every generation (what leaves the Start9 box)
- [ ] Provider labels: **LAN / self-hosted** vs **Maple TEE** vs **custom cloud**
- [ ] `AiContextBuilder` — aggregates only; no raw day-log dump when snapshot suffices
- [ ] Hard rule (already spec’d): draft only, **never auto-publish** to Nostr
- [ ] Prefer `OPENAI_COMPAT` → local llama on StartOS over remote cloud
- [ ] All model calls relayed by the server (`/api/ai/*`); browser never holds the Maple key or reaches the proxy directly
- [ ] Server redaction check rejects contexts containing `nsec1…`, `npub1…`, relay or media URLs
- [ ] Server logs request metadata only (task, model, duration, status) — never prompt or response bodies
- [ ] Optional ERV-held Maple key sealed with an nsec-derived KEK; usable only while unlocked; returned masked

---

## P3 — Network privacy (advanced)

**Phase:** 4+

- [ ] Optional Tor / SOCKS proxy for relay WebSocket connections
- [ ] Document relay operator trust model for home lab vs public relay

---

## P3 — Protocol graph / WOT sharing

**Phase:** 4+  
**Vision:** [PROTOCOL_GRAPH.md](../archive/vision/PROTOCOL_GRAPH.md)

- [ ] Default **private**; sharing is opt-in per routine/workout
- [ ] Fork lineage visible; no accidental publish of health logs with shared templates
- [ ] WOT affects discovery only — not a truth oracle

---

## Already aligned (maintain)

| Area | Evidence |
|------|----------|
| Local-first default | No ERV cloud; Android local-only mode |
| Wire encryption | NIP-44 kind 30078; Blossom blob encryption |
| Key handling | Android Keystore + EncryptedSharedPreferences; web sealed nsec |
| No first-party tracking | No ads / analytics SDKs in repo |
| Honest deletion | Best-effort relay replace; policy states limits |
| Open, dispersible code | MIT license; Start9 `.s9pk`; user-chosen relays |
| Android backup off | `backup_rules.xml` excludes all app data |
| NIP-65 publish off by default | `neverPublishNip65RelayList` |
| Kind 1 social-only publish | `Kind1SocialShare` + `relayUrlsForKind1Publish()` |
| Share preview before post | `WorkoutKind1SharePreviewDialog` |
| Route image default off | `UserPreferences` / settings copy |

---

## Suggested sequencing

```
P0  Remove default relays + fix bootstrap UX
    ↓
P1  Sync/share separation + metadata copy (+ optional Import from network button)
    ↓
P2  Encrypted export + at-rest encryption + app lock  (Phase 3)
    ↓
P3  AI hardening + Tor + protocol graph                    (Phase 4+)
```

---

## Test plan (P0 relay change)

1. **Fresh install → generate keys** — no relays until user adds one; cannot sync until then.
2. **Fresh install → import nsec** — relay setup before post-login fetch; with user’s private relay configured, `erv/settings` restores data/social roles.
3. **Existing user upgrade** — if they only had default relays, show empty list + prompt to add relay (migration note in release notes).
4. **Use Data Relay Blossom** — with `wss://haven…` data relay, button still fills Blossom public URL.
5. **Load from my Nostr profile** — still works when ≥1 relay connected.
6. **No background connection** to `damus.io` / `nostr.band` / `nos.lol` when relay list empty.
