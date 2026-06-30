# ERV Start9 companion — v1 checklist

Track implementation of the relay-synced desktop companion. Goal: **create a weight routine on StartOS → publish → see it on Android**.

## Phase A — Scaffold (this PR)

- [x] `apps/web/server/` — Rust Axum backend (FiatLife pattern, `ERV_*` env vars)
- [x] `apps/web/web/` — React + Vite + Tailwind SPA
- [x] ERV sun theme (light palette from `ui/theme/Color.kt`)
- [x] Auth: nsec seal/unlock, session cookie, relay URL in `state.json`
- [x] NIP-44 encrypt-to-self + kind **30078** publish/fetch
- [x] Background publish **outbox** with retry
- [x] `erv_tags.rs` — filter/publish allowlist for `erv/*` d-tags
- [x] v1 UI: **Weight routines** list + create form
- [x] **Catalog editor** — browse/edit `erv/catalog/*` by category (weight muscle group, stretch category, cardio section)
- [x] Publish `erv/weight/routines` with `lastModifiedEpochSeconds`
- [x] `packages/start9/` — StartOS manifest + Makefile (from FiatLife)
- [x] This checklist doc

## Phase B — Verify on device

See also: [START9_SCAFFOLD_AUDIT.md](START9_SCAFFOLD_AUDIT.md) (FiatLife scaffold issues + Cursor freeze causes).

- [x] Host package build: `./packages/start9/build.sh` → `packages/start9/erv-web_x86_64.s9pk` (~32 MB)
- [ ] Sideload or `make install` on StartOS server
- [ ] Local dev: setup → create routine → `erv/weight/routines` on relay
- [ ] Android: open app → relay sync → routine appears under Weight Training → Routines
- [ ] StartOS: same flow on LAN relay (Haven recommended; external `wss://` relay also supported)

### Install on StartOS

**Option A — Sideload (UI):** Open StartOS → **Sideload** → upload:

`packages/start9/erv-web_x86_64.s9pk`

Use `_aarch64` build (`make arm-import`) on Raspberry Pi / ARM hardware.

**Option B — CLI (LAN):** Create `~/.startos/config.yaml`:

```yaml
host: http://YOUR-START9-HOST.local
```

Then from `packages/start9/`:

```bash
make install
```

**After install:** ERV service → open **Web UI** → Setup (nsec + relay) → Routines / Catalog tabs.

## Phase C — Next features (not v1)

Phasing detail: [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md) §14.

### Phase 1 — Silo routines (shipped)

- [x] Stretch routines (`erv/stretching/routines`) — web editor with library sidebar; edit/delete; drag-reorder
- [x] Cardio routines (`erv/cardio/routines`) — web editor; multi-leg `steps[]`; edit/delete
- [x] Weight routines (`erv/weight/routines`) — web editor; edit/delete; drag-reorder exercises
- [x] Built-in catalogs on relay (`erv/catalog/*`) + catalog editor UI (`/app/catalog`)
- [x] Shared searchable library sidebar for routine builders

Silo routines are **library ingredients** for the workout composer — not the scheduling layer. Do not add date assignment to routines.

### Phase 2 — Workout composer (`erv/workouts/library`) — **~90% complete**

**Goal:** Canonical session storyboard + Nostr library sync. See [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md) §14.1 for sub-phases B1–B7.

#### B1 — Schema

- [x] `Workout`, `WorkoutSegment`, polymorphic `WorkoutItem` (Android + web)
- [x] Segment kinds: `straight_sets`, `superset`, `circuit`, `composite`, `cardio`, `mobility` (wired in UI)
- [x] Item types: `weight`, `cardio`, `mobility`, `note`, `rest`
- [x] `SegmentRestPolicy`, `rounds`, `restAfterSeconds` on segments
- [x] Nostr d-tag **`erv/workouts/library`** (publish/fetch; server allowlist)
- [x] Import envelope v1 (`WorkoutImport.kt` + merge-by-id; unit tests)
- [ ] Item types: `heat_cold`, `light` — **out of scope:** post-workout only (Hot+Cold / Light Therapy tiles); not part of session storyboard
- [ ] Segment kinds in live run: `interval`, `recovery`, `freestyle`, `emom`
- [ ] Full prescription model (per-set rows, nested cardio legs, alternatives)

#### B2 — Android composer + live run

- [x] **Workout library** screen — list, create, delete, sync
- [x] **Workout composer** — straight sets, circuit, superset; exercise picker; note/rest items
- [x] **Live run** — segment auto-advance, rest countdown, weight → live set logging
- [x] **Run engine** — round-robin for circuit/superset; sequential items for straight/composite
- [x] Training category entry (Workouts tile under Training)
- [x] Composer: add cardio / mobility / flow segments on device
- [x] Live run: launch Cardio timer + stretch player for cardio/mobility steps
- [x] JSON file import UI (Settings → Data Interchange → Workouts)
- [x] Segment collapse, drag-reorder segments, duplicate workout

#### B3 — Cardio + HR

- [x] Steady cardio item (`targetMinutes`, `activity`) — web builder + schema
- [x] Optional `hrTargetBpm` field — web builder + schema
- [x] HR range (`hrTargetMinBpm` / `Max`), zone labels, log fields — web + Android schema
- [x] Flat sprint intervals + nested multi-round HIIT (`interval` segment)
- [x] Reference saved cardio routine (`cardioRoutineId`) — web + Android composer + live launch

#### B4 — Superset + time (lifting)

- [x] Superset + circuit `restPolicy` (between items / after round)
- [x] **`targetReps`** on weight prescription — ghost rep in live UI *(added during implementation; see spec §15)*
- [x] **`durationSeconds`** hold target — timed countdown in live UI (30s default) *(added during implementation)*
- [x] Rep range + RIR fields in composer (web + Android)
- [x] `max_reps` mode, per-side reps, per-set prescription editor
- [x] Exercise alternatives (`alternativeExerciseIds`) — composer + live-run picker
- [ ] Time-based mode tab in composer

#### B5 — Mobility + recovery

- [x] Mobility items (`catalogId`, hold seconds, per-side holds) — web builder
- [x] Composite **Flow block** mixing weight + cardio + mobility + notes — web
- [x] Stretch player integration in live run
- [—] `heat_cold` + `light` in workout composer — **won't do:** recovery is separate from session time; user opens Hot+Cold / Light Therapy after workout

#### B7 — Web workout builder

- [x] **Workout builder** tab — default route `/app/workouts`, first item in top nav *(product decision during implementation)*
- [x] Publish **`erv/workouts/library`** to relay
- [x] Multi-modality segments: Flow block, Cardio block, Mobility block, Straight sets / Circuit / Superset
- [x] Library sidebar adapts by segment type; **ADD** affordance on catalog rows
- [x] Segment/item drag-reorder; per-type item editors
- [x] **Builder-only web** — no “Start live workout” on StartOS *(added during implementation)*
- [x] Focus UX: grey catalog until segment selected; pulse on segment-type buttons + active segment *(added during implementation)*
- [x] Segment template library (+ Warm-up, Zone 2, HIIT, Superset, etc.)
- [ ] Editable targets during live session on web (N/A — web does not run sessions)

#### Phase 2 acceptance test

- [x] Build hybrid workout on web (lift + flow + cardio + mobility)
- [x] Publish → Android relay sync → appears in workout library
- [x] Run with segment auto-advance — weight + cardio timer + stretch player (recovery silos excluded by design)

**Not in Phase 2:** weekly planner, Programs tile merge, dashboard today card.

#### Implementation additions (not in original Phase 2 checklist)

These shipped while building the composer and are documented in [WORKOUT_PLAN_EDITOR_SPEC.md](WORKOUT_PLAN_EDITOR_SPEC.md) §15:

| Addition | Where |
|----------|--------|
| `targetReps` ghost in live weight UI | Android `WeightExerciseSetsCard.kt` |
| Timed hold countdown from prescription | Android live run |
| Web builder as **default landing** + first nav tab | `App.tsx`, `AppShell.tsx` |
| Builder-only web (no session start) | `WorkoutsTab.tsx` |
| Pulse / greyed catalog focus UX | `index.css`, `WorkoutsTab.tsx`, `LibrarySidebar.tsx` |
| Dedicated segment kinds `cardio` / `mobility` (not only composite) | Web + Android models |
| Android **Training → Workouts** hub (parallel to legacy Unified Workouts) | `ErvNavHost.kt` |
| Live run silo handoff (cardio timer, stretch player) | `WorkoutLaunchResolvers.kt`, `WorkoutLiveRunScreen.kt` |
| Interval / HIIT prescription | Web + Android models, launch resolvers |
| Reps vs time logging style in builder | `WeightDefaultCatalog.kt`, `exerciseLogging.ts` |
| Segment template library (web) | `segmentTemplates.ts`, `WorkoutsTab.tsx` |
| HR range + log fields + `cardioRoutineId` | Web + Android models, editors |
| JSON workout import UI | Settings → Workouts, `commitWorkoutImport` |
| Android multi-modality composer | `WorkoutComposerScreen.kt`, `WorkoutComposerEditors.kt` |

### Phase 3 — Weekly planner (`erv/programs/master`)

- [x] MVP week planner: assign saved workouts to weekdays on web and publish `workout` blocks
- [x] Android: display synced saved-workout blocks in Programs/Launch Pad and launch the live workout runner
- [ ] Week grid: drag exercises, routines, templates, and saved workouts onto days
- [ ] Android: merge Programs + Unified Workouts → **Planner** tile
- [ ] Plan strategy, habits, rest notes (3b)

### Phase 4 — Dashboard + AI

- [ ] Dashboard: surface planned workout for today (read-only)
- [ ] Web-only AI plan/workout generation (Maple / optional)

### Pre-AI athlete context (web-first)

Full spec: [ATHLETE_CONTEXT_WEB_PREP.md](ATHLETE_CONTEXT_WEB_PREP.md). Web = planning desk and AI generation; Android = live logging and synced workout execution.

#### W1 — Training profile (`erv/training-profile`)

- [x] JSON schema (Android + web)
- [x] Nostr sync + server publish allowlist
- [x] Web **Profile** tab (edit + publish)
- [x] Android read-only Settings summary
- [ ] Include profile in reference export bundle

#### W2 — History on web

- [x] Fetch weight/cardio day logs from relay
- [x] **Progress** tab — session timeline + per-exercise history
- [x] Basic volume/frequency charts

#### W3 — Training snapshot

- [x] Compute baseline from logs (working weights, muscle recency, cardio load)
- [x] **Progress** tab — “Your training baseline” panel + staleness hint
- [ ] Optional relay publish `erv/training-snapshot` (deferred)

#### W4–W6

- [x] Prescription polish (equipment filter, load suggestions) — W4
- [ ] Planner (Phase 3) — W5
- [x] “Copy training context” export (AI dry run) — W6

### Other

- [ ] Read-only analytics (day logs, route images via Blossom) — superseded by W2/W3 above

## Test plan (routine publish)

1. Install **Haven** on StartOS (recommended for local relay sync and future Blossom media backup), or use an external `wss://` relay.
2. Open ERV Start9 web UI → Setup with **same nsec** as Android.
3. Create routine "Test Push" with Bench Press + OHP.
4. Confirm success toast shows event id; outbox pending clears.
5. On phone: Settings → ensure same data relay → pull/sync.
6. Weight Training → Routines tab → **Test Push** appears.

## Architecture

```
Android (ERV)  ←—— kind 30078 / NIP-44 ——→  Nostr relay  ←——→  StartOS erv-web
     execute workouts                         encrypted JSON
     BLE, GPS, timers                         erv/weight/routines
                                              erv/stretching/routines
                                              erv/cardio/routines
                                              erv/catalog/*
                                              erv/workouts/library   ← Phase 2
```

## Key files

| Area | Path |
|------|------|
| Server routes | `apps/web/server/src/routes.rs` |
| Nostr crypto | `apps/web/server/src/nostr_support.rs` |
| d-tag policy | `apps/web/server/src/erv_tags.rs` |
| Silo routine UI | `apps/web/web/src/routes/RoutinesTab.tsx` |
| **Workout builder UI** | `apps/web/web/src/routes/WorkoutsTab.tsx` |
| **Workout segment editor** | `apps/web/web/src/components/WorkoutSegmentEditor.tsx` |
| **Workout JSON (web)** | `apps/web/web/src/lib/workoutTraining.ts` |
| Weight JSON contract | `apps/web/web/src/lib/weightTraining.ts` |
| Android silo merge | `app/.../LibraryStateMerge` |
| **Android workout models** | `app/.../workouts/WorkoutModels.kt` |
| **Android workout sync** | `app/.../workouts/WorkoutSync.kt` |
| **Android workout UI** | `app/.../ui/workouts/` (`WorkoutLibraryScreen`, `WorkoutComposerScreen`, `WorkoutLiveRunScreen`) |

---

*Last updated: June 2026 — Phase 2 workout composer in progress; see WORKOUT_PLAN_EDITOR_SPEC.md §9.3, §14–§15.*
