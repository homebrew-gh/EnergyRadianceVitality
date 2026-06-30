# Programs + Unified Workouts — merge, unified UI, and optional AI

This document is the **authoritative plan** for combining ERV’s **Programs** (weekly schedules, strategy, habits) and **Unified Workouts** (multi-modality session templates and live run flow) into **one product surface**, then adding an **optional AI assistant** for building and editing plans and workouts.

It consolidates an earlier design discussion (June 2026) and extends it with **Maple Proxy** on StartOS as a first-class, optional AI backend. Related import contracts remain in [programs_import_ai_guide.md](../import/programs_import_ai_guide.md) and [DATA_IMPORT_EXPORT.md](../import/DATA_IMPORT_EXPORT.md).

---

## 1. Problem

Today ERV exposes two Launch Pad tiles that overlap in purpose:

| Surface today | What it models | Primary files |
|---------------|----------------|---------------|
| **Programs** | Multi-week **Plan**: ISO weekdays, blocks, strategy (manual / repeat / rotation / challenge), habit checklists, completion | `ProgramModels.kt`, `ProgramsScreens.kt` (~2,500 lines) |
| **Unified Workouts** | Single **session template**: ordered weight / cardio / stretch blocks, live run, HR, summary | `UnifiedRoutineModels.kt`, `UnifiedRoutineScreen.kt` |

A program day can express “today’s workout” **three different ways**:

1. Reference a unified routine (`unifiedRoutineId`)
2. Inline-compose weight / cardio / stretch blocks (duplicating unified block shape)
3. Reference silo routines (`weightRoutineId`, `cardioRoutineId`, …)

That yields **three authoring paths**, **two giant AlertDialog-heavy editors**, and **two completion models** (manual program checkmarks vs unified session completion). The UX is hard to learn; AI generation would inherit the same confusion if built on top of the split.

**Decision:** merge conceptually and in the UI **before** shipping in-app AI for program/workout authoring.

The [workout construction schema](../import/workouts_import_schema.md) is the **proof** that merge works: one **Workout** entity holds every modality ERV already tracks (weight, cardio, stretch, sauna/cold, red light), and one **Plan** only schedules those workouts plus habits/rest. That replaces today’s split between Programs inline blocks and Unified Workouts.

### 1.1 Current interim state (June 2026)

Merge is **not** complete. Phase 2 workout composer is in progress (~65%). Three coexisting surfaces:

| Surface | Models | Scheduling | Live run |
|---------|--------|------------|----------|
| **Programs** | `FitnessProgram`, inline blocks | ✅ strategy + habits | Silo refs / unified refs |
| **Unified Workouts** | `UnifiedRoutine` | ❌ no dates | ✅ unified pipeline |
| **Workouts (Training)** | `Workout` + segments | ❌ no dates | 🟡 new pipeline; weight ✅, cardio/mobility stubs |

**Canonical direction:** `Workout` replaces `UnifiedRoutine` over time; `Plan` will reference `workoutId` only (Phase 3). Until then, do **not** remove Programs or Unified Workouts tiles.

**Start9:** workout builder is the default web landing; silo routines remain library ingredients. See [START9_COMPANION_V1.md](START9_COMPANION_V1.md) Phase 2 and [WORKOUT_PLAN_EDITOR_SPEC.md §9.3](WORKOUT_PLAN_EDITOR_SPEC.md).

---

## 2. Target architecture

One hierarchy, two levels:

```
Plan  (= FitnessProgram: schedule + strategy + habits)
  └── ProgramWeekDay
        ├── workoutRef(s)  →  Workout by id
        └── plan-only items: habit checklist (OTHER), rest, custom notes

Workout  (= UnifiedRoutine, expanded: the ONE canonical session entity)
  └── segments[]: ordered flow with full **prescriptions**
        └── items from ALL ERV silos (see §2.3)
        └── see [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md)
```

### 2.3 One catalog — every silo in one composer

The merged builder must pull from **libraries the app already ships**, not a parallel exercise list:

| Silo | Pick in composer | References |
|------|------------------|------------|
| **Weight training** | Built-in + custom lifts | `erv-weight-exercise-*` ids; user `WeightExercise` library |
| **Cardio** | Built-in activities + saved cardio routines | `CardioBuiltinActivity` names; `cardioRoutineId` |
| **Stretching** | Bundled stretch catalog + saved stretch routines | `builtin_*` catalog ids; `stretchRoutineId` |
| **Hot + cold** | Sauna or cold plunge session | `HeatColdMode`: `SAUNA` \| `COLD_PLUNGE`; duration (and optional temp at run time) |
| **Light therapy** | User’s devices + saved light routines | `LightDevice.id`, `LightRoutine.id`; duration minutes |

A single workout can chain **any combination** — e.g. lift → sauna → red-light cooldown — in one storyboard and one unified live run. Programs never re-specify those modalities inline; they only assign the workout id for that day.

**Plan-only (not inside workout segments):** habit checklists (`OTHER`), rest days, free-form notes — stay on `Plan` days when they are not timed session work.

**Rules:**

- **Author a workout once; schedule it anywhere.** Plans reference workouts by id instead of re-implementing block editing.
- **Plans own scheduling and meta:** strategy engine, `activeProgramId`, habit checklists, rest/custom notes — unchanged in responsibility, slimmer in block authoring.
- **Workouts own session execution:** live run, block transitions, HR scaffolding, `UnifiedWorkoutSession`, summary — keep the unified run pipeline as the single execution path.
- **Completion:** a plan day’s workout block is **done** when a finished `UnifiedWorkoutSession` exists for that workout on that calendar day (via existing `attachLoggedBlock` / session links). Habit checklist lines keep `programChecklistCompletionKey`.

### 2.1 Workout block types (extend UnifiedRoutine)

Current `UnifiedRoutineBlockType` is only `WEIGHT | CARDIO | STRETCH`. Extend to match what program days already support for modalities:

| Block type | Purpose | Fields (conceptual) |
|------------|---------|---------------------|
| `WEIGHT` | Resistance | `weightExerciseIds`, `weightRoutineId` |
| `CARDIO` | Cardio timer / activity | `cardioActivity`, `cardioRoutineId`, `targetMinutes`, quick-launch fields |
| `STRETCH` | Mobility | `stretchRoutineId`, `stretchCatalogIds`, hold seconds |
| `HEAT_COLD` | Sauna / cold plunge | `heatColdMode`, `targetMinutes`, optional temp hints |
| `LIGHT` | Red/NIR or other light therapy | `lightDeviceId` and/or `lightRoutineId`, `targetMinutes` |
| `REST` | Recovery slot | `title`, `notes` |
| `FLEX` | User picks weight or cardio at launch | (replaces `flex_training` on program blocks) |

At run time, `HEAT_COLD` and `LIGHT` segments launch the **existing** Hot+Cold and Light Therapy timers/logging — same as if the user opened those categories standalone.

### 2.2 Slim program day model

After merge, `ProgramDayBlock` collapses to:

| Kind | Meaning |
|------|---------|
| `workout` | Reference `workoutId` (+ optional display title override) |
| `other` | Habit checklist (`checklistItems`) |
| `rest` | Plan-level rest / recovery note |
| `custom` | Free-form plan text |

Inline `weight`, `cardio`, `stretch_routine`, `stretch_catalog`, `heat_cold`, `unified_routine`, and `flex_training` program blocks become **deprecated**; migrate to workout references or plan-only kinds.

Keep `ProgramStrategy`, templates (75 Hard / Soft), and Nostr program master sync as-is at the Plan layer.

---

## 3. Unified UI (one section, not two tiles)

Replace separate **Programs** and **Unified Workouts** Launch Pad entries with **one** tile: **Planner** (name TBD — see [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md) §2).

### 3.1 Top-level segments (inside Planner)

| Segment | User job |
|---------|----------|
| **Week** | Primary view: all workouts for the week; drag-and-drop onto days |
| **Library** | Saved workouts, silo routines, catalogs, segment templates |
| **Composer** | Deep-edit one session — all silos (weight, cardio, stretch, sauna/cold, light) |
| **History** | Unified session summaries + plan completion (optional v2) |

**Today / Launch** moves to the **dashboard** as a read-only “planned for today” card (Phase 4 in workout spec §5.4) — not a separate top-level Programs sheet long term.

### 3.1 UI revamp principles (fix “clunky”)

Both current editors fail for the same reasons. The merged UI should:

- Use **full-screen builder routes** instead of stacked `AlertDialog`s (sheet → dialog → picker → sub-dialog).
- **Auto-save** or one explicit draft model — avoid “Save changes” on metadata while block reorder persists differently.
- **Drag-to-reorder** blocks instead of up/down icon buttons.
- One **inline block editor** — no separate “structure vs content” dialog modes.
- **Compact week grid** for plans — do not render seven empty day columns by default.
- **Run flow:** auto-advance to the next block where possible instead of a passive checklist that bounces to silo screens without context.
- Split `ProgramsScreens.kt` into focused files (`PlanBuilderScreen`, `WorkoutBuilderScreen`, `PlanTodaySheet`, …).

### 3.2 Navigation impact

- Collapse `category/programs` and unified workout category into one nav destination (or programs as parent, workouts as sub-route).
- Update `LaunchPadTileOrder`, onboarding (`FirstRunSetupScreen`), and dashboard tile copy.
- Rework `programBlocksForDate`, `UnifiedRoutineLaunchResolvers`, and dashboard deep links (`program_dashboard_unified_routine_launch_json_v1` in `UserPreferences`) to resolve **workout refs** only.

---

## 4. Migration (lazy, on-read)

Deterministic migration when loading legacy JSON:

1. For each `ProgramDayBlock` with inline modality kinds (`weight`, `cardio`, …), **auto-generate** a `Workout` whose blocks mirror the inline content; replace with a `workout` reference (`workoutId`).
2. Map `unified_routine` → same-id workout reference (unified routines become workouts; type rename only if needed).
3. Keep `other`, `rest`, `custom` on the plan day.
4. Map `flex_training` → workout containing a single `FLEX` block (or a small generated workout).
5. Preserve ids where possible so completion keys and Nostr events remain stable.
6. **Old JSON must still deserialize**; migration runs once per program on read, then saves normalized shape.

Add unit tests: sample legacy programs → expected workout library + slim plan.

---

## 5. Implementation sequencing

**Order (updated): merge first, then AI in the same section.**

| Phase | Scope | Ships |
|-------|--------|-------|
| **A — Data + migration** | Prescription schema (`WorkoutSegment`, `ExercisePrescription`); slim program blocks; lazy migration | Correct data under the hood; old UI still works |
| **B — Editor + merge UI** | **Workout Composer** + **Plan Calendar** ([editor spec](WORKOUT_PLAN_EDITOR_SPEC.md)); single Launch Pad tile; today/launch sheet | User-visible merge + rich authoring |
| **B5 — Start9 plan editor** | `erv-plan-editor-startos` SPA; JSON import/export; optional LAN sync | Desktop/tablet authoring on Start9 |
| **C — AI infrastructure** | Settings, OpenAI-compatible client, context builder, draft pipeline | Plumbing only |
| **D — AI in editor** | Provider selector in composer/calendar; full **prescription** generation (not exercise ids only) | AI-assisted build/edit |

Optional parallel track (not blocking merge): **weekly coaching digest** on Dashboard using the same AI infra from Phase C — see §7.4.

---

## 6. AI integration (Phase C + D)

AI is **optional**, **off by default**, and lives **inside the merged Plans & workouts section** — not a separate Settings-only workflow.

### 6.1 Provider selector

In the merged section (toolbar or “Create with AI” entry point), user picks:

| Provider | Role |
|----------|------|
| **None / Manual** | Default; existing hand-built editors only |
| **Maple Proxy** (recommended private cloud) | Start9 or desktop Maple Proxy; TEE-encrypted; uses Maple subscription credits |
| **Custom OpenAI-compatible** | Any endpoint: local llama.cpp, other StartOS LLM packages, LAN dev proxy |

Store per-provider settings in the Start9 web companion/server config (encrypted where needed):

- `aiEnabled` (master toggle)
- `aiProvider` enum: `OFF`, `MAPLE`, `OPENAI_COMPAT`
- `aiBaseUrl` (e.g. `http://start9-host:8080/v1` for Maple API interface)
- `aiApiKey` (optional if proxy/server stores key — Maple allows per-request `Authorization`)
- `aiModel` (from `GET /v1/models` or free-text)
- `aiTimeoutSeconds`

**Maple-specific notes:**

- Run [maple-proxy-startos](https://github.com/islandbitcoin/maple-proxy-startos) on Start9; API on port **8080**, optional Web UI on port **80**.
- Web companion targets `{baseUrl}/v1/chat/completions` with **streaming SSE** (Maple supports streaming only).
- Usage bills against Maple Pro/Team/Max credits; health prompts leave the device encrypted to Maple’s enclave — still disclose in UI.

**Custom backend notes:**

- llama.cpp and most local servers expose the same OpenAI-compatible API; reuse one client.
- Prefer **single-flight queue** (one request at a time) when the backend is a shared home CPU.
- Add a large-generation warning for full multi-week plans when the backend is a shared home CPU.

### 6.2 Web-only AI package

| Component | Responsibility |
|-----------|----------------|
| `OpenAiCompatibleClient` | Web/server POST `/v1/chat/completions`, SSE parse |
| `AiRequestQueue` | Single-flight + cancel + “busy” state |
| `AiContextBuilder` | Token-budgeted bundle: **training profile + snapshot** (see [ATHLETE_CONTEXT_WEB_PREP.md](ATHLETE_CONTEXT_WEB_PREP.md)), equipment, **weight/cardio/stretch catalogs**, saved workout ids, **light devices/routines**, heat/cold not cataloged (mode only) — reuses import reference bundle shapes |
| `AiDraftValidator` | Parse JSON → `ProgramImportEnvelope` / workout DTO; remap hallucinated exercise ids; surface errors for preview |
| `ProgressionGuardrails` | Deterministic policy + validators for load, reps, volume, recovery, and optional HR load evidence |
| `AiSettingsPanel` | Connection test (`GET /v1/models`, `GET /health` for Maple) |

**Hard rule:** AI output is always a **local draft**; user previews and saves. **Never auto-publish to Nostr.**
AI authoring is only implemented in the Start9 web companion. Android does not host prompts,
provider settings, generation queues, or draft validation; it only syncs saved workouts/plans and
runs live sessions after the user saves web-generated output.

Progression guardrails use HR only as optional evidence. Android day logs may include compact
`heartRate.load.zoneSeconds` summaries, but web generation must still work from reps, load, RPE/RIR,
volume, frequency, equipment, and profile when HR is missing or partial.

### 6.3 AI actions in the merged UI

Expose a consistent **“AI” affordance** (icon or segmented control) on web:

| Action | Input | Output |
|--------|-------|--------|
| **Generate workout** | Free-text (“45 min full body, dumbbells only”) + device context | Draft `Workout` → workout builder preview |
| **Generate plan** | Free-text (“8-week PPL, 4 days”) + context | Draft `Plan` (envelope with `programs[]`, optional `strategy`) → plan builder preview |
| **Edit existing** | Current workout/plan JSON + change request (“swap Wednesday to cardio”) | Patched draft → same preview flow |
| **Import assist** | Pasted export or notes | Same as generate; lands in existing import preview dialog |

Flow: **Prompt → queue → stream/progress → validate → full-screen preview (existing builder) → Save**.

Set `sourceLabel` e.g. `"AI · Maple"` or `"AI · local"`.

### 6.4 Reuse existing import contract

Do **not** invent a second schema. Generation targets:

- [programs_import_ai_guide.md](../import/programs_import_ai_guide.md) / `ProgramImportEnvelope` for plans
- A **workout import envelope** (new, minimal): `{ "ervWorkoutImportVersion": 1, "workouts": [ ... ] }` — see [workouts_import_schema.md](../import/workouts_import_schema.md)

Post-generation path reuses:

- `ProgramImport.parse()` + `ProgramImportPreviewDialog`
- New workout import preview mirroring the same validate → merge pattern

Update the AI guide bundle (`shareProgramsReferenceBundle`) to include **workout schema** once Phase A lands.

### 6.5 Prompt and reliability

- Inject **valid id catalogs** (built-in exercise ids, cardio enum names, stretch catalog ids, saved routine/workout ids) — same content as the Settings “reference bundle for AI”.
- Request **JSON-only** output; strip markdown fences if present.
- Optional: GBNF grammar / `response_format` for OpenAI-compatible servers that support it (local llama.cpp).
- **Post-validate** every id against on-device libraries; fuzzy-match names for unknown lifts and offer “create custom exercise” before save.
- Post-validate progression against deterministic guardrails before preview: no excessive load jumps, no uncontrolled weekly set spikes, no stacked HIIT days, and no HR-only load decisions.

---

## 7. Optional follow-ons (not blocking merge)

### 7.1 Prescriptions and import

The [Workout & plan editor spec](WORKOUT_PLAN_EDITOR_SPEC.md) adds **`ExercisePrescription`** (sets, reps, weight, intervals, rest). AI and import envelopes must target that schema — not the old program-only block lists. Deprecate silo `WeightRoutine` as the primary template; **Workout** is canonical.

### 7.2 Weekly digest

Independent web/server feature: scheduled digest job + `AiContextBuilder` + Dashboard card. Can ship after Phase C without waiting for Phase B. Digest suggestions may deep-link into “Generate adjusted plan” (Phase D) in the web companion.

### 7.3 Agent API / Nostr

Longer term: [USER_AGENT_NOSTR_AUTHORIZATION.md](../archive/planning/USER_AGENT_NOSTR_AUTHORIZATION.md) for user-owned agents. Web Maple/llama remains the **interactive authoring** path; external agents use the same JSON import envelopes.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Migration corrupts legacy programs | Lazy on-read + golden-file tests + keep deserializing old kinds until migration runs |
| Dashboard launch regressions | Explicit tests for `programBlocksForDate` / workout ref resolution |
| Completion semantics across devices | Unified session-based completion + existing `completionState` merge rules |
| Maple/streaming-only API | Client must use SSE; no non-streaming code path |
| Health data in prompts | Opt-in toggle; show what context is sent; prefer Maple/local over generic cloud |
| 14B/local model invalid JSON | Grammar + validator + preview before merge |
| Two tiles removed confuse existing users | Short in-app notice; onboarding copy update |

---

## 9. File touch list (implementation checklist)

**Models & data**

- [x] **`WorkoutModels.kt`** — `Workout`, segments, items, prescriptions (Phase 2; no `heat_cold`/`light` yet)
- [x] **`WorkoutRepository.kt`**, **`WorkoutSync.kt`**, **`WorkoutImport.kt`** — library + Nostr + merge
- [x] **`WorkoutRunEngine.kt`** — segment advance, circuit/superset round-robin
- [ ] `UnifiedRoutineModels.kt` — block types + fields (legacy; deprecate after Phase 3)
- [ ] `ProgramModels.kt` — slim `ProgramDayBlock` / `workoutRefs` migration
- [ ] Migration helper + tests (inline blocks → workout refs)
- [ ] Merge APIs: single workout library replacing unified + program inline blocks

**UI**

- [x] **`WorkoutLibraryScreen`**, **`WorkoutComposerScreen`**, **`WorkoutLiveRunScreen`** — Phase 2 MVP
- [x] **`WeightExerciseSetsCard.kt`** — ghost `targetReps`, hold countdown in live run
- [x] Web **`WorkoutsTab.tsx`**, **`WorkoutSegmentEditor.tsx`** — multi-modality builder
- [ ] New merged **`PlanBuilderScreen`** / Planner shell (Phase 3)
- [ ] Retire or shrink `ProgramsScreens.kt`, unify with `UnifiedRoutineScreen.kt` patterns
- [ ] `DashboardScreen.kt` — today card (Phase 4); single tile after nav merge
- [ ] `LaunchPadTileOrder.kt`, `ErvNavHost.kt` — Planner replaces two tiles (Phase 3)

**AI**

- [ ] Web/server AI package for provider client, queue, context builder, and draft validation
- [ ] Web companion settings — AI + Maple settings
- [ ] Web merged section: provider selector + generate/edit entry points
- [ ] Web workout import envelope + preview dialog
- [ ] Extend web context bundle for workouts

**Docs**

- [x] [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md) — authoring UX + **implementation status (§9.3, §14–§15)**
- [x] [START9_COMPANION_V1.md](START9_COMPANION_V1.md) — Phase 2 progress checklist
- [x] [workouts_import_schema.md](../import/workouts_import_schema.md) — status + checklist updated
- [ ] Update `programs_import_ai_guide.md` when plan day schema changes (Phase 3)

---

## 10. References

| Topic | Location |
|-------|----------|
| Program import schema | `docs/import/programs_import_ai_guide.md`, `ProgramImport.kt` |
| Agent interoperability | `docs/import/DATA_IMPORT_EXPORT.md` §5 |
| Program UI (legacy) | `app/.../ui/programs/ProgramsScreens.kt` |
| Unified UI (legacy) | `app/.../ui/unifiedroutines/UnifiedRoutineScreen.kt` |
| Context bundle for AI | `ImportExportCoordinator.buildProgramsReferenceBundleMarkdown()` |
| Workout & plan editor UX | [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md) |
| Maple Proxy on StartOS | [islandbitcoin/maple-proxy-startos](https://github.com/islandbitcoin/maple-proxy-startos) |
| Start9 plan editor (planned) | `erv-plan-editor-startos` — same JSON as Android composer |
| Maple Proxy upstream | [OpenSecretCloud/maple-proxy](https://github.com/OpenSecretCloud/maple-proxy) |

---

*Last updated: June 2026 — merge-first sequencing; Phase 2 Workout entity shipped in parallel; Phase 3 nav merge pending.*
