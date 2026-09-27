# AI coach — Maple Proxy integration (Start9 web companion)

Planning spec for connecting the ERV Start9 web companion to a **Maple Proxy** service running on the
same StartOS server, and using it for **training analysis** and **workout change / update
recommendations**. This is the concrete implementation plan for the "web-only AI" line item in
Phase 4.

**Scope:** `apps/web/web` + `apps/web/server` + `packages/start9` only. Android is untouched in
this initiative (it keeps syncing saved workouts/plans and running live sessions). Anything that
would require Android changes is listed under [§11 Later](#11-later--out-of-scope-for-this-initiative).

**Related:** [PHASES.md](../PHASES.md) · [PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) §6 ·
[ATHLETE_CONTEXT_WEB_PREP.md](ATHLETE_CONTEXT_WEB_PREP.md) §10 · [CYPHERPUNK_PRIVACY_CHECKLIST.md](CYPHERPUNK_PRIVACY_CHECKLIST.md) P3 ·
[USER_AGENT_NOSTR_AUTHORIZATION.md](../archive/planning/USER_AGENT_NOSTR_AUTHORIZATION.md)

Last updated: September 2026. Status: **M3 implemented** — Progress streams a sectioned coach review from the selected period. Workout chat stays later.

---

## 0. Recommendations (read this first)

1. **Route every model call through the Rust server (`/api/ai/*`).** The SPA never talks to Maple
   directly. The browser cannot reach `maple-proxy.startos` (container network only), the API key
   must never be in the browser, and the server is where single-flight queueing, size caps,
   timeouts, and the "never log prompts" rule are enforced. This matches the existing rule that
   the SPA goes through `/api` for relays.
2. **Build the context in the web app, not the server.** Everything the model needs already exists
   in TypeScript (`trainingContextBundle.ts`, `trainingSnapshot.ts`, `aiProgressionGuardrails.ts`).
   The web builds the exact JSON that will be sent, shows it in a **context preview**, and posts it
   to the server. The server is a policy-enforcing relay, not a second analytics engine. Do not
   port snapshot logic to Rust.
3. **Detect Maple Proxy the same way ERV detects Haven.** Probe the `maple-proxy` package's `api`
   interface from `packages/start9/startos/main.ts` and pass it to the server as env. Pre-fill the
   endpoint; still allow a manual URL for desktop Maple Proxy or any OpenAI-compatible server.
4. **Model selection is a first-class setting sourced from `GET /v1/models`.** Never hardcode model
   ids. Store the chosen id; allow a separate (cheaper) model for analysis vs generation.
5. **Prefer "key lives in Maple Proxy".** The StartOS package's Configure action already stores the
   Maple API key. Default ERV to *send no `Authorization` header*. Offer an optional per-request key
   stored **sealed** (see §6.3) for users who left the proxy keyless.
6. **Ship analysis before generation.** Read-only "Coach review" on the Progress tab cannot corrupt
   the library and proves the pipeline (settings → context → model → render). Workout
   recommendations come next and reuse the same plumbing.
7. **The model returns a full replacement `Workout` inside the import envelope, not a JSON patch.**
   Validate that envelope, then show it beside the current workout. The user chooses which current
   exercises to remove and which suggested exercises to add; unchanged current exercises stay.
   Saving writes that selection as a draft. Paste-import (M0) still replaces the whole workout by
   id. The picker is the AI preview (M4), and the exact controls can be iterated.
8. **Draft only. Never auto-publish to Nostr.** AI output lands as a local draft in the builder with
   `sourceLabel: "AI · Maple"`; the user saves and publishes explicitly, exactly like a manual edit.
9. **AI is off by default and every generation shows what leaves the box.** Maple runs in a TEE off
   the StartOS server and bills the user's Maple account. Say so in the UI.
10. **Land the web workout import preview first (M0).** It is unchecked in the merge doc and every
    AI draft depends on it.

---

## 1. What the user gets

| Feature | Where | Input | Output | Milestone |
|---------|-------|-------|--------|-----------|
| **F1 AI settings** | Settings → AI Coach | Endpoint (auto-detected), key mode, model picker, context level | Connection test + model list | M1 |
| **F2 Coach review** (analysis) | Progress tab | Profile + snapshot + guardrails + recent history summary; period selector | Read-only narrative with fixed sections; cached until logs change | M3 |
| **F3 Suggest changes** | Workout Builder, on a saved workout | Current workout JSON + context + free-text request ("make Wednesday shorter") | Replacement `Workout` + rationale → diff preview → save as draft | M4 |
| **F4 Generate workout** | Workout Builder "Create with AI" | Free-text prompt + context | New `Workout` → builder preview → save | M4 |
| **F5 Plan recommendations** | Planner tab | Current `erv/programs/master` week + context | Suggested week (workout ids per day) → preview → publish | M5 |
| **F6 Insights synced to Android** | — | — | `erv/insights/*` documents | Later (§11) |

**Analysis** = observes and advises; it never writes to the library.
**Recommendations** = structured drafts that must pass validation + guardrails + user preview.

---

## 2. Decisions already made (from existing docs)

These are inherited and not re-opened here:

- AI is **optional, off by default**, web companion only ([merge doc §6](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md)).
- Providers: `OFF`, `MAPLE`, `OPENAI_COMPAT` — one OpenAI-compatible client serves both.
- Output targets the **existing import contracts** (workout envelope `ervWorkoutImportVersion: 1`,
  `ProgramImportEnvelope`); no second schema.
- `AiContextBuilder` reads profile + snapshot + equipment + catalogs + saved ids; ephemeral prompt
  appended at generate time ([web prep §10](ATHLETE_CONTEXT_WEB_PREP.md)).
- Progression guardrails are deterministic and HR is optional evidence.
- **Never auto-publish** AI output to Nostr.
- Android does not host prompts, providers, queues, or validators.

Corrections to older notes:

- Maple Proxy supports **both streaming and non-streaming** responses (the merge doc said
  streaming only). Use non-streaming for structured JSON drafts, streaming SSE for narrative
  analysis.
- Upstream Maple Proxy development moved to the `MaplePrivacyLabs/Maple` monorepo (`proxy/`). The
  StartOS wrapper pins the historical `ghcr.io/opensecretcloud/maple-proxy` image. Verify the
  wrapper is current before recommending it in release notes.

---

## 3. Maple Proxy on StartOS — facts the design depends on

| Item | Value | Source |
|------|-------|--------|
| Package id | `maple-proxy` | `islandbitcoin/maple-proxy-startos` README |
| API interface | id `api`, port **8080**, HTTP | `startos/interfaces.ts` |
| Internal URL from ERV container | `http://maple-proxy.startos:8080` | same pattern as `ws://haven.startos:3355` |
| Endpoints | `GET /health`, `GET /v1/models`, `POST /v1/chat/completions`, `POST /v1/embeddings` | README |
| Auth | Key configured in package **Configure** action (`store.json`), **or** per-request `Authorization: Bearer` | README |
| Streaming | `stream: true` → SSE; `stream: false` → single JSON | upstream README |
| Billing | Metered to the user's Maple account (Pro/Team/Max credits). Some models are plan-gated → expect `402`/`403` | Maple docs |
| Privacy | Prompts leave the StartOS box encrypted to Maple's TEE enclave | Maple docs |

Model ids change over time. Populate from `/v1/models`. Example ids seen in 2026: `gpt-oss-120b`,
`gemma4-31b`, `llama3-3-70b`, `deepseek-v4-flash`, `kimi-k2-6`, `glm-5-2`. Pricing is **not**
returned by `/v1/models` — link to Maple's pricing page rather than hardcoding numbers.

---

## 4. Architecture

```
Browser (SPA)                      erv-web (Rust, Axum)                  maple-proxy (StartOS sibling)
─────────────                      ────────────────────                  ─────────────────────────────
AiContextBuilder (TS)  ──JSON──▶   /api/ai/*                             http://maple-proxy.startos:8080
  profile+snapshot+                  • require unlocked session            • /v1/models
  guardrails+catalog ids             • settings from state.json            • /v1/chat/completions
  + task + user prompt               • size caps, redaction check  ──▶     • TEE → Maple backend
Context preview modal                • single-flight queue
Draft validator (TS)   ◀──JSON──     • timeout, SSE relay
  → import envelope                  • no prompt logging
  → guardrails
  → builder preview (diff)
  → Save (local) → Publish (explicit)
```

### 4.1 Responsibilities

| Layer | Owns | Does not own |
|-------|------|--------------|
| **StartOS package** (`main.ts`, `manifest`) | Probe `maple-proxy` `api` interface → `ERV_DETECTED_AI_ENDPOINTS_JSON`; optional dependency metadata | Any request logic |
| **Server** (`apps/web/server`) | AI settings persistence, sealed key, provider client, `/api/ai/*`, queue, caps, SSE relay, model list cache | Context assembly, output validation, UI |
| **Web** (`apps/web/web`) | Settings UI, context builder + preview, prompt templates, draft validation, guardrails, diff preview, caching of analysis | Direct network calls to Maple |

### 4.2 Why the server relays instead of the browser calling Maple

- `maple-proxy.startos` resolves only inside the StartOS container network.
- The Maple API key must never reach the browser.
- One place to enforce: unlocked session, single-flight, body size cap, timeout, model allowlist,
  no logging of prompt bodies.
- Identical pattern to relays today (`/api/nostr/*`).

### 4.3 Why the web builds context

- All parsing/analytics is TypeScript already (`trainingHistory.ts`, `trainingSnapshot.ts`,
  `trainingContextBundle.ts`, `aiProgressionGuardrails.ts`, `workoutPrescriptionHints.ts`).
- The context preview must show **exactly** what is sent. Building it client-side guarantees that.
- The server stays small and auditable; it checks the payload rather than composing it.

Server-side checks on the posted context: max bytes (e.g. 64 KB), reject any string that matches
`nsec1…`, `npub1…`, `wss://`/`ws://`, or `http(s)://` media URLs, reject unknown top-level keys.

---

## 5. Request / response contracts

### 5.1 Server endpoints (all require unlocked session)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/ai/settings` | Current AI settings (key masked) + detected endpoints |
| `PUT` | `/api/ai/settings` | Save provider, endpoint, key mode, model ids, context level |
| `POST` | `/api/ai/test` | `GET {base}/health` + `GET {base}/v1/models`; returns reachability, auth status, model list |
| `GET` | `/api/ai/models` | Cached model list (refresh on demand) |
| `POST` | `/api/ai/complete` | Non-streaming chat completion; returns `{ content, model, usage? }` |
| `POST` | `/api/ai/stream` | SSE relay of a streaming completion (analysis UX) |
| `POST` | `/api/ai/cancel` | Cancel the in-flight request |
| `GET` | `/api/ai/status` | `{ busy, queued, lastError }` |

Request body for `complete` / `stream`:

```json
{
  "task": "analysis | workout_suggest_changes | workout_generate | plan_suggest",
  "modelId": "optional override, else task default from settings",
  "context": { "ervTrainingContextVersion": 1, "...": "output of buildTrainingContextJson()" },
  "subject": { "workout": { "...": "current Workout when task is suggest_changes" } },
  "userPrompt": "free text (max 2,000 chars)",
  "responseFormat": "markdown | json"
}
```

The server owns the **system prompt** per task (§5.3) so prompt policy is versioned with the
binary and cannot be edited from the browser.

### 5.2 Output contracts

| Task | Format | Validation on web |
|------|--------|-------------------|
| `analysis` | Markdown with fixed H2 sections: Summary · What went well · Watch-outs · Suggested focus next week · Data gaps | Section presence; strip anything after an unknown H2; render as text (no HTML) |
| `workout_suggest_changes` | `{ "ervWorkoutImportVersion": 1, "workouts": [<full replacement>], "rationale": ["..."], "changes": ["..."] }` | Parse with the workout import envelope; **id must equal the source workout id**; every `exerciseId`/`catalogId`/`activity` must exist; guardrails on load/volume deltas vs source |
| `workout_generate` | Same envelope, new id, `sourceLabel: "AI · Maple"` | Same, plus equipment check |
| `plan_suggest` | `ProgramImportEnvelope` subset: 7 days × `workoutId[]` + notes | All `workoutId`s exist in library; no more than N sessions/week vs profile |

Post-processing rules (already in the merge doc, restated so they land in code):
strip markdown fences; use `response_format: { "type": "json_object" }` when the model accepts it
and fall back gracefully when it does not; on invalid JSON offer **one** automatic retry with the
validator errors appended, then surface errors in the preview.

### 5.3 System prompts (server-owned, versioned)

- `PROMPT_VERSION` constant recorded on every draft (`aiMeta.promptVersion`).
- Shared preamble: role (coach assistant for one athlete), hard rules (use only supplied ids,
  respect equipment + movement limits, no medical claims, advisory only), output format.
- Per-task addendum: analysis sections; change-request rules (minimal diff, preserve untouched
  segments, explain each change); generation rules (session length, split derivation per
  profile); plan rules.
- Style preset hints come from the **context**, not the prompt (`STYLE_PRESET_AI_HINTS` already
  exists in `trainingContextBundle.ts`).

---

## 6. Settings and secrets

### 6.1 Settings schema (`state.json` → new `ai` object)

Field names follow the rest of `state.json` (snake_case).

```json
"ai": {
  "enabled": false,
  "provider": "OFF",
  "base_url": "http://maple-proxy.startos:8080",
  "key_mode": "PROXY_HELD",
  "sealed_api_key": { "nonce_b64": "...", "ciphertext_b64": "..." },
  "analysis_model_id": "",
  "generation_model_id": "",
  "context_level": "STANDARD",
  "always_show_context_preview": true,
  "timeout_seconds": 120,
  "last_models_refresh_epoch_seconds": 0
}
```

`provider` is `OFF`, `MAPLE`, or `OPENAI_COMPAT`. `key_mode` is `PROXY_HELD` or `ERV_SEALED`.
`context_level` is `MINIMAL`, `STANDARD`, or `FULL`.

`context_level`: `MINIMAL` = profile + snapshot aggregates + ids; `STANDARD` = + guardrails + last
N session summaries (default); `FULL` = + per-exercise recent history rows. Raw day logs are never
sent.

### 6.2 Endpoint detection (StartOS)

Mirror `relayCandidates` in `packages/start9/startos/relay.ts`:

```ts
// ai.ts (planned)
export const aiEndpointCandidates = [
  { packageId: 'maple-proxy', interfaceId: 'api', internalPort: 8080, label: 'Maple Proxy', provider: 'MAPLE' },
]
```

`main.ts` probes with `sdk.serviceInterface.get(...)` and sets `ERV_DETECTED_AI_ENDPOINTS_JSON`.
`config.rs` exposes it to `/api/ai/settings` as `detectedEndpoints[]` so the Settings UI can
pre-fill (same UX as `DetectedRelayNotice` / `LocalRelayPicker`). Add `maple-proxy` to
`manifest.dependencies` as `optional: true` with title/icon metadata, like `haven`.

### 6.3 API key handling

| Mode | Where the key lives | ERV sends | Notes |
|------|---------------------|-----------|-------|
| `PROXY_HELD` (default) | Maple Proxy `store.json` via its Configure action | No `Authorization` header | ERV never sees the key |
| `ERV_SEALED` | `state.json` `ai.sealedApiKey` | `Authorization: Bearer …` | For keyless proxies / desktop Maple / custom servers |

Seal the key with a KEK derived from the **user's nsec** (HKDF over the unlocked secret, domain
tag `erv-ai-key-v1`), not from the passphrase. Consequences: usable only while the session is
unlocked (fine — all AI calls are interactive), and it **survives passphrase changes and the
"unlock with nsec" recovery flow**, which reseals only the nsec blob. Return the key masked
(`…last4`) from `GET /api/ai/settings`; never echo it in full.

### 6.4 Connection test

`POST /api/ai/test` → `{ reachable, healthOk, authOk, models[], error? }`. Map `401/403` to "key
missing or rejected", `402` to "Maple credits exhausted / plan-gated model", network errors to
"proxy not running — start Maple Proxy on StartOS".

---

## 7. UX

### 7.1 Settings → AI Coach section

- Master toggle **Enable AI coach** (off by default). Disclosure text under it: *"Prompts are
  processed off this server inside Maple's encrypted enclave and billed to your Maple account.
  ERV only sends aggregates you can preview."*
- Provider: Maple Proxy (detected) / Custom OpenAI-compatible URL.
- Key mode radio + masked key field (only when `ERV_SEALED`).
- **Test connection** button → status line + model list load.
- **Analysis model** and **Generation model** dropdowns populated from `/api/ai/models`, each with
  a "why two?" hint (cheaper model for routine reviews). Show the raw model id; link to Maple
  pricing.
- Context level radio + **Always show context preview** checkbox.
- Labels/section headers use `FieldLabel` / `SectionHeader` per the title-case rule.

### 7.2 Progress → Coach review card

- Hidden entirely when `ai.enabled` is false (show a one-line "Enable AI coach in Settings" hint
  instead, dismissible).
- Period selector reuses `HistoryPeriodWeeks`.
- **Review my training** → context preview modal (what is sent, byte count, model, estimated
  tokens) → confirm → streamed markdown into the card.
- Footer: model id, generated-at, prompt version, **Regenerate**, **Copy**.
- Cache last review per period in `localStorage` keyed by a hash of the context; invalidate when
  the hash changes.

### 7.3 Workout Builder → AI affordances

- On a saved workout: **Suggest changes** → prompt sheet (free text + quick chips: shorter,
  easier, harder, swap equipment, more cardio, deload) → context preview → generate →
  **side-by-side exercise picker**. The current workout is on one side and the suggestion on the
  other. Current exercises start kept; the user marks which ones to remove. Suggested exercises
  start unselected; the user marks which ones to add. Load and prescription changes on a kept
  exercise show as a changed row the user can accept or leave. *Save draft* writes that selection.
  *Discard* drops the suggestion. Paste-import does not use these checkboxes; it still replaces
  the whole workout after a read-only diff.
- Toolbar **Create with AI** → prompt → preview in the normal builder with an "AI draft" banner
  → *Save*.
- Drafts carry `sourceLabel: "AI · Maple"` and an `aiMeta` block (`modelId`, `promptVersion`,
  `generatedAtEpochSeconds`, `contextHash`). `aiMeta` is web-side metadata; if it is not part of
  the shared `Workout` schema it must be stripped before publish (see §12 open decisions).
- Existing publish flow unchanged: the user presses Publish; the outbox does the rest.

### 7.4 Planner → Suggest week (M5)

- Only after Phase 3 planner is stable on both platforms.
- Output maps to existing `workout` blocks by id; unknown ids are dropped with a warning; preview
  is the normal week grid with proposed cells highlighted.

---

## 8. Privacy and safety controls

| Control | Where |
|---------|-------|
| Off by default; explicit enable + disclosure | Settings |
| Context preview before each request (or per "always show" setting) | Web modal |
| Data minimization: aggregates only, no raw day logs, no identifiers, no media URLs | `AiContextBuilder` + server redaction check |
| No prompt/response bodies in server logs; log only task, model, duration, status | `ai_routes.rs` |
| Single-flight queue + cancel | Server |
| Body size cap, prompt length cap, timeout | Server |
| Model allowlist = last fetched `/v1/models` list | Server |
| Draft only; never auto-publish; provenance badge + `sourceLabel` | Web |
| Deterministic guardrails run on every draft before preview | `aiProgressionGuardrails.ts` |
| Provider label in UI: **Maple TEE (off-box, metered)** vs **LAN / self-hosted** | Settings + preview modal |
| Analysis is advisory text; UI never renders it as HTML or as instructions to the app | Web |

These satisfy the P3 items in [CYPHERPUNK_PRIVACY_CHECKLIST.md](CYPHERPUNK_PRIVACY_CHECKLIST.md).

---

## 9. Milestones and checklists

Each milestone is independently shippable as a StartOS package rev. This is the **only** checklist
for the AI coach; check boxes here as work lands and update the one-line milestone status table in
[PHASES.md](../PHASES.md).

### M0 — Prerequisites (no AI)

- [x] Web **workout import envelope** parser (`ervWorkoutImportVersion: 1`) + validation errors
      (mirror `WorkoutImport.kt` rules in `workouts_import_schema.md` §10)
- [x] Web **workout import preview** dialog with segment-level diff against an existing workout
      (read-only; whole-workout replace on publish)
- [x] Analysis context adds `contextLevel` and a stable `contextHash` (`lib/aiContext.ts`)
- [ ] Decide `aiMeta` placement (§12) and update `workoutTraining.ts` if it becomes schema

**Acceptance:** paste a hand-written workout envelope → preview → save → appears in builder.

### M1 — Detection + settings + connection test

- [x] `packages/start9/startos/ai.ts` candidates + `main.ts` probe → `ERV_DETECTED_AI_ENDPOINTS_JSON`
- [x] `manifest.dependencies.maple-proxy` (optional, metadata + icon)
- [x] `config.rs`: parse detected AI endpoints
- [x] `state.rs`: `ai` settings object + migration default (`enabled: false`)
- [x] `crypto.rs`: HKDF-from-nsec seal/open for `sealed_api_key`
- [x] `ai_routes.rs`: `GET/PUT /api/ai/settings`, `POST /api/ai/test`, `GET /api/ai/models`
- [x] Web `components/AiSettingsPanel.tsx` in Settings (`lib/api.ts` types)
- [x] Model dropdowns (analysis / generation) from `/api/ai/models`
- [x] Unit tests: settings round-trip, key masking, error mapping (401/402/network)

**Acceptance:** with Maple Proxy installed on StartOS, Settings pre-fills the endpoint, Test
connection succeeds, model list populates, chosen models persist across restart. With the proxy
stopped, the test reports "proxy not running".

### M2 — Server relay

- [x] `ai_provider.rs`: OpenAI-compatible client (reqwest or existing HTTP stack), non-streaming +
      SSE, `Authorization` only in `ERV_SEALED` mode
- [x] `ai_queue.rs`: single-flight, cancel, status
- [x] `POST /api/ai/complete`, `POST /api/ai/stream`, `POST /api/ai/cancel`, `GET /api/ai/status`
- [x] Server-owned system prompts per task + `PROMPT_VERSION`
- [x] Request validation: size caps, redaction check, model allowlist, unlocked session
- [x] Logging policy: metadata only
- [x] Web `lib/aiClient.ts` (typed calls, SSE reader, cancel)
- [x] Tests: redaction check, size caps, SSE parsing with a fake server

**Acceptance:** a dev script posts a minimal context and receives a completion; a payload containing
`nsec1…` is rejected with 400; two concurrent requests → second gets 409 busy.

### M3 — Coach review (analysis)

- [x] `AiContextBuilder` task wrapper for `analysis` (period, context level)
- [x] `ContextPreviewModal` (JSON viewer, size, model, provider label, disclosure)
- [x] `CoachReviewCard` on Progress: stream, sections, footer meta, regenerate, copy
- [x] Markdown section validation + plain-text rendering
- [x] `localStorage` cache keyed by context hash

**Acceptance:** Progress → Review my training → preview → streamed review appears; changing the
period or logging a new session on Android (after sync) invalidates the cache.

### M4 — Workout recommendations

- [ ] `AiDraftValidator` for `workout_suggest_changes` / `workout_generate` (envelope parse, id
      checks, equipment check, guardrail deltas, one auto-retry with errors)
- [ ] **Suggest changes** sheet + quick chips on saved workouts
- [ ] **Create with AI** entry in builder toolbar
- [ ] Side-by-side exercise picker: current workout vs suggestion. User selects current exercises
      to remove and suggested exercises to add (current items start kept; suggestions start off).
      Save the composed draft or discard. Iterate on the controls after the first version.
- [ ] Diff rows for prescription and load changes on exercises the user keeps
- [ ] "AI draft" banner + `sourceLabel` + `aiMeta`
- [ ] Publish path unchanged; confirm `aiMeta` handling per §12

**Acceptance:** ask for "shorter, no barbell" on a saved workout → side-by-side picker shows the
barbell items and the suggested replacements → user removes the barbell items and adds the chosen
replacements → save draft → publish → Android syncs the updated workout and runs it.

### M5 — Planner recommendations

- [ ] `plan_suggest` task, validator against library ids and profile days/week
- [ ] Planner "Suggest week" → highlighted preview → publish

**Acceptance:** Phase 3 acceptance test still passes after accepting an AI-suggested week.

### M6 — Hardening / release

- [ ] Release notes + `packages/start9/instructions.md` section on installing Maple Proxy
- [ ] Privacy policy update (off-box TEE processing when enabled)
- [ ] `docs/import/workouts_import_schema.md` §13 prompt hint aligned with server prompts
- [ ] Manual test checklist entries (proxy down, bad key, plan-gated model, oversized context)

---

## 10. Code map (planned)

| Area | Path |
|------|------|
| StartOS detection | `packages/start9/startos/ai.ts`, `main.ts`, `manifest/index.ts` |
| Server config/env | `apps/web/server/src/config.rs` |
| Server settings + seal | `apps/web/server/src/state.rs`, `crypto.rs` |
| Server provider client | `apps/web/server/src/ai_provider.rs` |
| Server queue | `apps/web/server/src/ai_queue.rs` |
| Server routes + prompts | `apps/web/server/src/ai_routes.rs`, `ai_prompts.rs` |
| Web settings | `apps/web/web/src/lib/aiSettings.ts`, `components/AiSettingsPanel.tsx` |
| Web client | `apps/web/web/src/lib/aiClient.ts` |
| Web context | `apps/web/web/src/lib/trainingContextBundle.ts` (extend), `lib/aiContext.ts` |
| Web validation | `apps/web/web/src/lib/aiDraftValidator.ts`, `lib/workoutImport.ts` (M0) |
| Web UI | `components/ContextPreviewModal.tsx`, `components/CoachReviewCard.tsx`, `components/WorkoutDiffView.tsx`, `routes/ProgressTab.tsx`, `routes/WorkoutsTab.tsx`, `routes/SettingsTab.tsx` |

Reuse, do not duplicate: `buildTrainingContextJson/Markdown`, `trainingContextCompleteness`,
`buildProgressionGuardrailContext`, `STYLE_PRESET_AI_HINTS`, `duplicateWorkoutWithProgression`,
`FieldLabel` / `SectionHeader`, `.card` / `.btn-primary`, `DetectedRelayNotice` pattern.

---

## 11. Later — out of scope for this initiative

Captured so they are not lost; none of these ship with M0–M6.

- **Workout builder chat**: a conversation beside the composer so the athlete can ask questions
  while a workout is open. The model already receives profile, equipment, history, and the
  current workout through the same context. This stays later; the Progress review ships first.
- **Insights synced to Android** (`erv/insights/YYYY-MM-DD`): web/server generates a daily
  recap, publishes an encrypted kind-30078 document, Android renders it read-only. Needs a new
  d-tag in `erv_tags.rs` and Android read support.
- **Unattended / scheduled generation**: today the nsec lives in memory only while unlocked (15 min
  idle). A nightly job needs either generate-on-open (no security change) or an opt-in operational
  key. Decision deferred.
- **Agent identity + scoped signer**: auto-generated agent key that signs insights, with an ERV
  d-tag allowlist enforced in a tuned signer module. First concrete instance of
  [USER_AGENT_NOSTR_AUTHORIZATION.md](../archive/planning/USER_AGENT_NOSTR_AUTHORIZATION.md).
- **Embeddings / semantic search** over history via `/v1/embeddings`.
- **Training Mode** dashboard view on Android that surfaces insights.

---

## 12. Open decisions (need the user's call before M0/M1)

| # | Decision | Options | Default if unanswered |
|---|----------|---------|-----------------------|
| 1 | Where does `aiMeta` live? | (a) Add optional `aiMeta` to the shared `Workout` schema (web + Android tolerate/ignore) · (b) Web-only sidecar in `localStorage`, stripped before publish | (b) — no sync-contract change |
| 2 | Two model settings or one? | Analysis + generation vs single model | Two |
| 3 | Context preview every time or per setting? | Always · "Don't show again" toggle | Toggle, default **on** |
| 4 | Retry policy on invalid JSON | 0 · 1 automatic retry with errors · manual only | 1 automatic |
| 5 | Analysis cache location | `localStorage` · server-side per npub | `localStorage` |
| 6 | Custom OpenAI-compatible endpoints in v1? | Ship with Maple only · allow custom URL from day one | Allow custom URL (same client), label as "LAN / self-hosted" |
| 7 | Streaming for drafts too? | JSON drafts non-streaming only · stream with progress | Non-streaming |

---

## 13. Risks

| Risk | Mitigation |
|------|------------|
| Maple Proxy StartOS wrapper lags upstream (image namespace move) | Detect via `/health`; document manual URL fallback; verify wrapper before each release note |
| Model returns invalid or hallucinated ids | Envelope validator + id checks + one retry + preview; never auto-publish |
| Cost surprises on the user's Maple plan | Cheaper default analysis model; context level default `STANDARD`; show token estimate in preview |
| Health data leaves the box | Off by default; preview; aggregates only; redaction check; disclosure copy |
| Long requests block other web work | Single-flight with cancel; 120 s timeout; UI shows busy state |
| Session idle-out mid-request | `touch_unlocked` on each AI call; surface "session expired" via existing `SessionWatcher` |
| Passphrase reset orphans a sealed API key | Seal with nsec-derived KEK (§6.3), not passphrase |
| Drafts diverge from Android schema | Reuse `workoutTraining.ts` types + import envelope; Android ignores unknown fields |
| Scope creep into Android / scheduling / agents | §11 list; PHASES.md keeps them in "Later" |

---

*M0 paste-import is in progress (parser + preview shipped; `contextLevel` / `contextHash` and the
`aiMeta` decision in §12 are still open). §12 defaults apply until answered: `aiMeta` stays a
web-only sidecar.*
