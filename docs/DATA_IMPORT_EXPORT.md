# ERV — Data import, export, and agent interoperability

This document describes how **Energy Radiance Vitality (ERV)** can support **moving data in and out** of the app, and how **external agents and tools** (including LLM-assisted workflows) can **author routines, programs, and protocols** in a controlled way. It stays consistent with [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md): local-first storage, optional Nostr sync, strong privacy, and **no requirement** to integrate proprietary platforms (e.g. Google Health Connect) for core workflows.

**Social discovery and forking** of protocols (web of trust, lineage) is described at a product level in [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md). **This document** is the right place to define the **technical contract**: what structured data ERV accepts so that imports, exports, and agent-generated bundles all speak the **same language**.

---

## 1. Goals

| Goal | Approach |
|------|------------|
| Users own their data | Offer **clear export** of what ERV stores (per silo or full bundle). |
| Optional migration from other tools | Prefer a **single canonical import format** rather than many vendor-specific parsers. |
| Agents can add routines / programs / protocols | Use the **same canonical bundle** as human import: validate → preview → merge; no silent writes. |
| Sustainable engineering | Avoid an open-ended commitment to **every** fitness app’s CSV quirks inside the client. |
| Trust and safety | **Validate** imports, offer **preview** and **merge policy**, avoid silent data loss. |
| Privacy | Document risks when users use **third-party tools** (including cloud LLMs) to transform exports or generate bundles. |

---

## 2. Reality check: there is no universal “fitness export” standard

- **GPS / cardio activities** often use **GPX**, **TCX**, or device **FIT** files; many tools support one or more of these.
- **Strength training** and **multi-domain health logs** (supplements, sauna, sleep, etc.) usually ship as **app-specific CSV/JSON**, **proprietary APIs**, or **platform health APIs**—not one file format everyone agrees on.

So ERV should treat **interoperability** as: **we define what we accept and emit**, and we document how users can **convert** other sources into that definition when needed.

---

## 3. Export (ERV → files the user keeps)

### 3.1 What “export” means in ERV

Export is a **snapshot** of user-owned health data the app already holds locally (and optionally reflects from the user’s relay). It is **not** a promise to mirror every internal implementation detail forever; version the bundle format (see §7).

Reasonable scopes:

- **Full bundle** — All silos the user has used (weight training, cardio, sleep, …) in one structured archive.
- **Per-silo export** — e.g. weight training + exercise library only; smaller and easier to share with a coach or script.

### 3.2 Suggested representation

- **Primary:** **JSON** (or **JSON Lines** for very large append-only logs) with a **top-level `ervExportVersion`** field and per-silo sections that align with the **encrypted JSON shapes** already described in [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md) (same field names where possible, so docs stay single-sourced conceptually).
- **Secondary:** **CSV** only where it genuinely helps humans (e.g. body-weight diary, simple tables)—optional and derived from the same canonical models.

### 3.3 Security of exported files

- Export files may contain **sensitive health data**. The app should:
  - Warn that files are **plaintext** unless the user encrypts them externally.
  - Optionally support **password-encrypted ZIP** or similar **later** if there is demand (not required for an initial export story).

### 3.4 Nostr as export

Publishing to the user’s **personal relay** is already a form of **off-device backup**. Export to file is still valuable for:

- **Offline archives**
- **Migration** to another device without relay setup
- **Inspection** and third-party tooling

The relationship should be explicit in UI copy: **relay sync ≠ complete archival strategy** unless the user also manages relay retention and account keys.

---

## 4. Import (files → ERV)

### 4.1 Principle: one canonical import contract

The app should implement **import** against **one** (or a small number of) **ERV-defined** formats—e.g. **`erv-import.json`** matching a documented schema—not against “whatever Strong exported last Tuesday.”

Benefits:

- **Testable** validation and preview.
- **Stable** maintenance burden.
- Clear **user expectation**: “If your file matches this spec, ERV can load it.”

### 4.2 Long tail: user-side conversion

For everything else (exports from other apps, messy spreadsheets, legacy CSV):

- Provide **documentation**: the **canonical import schema**, **examples**, and **non-goals** (e.g. “do not invent workouts”).
- Optionally provide a **prompt template** or short guide for users who want to use a **local or cloud LLM** to map an arbitrary export into the canonical JSON—**with explicit privacy warnings** (see §6.2).

ERV does **not** need to endorse a specific vendor; it only needs to publish **the target shape** and **validation rules**.

### 4.3 Import UX (recommended)

1. **Pick file** (user-initiated; no background scraping).
2. **Validate** against schema / required fields.
3. **Preview** — counts per silo, date range, obvious issues (unknown exercise IDs, duplicate days).
4. **Merge policy** — user chooses e.g. **merge** (default) vs **replace silo** vs **cancel**; destructive paths require confirmation.
5. **Backup hint** — recommend export **before** a large import.

### 4.4 Mapping challenges (why in-app “import from App X” explodes)

Examples that are costly to support natively for every source:

- Exercise names that **don’t match** ERV’s library; synonyms and user renames.
- **Supersets**, **dropsets**, **RPE**, **rest timers** encoded differently or missing.
- **Timezone** and “session date” vs “logged at” ambiguity.
- **Partial exports** from vendors that omit sets or merge rows.

A **canonical import** + **user-controlled mapping** keeps ERV honest: we implement **our** model correctly, not **everyone else’s** edge cases.

---

## 5. Agent-authored routines, programs, and protocols

“Agents” here means **any external system** that outputs structured data for ERV: LLM chat assistants, local scripts, coaches’ web tools, or future first-party automation. The **delivery mechanism** should be the same as **§4**: a **canonical JSON bundle** the user (or a future opt-in integration) brings into the app.

### 5.1 Why reuse the import contract

- **One schema to maintain** — Master lists and templates (routines, programs, protocols) use the same JSON shapes as in [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md): e.g. `erv/weight/routines`, `erv/cardio/routines`, `erv/heatcold/routines`, supplement device lists, etc.
- **Review before commit** — Agents do not get a **hidden write path**. The user sees **validation + preview + merge policy**, same as migration import.
- **Documentation doubles as an API** — A published **JSON Schema** (or equivalent) plus examples is enough for humans and agents to target **without** a bespoke server API in v1.

### 5.2 What agents might author (examples)

| Intent | Typical payload slice (per PLAN_OF_ACTION d-tags) |
|--------|---------------------------------------------------|
| Strength **program** / **routine templates** | `erv/weight/exercises` (new or referenced) + `erv/weight/routines` |
| Cardio **plans** | `erv/cardio/routines` + optional custom activity metadata |
| **Contrast** or **heat/cold** sequences | `erv/heatcold/routines` and linked session scaffolding |
| **Supplement** stacks | `erv/supplements/list` entries + optional schedule hints in daily logs if the spec allows |
| **Stretch** programs | `erv/stretching/routines` (and poses catalog if applicable) |

Exact field names and merge rules stay **authoritative** in PLAN_OF_ACTION and silo specs ([WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md), etc.). This document only states that **agent bundles** should **reuse those shapes** inside the import/export wrapper (see §8).

### 5.3 Prompting and tooling

- Ship **machine-readable schema** + **minimal valid examples** (one routine, one week block, etc.).
- Optionally ship a **prompt template** (“You are converting a natural-language program into ERV JSON…”) with **strict output rules**: valid JSON only, no commentary, unknown fields omitted, **do not fabricate** medical claims in `notes` beyond the user’s text.
- For **protocol lineage** and social forking, see [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md); the **import bundle** can still carry **provenance** in app-defined optional fields (e.g. `sourceLabel`, `parentProtocolId`) once those are added to the schema.

### 5.4 Relationship to a future API or relay features

If ERV later exposes **HTTP**, **Nostr app-specific events**, or a **website** that creates routines, those surfaces should **serialize/deserialize the same canonical types** so agents, importers, and the UI are not three divergent models.

---

## 6. Privacy and third-party conversion

### 6.1 File import path

Data stays **user-driven**: user selects a file generated on their device or downloaded from a provider. ERV does not need to call third-party APIs for this path.

### 6.2 AI-assisted conversion (documentation-only or helper scripts)

If users paste exports into **cloud LLMs**, **health data may leave the device**. Documentation should:

- State this plainly.
- Suggest **local/offline models** or **manual editing** when appropriate.
- Suggest **redaction** of fields they do not need to convert.

The same applies when users ask an **agent** to **author** a routine from free text: **program content** may be less sensitive than **years of logs**, but users should still understand **where** the model runs.

ERV can still provide **schema + examples** without operating a conversion service.

---

## 7. Versioning and compatibility

- **`ervExportVersion` / `ervImportVersion`** (or a single **`ervDataBundleVersion`**) should appear in JSON bundles.
- **Forward-compatible parsers:** ignore unknown fields where safe (`ignoreUnknownKeys`-style behavior in Kotlin is a good match).
- **Breaking changes:** bump major version; document migration notes in this file or a changelog section.

---

## 8. Relationship to Nostr event payloads

Where possible, **import/export JSON** should **reuse the same nested structures** as the encrypted content described in [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md) for each `erv/...` silo, so there is one conceptual model:

- **Export:** serialize local state (or decrypt-and-repack relay state) into the bundle.
- **Import:** validate, then **merge into local storage** and **publish** updated replaceable events if the user uses sync—subject to the same merge rules as normal app edits.

Exact merge semantics per silo remain defined in **PLAN_OF_ACTION** and silo specs (e.g. [WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md)).

---

## 9. Phasing (suggested)

| Phase | Deliverable |
|-------|-------------|
| **A** | **Export** full or per-silo JSON bundle; document format. |
| **B** | **Import** canonical JSON only: validate + preview + merge. |
| **C** | Optional **CSV** for simple silos; optional encrypted archive. |
| **D** | Published **conversion guide** (schema + examples + AI prompt template + privacy notes)—no obligation to parse vendor exports in-app. |
| **E** | Same bundle path documented for **agent-authored** routines/programs (§5); optional **JSON Schema** publish. |

Phases can overlap; **A + B** deliver most of the “data ownership” story; **D + E** complete the **agent interoperability** story without new runtime surfaces.

---

## 10. Non-goals (for the core app)

- **Native importers** for every commercial fitness app’s export format (unless explicitly chosen later with a maintenance budget).
- **Mandatory** use of Google Health Connect or similar as the **only** migration path.
- **Silent** overwrite of user data without confirmation.
- **Unreviewed** agent writes: autonomous agents that push routines **without** user confirmation in the ERV UI (unless explicitly designed later with strong consent and audit).

---

## 11. Shipped references (import docs)

These files are **copied into the app bundle** under **Settings → Import And Export**. They are written so **users and AI assistants** can map foreign exports → ERV import JSON/CSV **without** reading internal planning docs. (Repository-only **PLAN_OF_ACTION.md** remains for engineers.)

- **Weight training (implemented import):** **`docs/weight_training_import_ai_guide.md`** (JSON contract + optional §9 Nostr overview), **`docs/weight_training_import_csv_guide.md`**, **`docs/weight_training_builtin_exercise_ids.md`** (authoritative lift ids). Relay encoding uses kind **30078** and `erv/weight/...` d-tags—see **PLAN_OF_ACTION.md** / **PROTOCOL_GRAPH.md** if you need more than the short note in the AI guide.
- Sessions created from weight file import use `source`: **`IMPORTED`** in encrypted day payloads (`erv/weight/<date>`).
- **Relay uploads (weight + cardio import):** After local merge, payloads go through a durable **`RelayPublishOutbox`** (DataStore): enqueue **master + per-day** JSON, then **`kickDrain`** sends sequentially with **~150 ms** spacing and **exponential backoff** on failure. **`MainActivity`** also drains the outbox when relays/signer are available (e.g. after relay URL changes). **Local DataStore remains canonical**; failed sends stay queued and retry.
- **Cardio (implemented import):** **`docs/cardio_training_import_ai_guide.md`** (JSON, **built-in activity enum table**, Strava → ERV hints), **`docs/cardio_training_import_csv_guide.md`**, and **`docs/cardio_training_nostr_events_reference.md`** (*optional*—`d` tags / kind **30078** for relay sync, **not** required to author imports). JSON (primary) and ERV cardio CSV ship under **Import And Export** with the same preview/merge pattern as weight. Sessions use `source`: **`IMPORTED`**.

## 12. Open decisions

These are product/engineering choices to settle when implementing:

- **Minimum silo set** for v1 import/export (e.g. weight training + body weight first).
- **Identity of exercises** on import: match by **name**, **stable ID**, or **always create new** custom exercises.
- **Maximum file size** and streaming vs load-all for validation.
- Whether **import** also triggers **immediate relay publish** or waits for the next normal sync.
- **Provenance fields** for agent-authored programs (`sourceLabel`, `parentProtocolId`, fork lineage) and how they map to [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md) when that layer ships.

Document conclusions here as they are decided.
