# Workout & plan editor — product and UX specification

This document defines the **authoring experience** for ERV training content: a rich **workout composer** (single session with prescriptions) and a **weekly planner** (scheduling workouts across days). It extends [PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) and is designed so the **same JSON model and editing semantics** work on **Android** and the **Start9 companion** (`apps/web/`).

**Scope:** product and UX — not implementation detail at the code level.

**Related companion checklist:** [START9_COMPANION_V1.md](START9_COMPANION_V1.md) (web silo routine editors shipped; workout composer and planner are later phases).

---

## 1. Vision

Most fitness apps split “programming” (spreadsheet hell) from “doing” (timer + log). ERV should feel like **directing a session**: you storyboard what will happen, then the phone becomes a **teleprompter** during the live workout — pre-filled targets, rest cues, interval timers, and easy “as planned / adjust” logging.

**Design north stars:**

| Principle | Meaning |
|-----------|---------|
| **Storyboard, not spreadsheet** | Vertical flow of cards and segments; drag to reorder; glanceable structure. |
| **Prescribe once, execute many times** | Targets live in the workout template; each live session logs **actuals** against them. |
| **Phone edits, desktop composes** | Quick tweaks on device; deep authoring on Start9 with keyboard, wide week view, and AI side-by-side. |
| **Same language everywhere** | One canonical `Workout` + `Plan` JSON; Android, Start9, Maple AI, and import/export all speak it. |
| **Week-first planning** | The primary mental model is “what am I doing this week?” — drag building blocks onto days; each day resolves to scheduled work. |
| **One builder, every silo** | Weight, cardio, stretch, sauna/cold, and light therapy share one composer — the merged Programs + Unified Workouts surface. |

### 1.1 Product shift (today → target)

| Today | Target (after planner ships) |
|-------|------------------------------|
| **Programs** and **Unified Workouts** are separate Launch Pad tiles | One **Planner** tile replaces both |
| Silo **routines** (weight / stretch / cardio) are ordered lists without dates | Routines remain **library ingredients**; scheduling lives on the plan |
| Users build exercise **blocks** but nothing is assigned to a calendar day | **Week view** shows all planned workouts; empty days are explicit |
| Dashboard has no “today’s plan” surfacing | **Future:** dashboard shows today’s planned workout when the active plan has one (see §5.4) |

**Important:** Do **not** add date assignment to silo routines as an interim step. That would be throwaway UX and data. Silo routine editors (web + Android) stay valid as **sources** until the workout composer and planner ship.

---

## 2. Where this sits in the product

Replace separate **Programs** and **Unified Workouts** Launch Pad entries with one tile: **Planner** (name TBD — “Training plan”, “Planner”, etc.).

```
Planner (merged Launch Pad tile — Android + Start9 companion)
├── Week           → **primary view**: Mon–Sun; drag-and-drop planned workouts per day
├── Library        → saved workouts, silo routines, catalogs, segment templates
├── Composer       → session storyboard (when editing a workout in depth)
└── History        → completed sessions vs prescription (optional v2)

Dashboard (separate surface, future)
└── Today card     → read-only: planned workout for calendar today (§5.4)
```

- **Week view** — assign and compose work per day; the main way users see “all workouts for this week.”
- **Workout Composer** — build or refine one session: segments, exercises, sets, intervals, cardio legs, stretch, sauna, etc.
- **Library** — palette for drag sources (exercises, routines, circuits, HIIT blocks, saved workouts).
- **Live run** — consumes the workout prescription; never re-author structure mid-session unless user explicitly edits.

### 2.1 Week-first UX, workout-backed storage

Users should be able to build weekly training by **dragging and dropping** onto days:

- Individual **exercises**
- Silo **routines** (weight / stretch / cardio)
- **Segment templates** (circuit shell, HIIT interval block, superset, etc.)
- Complete **saved workouts**

**UX model:** week-first (drop directly onto Tuesday).  
**Storage model:** workout-backed (every populated day resolves to a `Workout` entity).

| User action | What happens under the hood |
|-------------|----------------------------|
| Drop a **saved workout** onto a day | `PlanDay.workoutRefs[]` gains `{ workoutId }` — no duplication |
| Drop an **exercise**, **routine**, or **segment template** onto a day | Create or **append** to that day’s workout: materialize `Workout.segments[]` / `items[]` from the drop payload |
| Edit prescription detail | Open **Composer** for that day’s workout id (✎ or double-click) |
| Drop **habit** / mark **rest** | Plan-only on `PlanDay` — never inside workout segments |

This preserves the merge rule from [PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md): plans do not re-specify modalities as inline legacy program blocks; they **schedule workout ids**. The week canvas is a composer + calendar in one; drops that are not pure refs still **persist as workouts** in `erv/workouts/library`.

### 2.2 Library palette (drag sources)

Phase 2 should define each row as a valid **drop payload** (even if drops only work inside the composer until the week UI ships in Phase 3).

| Drag source | Becomes in the day’s workout |
|-------------|------------------------------|
| Weight exercise | `weight` item (new or existing `straight_sets` / circuit segment) |
| Weight routine | Ordered `weight` items expanded from `erv/weight/routines` |
| Stretch routine | `mobility` segment from `erv/stretching/routines` |
| Cardio routine | `cardio` or `interval` segment from `erv/cardio/routines` (incl. multi-leg `steps[]`) |
| Circuit template | Empty `circuit` segment scaffold; user fills exercises |
| HIIT / interval template | `interval` segment scaffold (work/rest, nested legs) |
| Saved workout | `workoutRef` only |

Silo routine editors on web ([START9_COMPANION_V1.md](START9_COMPANION_V1.md) Phase 1) remain useful as **reusable lists** referenced by the composer and planner — not as the scheduling layer.

---

## 3. Data model extension (prescription layer)

Today `WeightRoutine` is only an ordered list of exercise ids; program import **explicitly rejects** per-set fields. The editor requires a first-class **prescription** that is separate from **logged sets** (`WeightWorkoutEntry` / `WeightSet`).

### 3.1 Core entities

```
Workout
  id, name, notes, tags, estimatedDurationMinutes?, sourceLabel?
  segments[]          ← ordered top-level flow (replaces flat "blocks only")

WorkoutSegment
  id, kind              ← see §3.2
  title?, notes?
  items[]               ← exercises, cardio legs, etc. within segment
  restAfterSeconds?     ← transition rest before next segment

WorkoutItem (polymorphic by kind)
  weight          → exerciseId from weight library (built-in + custom)
  cardio          → CardioBuiltinActivity or saved cardio routine
  mobility        → stretch catalog id or saved stretch routine
  heat_cold       → SAUNA | COLD_PLUNGE + targetMinutes
  light           → LightDevice and/or LightRoutine + targetMinutes
  rest            → duration, label
  note            → coach cue text
```

**All modalities use existing ERV libraries** — the composer is the unified face of Weight Training, Cardio, Stretching, Hot+Cold, and Light Therapy catalogs. See [workouts_import_schema.md §1.2](../import/workouts_import_schema.md).

**Segments** are the UX and data unit for grouping (warm-up, main lift, accessory circuit, finisher). They map cleanly to unified **blocks** for execution: one segment often becomes one runnable block, or splits by modality at launch time.

### 3.2 Segment kinds

| Kind | User-facing label | Purpose |
|------|-------------------|---------|
| `straight_sets` | Straight sets | Classic lift list — default |
| `superset` | Superset | 2–4 exercises alternated; [SegmentRestPolicy](§3.3.1) |
| `circuit` | Circuit | Round-robin; `rounds` on segment |
| `composite` | Mixed block | Warm-up: cardio + mobility + bodyweight in one named segment |
| `emom` | EMOM | Every minute on the minute — time box + reps target |
| `interval` | Intervals | Work/rest rounds (cardio HIIT or weighted carry timers) |
| `cardio` | Cardio | Steady or HR-target session (Zone 2 walk, easy bike) |
| `mobility` | Mobility / flex | Stretch holds, per-side duration, flex day |
| `recovery` | Recovery | Sauna, cold plunge, red/NIR light — launches existing Hot+Cold or Light Therapy run flow |
| `freestyle` | Open block | User picks modality at launch (`FLEX`) |

### 3.3 Weight exercise prescription

Each `WEIGHT_EXERCISE` item carries a **prescription** — targets for the *next* live session, not historical log data.

```
ExercisePrescription
  mode: STRAIGHT | INTERVAL | AMRAP | TIME_BASED | MAX_REPS

  // STRAIGHT (default)
  sets[]: PrescriptionSet
  warmupSets[]?          ← optional, visually lighter in UI
  restBetweenSetsSeconds?
  restAfterExerciseSeconds?
  repRangeMin?, repRangeMax?     ← e.g. 8–12 when not fixed per set
  targetRir?                     ← reps in reserve (e.g. 2 for "strict 2 RIR")
  intensityLabel?                ← "strict 2 RIR", "heavy", display-only cue

  // INTERVAL (maps to existing HIIT timer)
  intervals: Int
  workSeconds: Int
  restSeconds: Int
  targetWeightKg?, targetRpe?

  // TIME_BASED (v1 — required for planks, carries, neck protocols)
  durationSeconds: Int
  durationPerSideSeconds?        ← e.g. couch stretch 2 min/side
  side: BOTH | LEFT | RIGHT | ALTERNATING

  // MAX_REPS
  setCount: Int                   ← e.g. 3 sets to failure / max reps

  // Alternatives (pick one at launch or when authoring)
  alternativeExerciseIds[]?      ← "cable face pulls OR band pull-aparts"

PrescriptionSet
  reps: Int?             ← null with repRangeMin/Max = range target
  repsPerSide: Int?      ← "12 reps/side", "10/leg"
  weightKg: Double?      ← null = bodyweight or unset
  rpe: Double?
  rir: Int?              ← per-set RIR if different from exercise default
  isWarmup: Boolean
  isDropSet: Boolean     ← chains to next set visually
  notes: String?         ← "regressed", "slantboard", "figure-8 CCW×5 CW×5"
```

**Progression (optional on exercise):**

```
ProgressionRule
  type: LINEAR | DOUBLE_PROGRESSION | PERCENT_OF_E1RM | MANUAL_ONLY
  incrementKg?, repRangeMin?, repRangeMax?, deloadEveryWeeks?
```

Live workout **seeds** the set editor from `PrescriptionSet` rows; logged `WeightSet` values diverge without mutating the template unless user taps **“Update template from session”** (existing “update routine” pattern, extended).

### 3.3.1 Superset and circuit rest model

Supersets and circuits need **two rest knobs** (your Thursday bench + ring rows example):

```
SegmentRestPolicy
  restBetweenItemsSeconds?    ← 60s between bench and rows within a round
  restAfterRoundSeconds?      ← 60s after completing the pair, before next set
  rounds: Int                 ← 3 for "3 sets" on a superset
```

During live run: complete item A → rest between → item B → rest after round → repeat until rounds exhausted.

### 3.4 Cardio / mobility / recovery prescriptions

Reuse existing cardio models where possible; extend for **HR-guided** and **nested intervals** (your 4×4 and sprint days).

```
CardioPrescription
  activity: CardioBuiltinActivity | routineId
  mode: STEADY | INTERVAL_TEMPLATE | SPRINT_INTERVALS

  // STEADY (Wed walk, Mon warm-up/cool-down bike)
  targetMinutes: Int
  targetDistanceMeters?
  equipmentNotes?              ← "weighted vest"
  logFields[]: INCLINE | SPEED | DISTANCE | NOTES   ← "record incline and speed"

  // HR guidance (requires BLE HR during run — cue only if disconnected)
  hrTargetBpm: Int?            ← single target (warm-up 115)
  hrTargetMinBpm?, hrTargetMaxBpm?   ← range (Zone 2: 110–130)
  hrZoneLabel: String?         ← "Zone 5", "Zone 2" (display + zone math from user settings)

  // INTERVAL_TEMPLATE — Nordic 4×4 style (Mon Zone 5 block)
  outerRounds: Int              ← 3 rounds
  legs[]: CardioIntervalLeg
    workSeconds, restSeconds, label?, hrTargetBpm?

  // SPRINT_INTERVALS — Fri 10×1:1
  rounds: Int
  workSeconds, restSeconds
  activity per leg
```

**Mobility / stretch:**

```
MobilityPrescription
  items[]: StretchItem
    catalogId | customName
    holdSeconds                 ← 60s default in your Wed flex block
    holdSecondsPerSide?         ← couch stretch 120s/side
    side: BOTH | LEFT | RIGHT | ALTERNATING
  rounds: Int?                  ← usually 1 for flexibility day
```

**Composite segments** — mix any item types in one named block (cardio warm-up + leg swings + note; sauna then cold plunge).

**Heat/cold/light:** see [workouts_import_schema.md §7.6–7.7](../import/workouts_import_schema.md) — `heatCold.mode`, `targetMinutes`; `lightRoutineId` / `lightDeviceId` from the user’s Light Therapy library. Run time uses **existing** Hot+Cold and Light Therapy screens.

### 3.5 Plan level (scheduling + plan-only meta)

```
Plan (FitnessProgram)
  strategy, activeProgramId, templates…   ← unchanged responsibility
  weeklySchedule[]: PlanDay

PlanDay
  dayOfWeek (1–7)
  workoutRefs[]: { workoutId, optionalLabel, optionalTimeOfDay? }
  habits[], restNotes[], customNotes[]     ← plan-only; not workout segments
```

- A day may reference **multiple workouts** (e.g. AM lift + PM walk).
- The plan editor **never** stores exercise-level prescription inline on the day — only **workout ids** plus plan-only rows (habits, rest, notes).
- **Week-first drops** that add exercises or segment templates **create or update** the referenced workout in the library, then set `workoutRefs` on that day (see §2.1).
- **Deprecated:** legacy `ProgramDayBlock` inline kinds (`weight`, `cardio`, `stretch_routine`, `unified_routine`, …). Migrate on read to generated workouts + `workout` refs ([PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) §4).

### 3.6 Versioning and sync

- Bump `workoutSchemaVersion` / `ervWorkoutImportVersion` when prescription fields change.
- Workouts sync as part of unified workout library (DataStore + future Nostr d-tag e.g. `erv/workouts/library`).
- Plans continue on `erv/programs/master` (or equivalent).
- **Start9 and phone** both read/write the same envelope; conflict rule: **newer `lastModifiedEpochSeconds` wins** per entity id (same as today’s replaceable events).

---

## 4. Workout Composer — mobile UX (Android)

Full-screen route: **no dialogs for structure**. One continuous editor with a sticky header and bottom action bar.

### 4.1 Layout metaphor: “Session storyboard”

```
┌─────────────────────────────────────┐
│  ←  Push Day A          ⋮  AI  ▶ Run │  sticky header
├─────────────────────────────────────┤
│  ○ Warm-up          ≈ 8 min         │  segment chip (collapsed)
│  ┌───────────────────────────────┐  │
│  │ ≡  Bench press                │  │  drag handle
│  │    3 × 8 @ 80 kg  ·  90s rest │  │  one-line summary
│  │    [ tap to expand sets ]     │  │
│  └───────────────────────────────┘  │
│  ┌─ Superset ───────────────────┐  │  visual bracket
│  │ ≡ Row  ·  3×10               │  │
│  │ ≡ Face pull · 3×15           │  │
│  └──────────────────────────────┘  │
│  + Add exercise   + Add segment     │
└─────────────────────────────────────┘
│  Duplicate · Preview · Save         │  bottom bar
└─────────────────────────────────────┘
```

**Interactions:**

| Gesture | Action |
|---------|--------|
| Long-press drag handle | Reorder exercises within segment or move across segments |
| Swipe exercise card left | Remove (with undo snackbar) |
| Swipe right | Duplicate exercise |
| Tap card | Expand inline set editor (no new screen) |
| Pinch segment header | Collapse/expand whole segment |
| Double-tap segment title | Rename segment |

### 4.2 Inline set editor (expanded card)

When expanded, the card becomes a **mini spreadsheet tuned for thumbs**:

```
Bench press                    [History ↗]
Warm-up  ●○○   45×8  45×5  60×3        ← toggle warm-up row style
Working  ●●●   80×8  80×8  80×8  [+ set]
Rest     90s between sets  ·  2m after exercise
         [ Linear +2.5 kg/week ]

        [ Sets ] [ Intervals ] [ Notes ]   ← mode tabs if hiitCapable
```

- **Steppers** for reps (+/−); **wheel or numpad** for weight in user’s load unit.
- **RPE** optional column — hidden until enabled in settings.
- **Copy from last session** chip: pulls last logged sets for this exercise (not template).
- **Copy from prescription** is the default when starting live workout.

**Interval tab** (for HIIT-capable exercises): visual **work | rest** bar editor with round count; mirrors live `WeightHiitIntervalPlan`.

### 4.3 Adding content — fast paths

**Floating “+” menu:**

1. **Exercise** — searchable **weight** library (built-in + custom; muscle/equipment filters).
2. **Cardio** — built-in activities + saved **cardio routines**.
3. **Stretch** — catalog poses + saved **stretch routines**.
4. **Sauna / cold** — `SAUNA` or `COLD_PLUNGE` with target duration.
5. **Light therapy** — pick **device** and/or saved **light routine** from Light Therapy library.
6. **Segment template** — “Warm-up”, “Superset”, “Circuit (3 rounds)”, “Contrast heat/cold”, “Red light finisher”.
7. **Note / rest** — coach cue or timed rest item.
8. **From past workout** — duplicate completed session as template.

Unified **search** (optional v1.1): one field queries weight names, cardio labels, and stretch names; recovery types appear as fixed tiles.

**Smart paste:** paste text like `Bench 3x8 @185, Squat 5x5 @225` → parser proposes structured cards (AI assist optional).

### 4.4 Superset & circuit UX

- User selects 2+ exercises → **“Group as superset”** — UI draws a shared border and one rest-between-rounds control.
- **Circuit:** “3 rounds” stepper at segment level; exercises show round checkmarks during live run.
- During live workout, superset **cycles** exercises before advancing rest (existing live flow extended).

### 4.5 Preview & run

- **Preview** mode: read-only storyboard with estimated duration (sum segment heuristics).
- **Run** launches unified live session with prescription injected; header shows workout name and segment progress (“Segment 2 of 4 · Superset”).

### 4.6 Empty states & templates

Starter cards when library is empty:

- “Full body · 45 min”
- “Push · dumbbells only”
- “Intervals + core”

Each opens composer pre-filled with segment scaffolding, not a blank screen.

---

## 5. Weekly planner — UX

Optimized for **week-centric authoring**: see the whole week, drag building blocks onto days, tap to run or refine. This is the **primary** Planner surface once shipped — not a secondary “assign only” screen.

### 5.1 Week grid + library sidebar

```
┌ Library ─────────────┐  ┌── Week ──────────────────────────────────────┐
│ 🔍 Search            │  │  M      T      W      T      F      S      S  │
│ Exercises            │  │ ┌────┐ ┌────┐  ·   ┌────┐  ·    ·     ·   │
│ Routines             │  │ │Push│ │Zone│      │Flex│                  │
│ Templates ▾          │  │ │ A  │ │ 2  │      │    │                  │
│  · Circuit           │  │ └────┘ └────┘      └────┘                  │
│  · HIIT intervals    │  │  drag from library → drop on day column      │
│ Saved workouts       │  └────────────────────────────────────────────┘
└──────────────────────┘
```

**Interactions:**

| Gesture | Action |
|---------|--------|
| Drag exercise / routine / template onto day | Materialize or append to that day’s `Workout` (§2.1) |
| Drag saved workout onto day | Set `workoutRef` only |
| Tap day column | Expand day sheet: workouts, habits, rest notes |
| Tap ✎ on workout chip | Open **Workout Composer** for that `workoutId` |
| Drag workout chip between days | Move assignment (same `workoutId` or copy — user choice) |
| Long-press day | Quick actions: rest day, add habit checklist |

- **Strategy** lives in plan header: Manual / Repeat / Rotation / Challenge — existing engine, cleaner shell.
- **Multi-week view:** pinch to zoom out to 4-week grid (v1.1); v1 is single-week strip with swipe between weeks.

### 5.2 Day sheet (expanded)

```
┌─────────────────────────────────────┐
│  Wednesday                          │
│  ┌ Push Day A ─────────────── ✎ ▶ ┐ │  ✎ → Composer  ·  ▶ → Live run
│  └─────────────────────────────────┘ │
│  + Add another workout              │
│  ☐ Read 10 pages  ☐ 1 gal water     │  habit checklist (plan-only)
│  Rest / notes…                      │
└─────────────────────────────────────┘
```

- **Edit plan only** never shows set rows — prescription editing stays in Composer.
- Starting **Run** from the day sheet uses the same unified live session pipeline as today’s unified workouts.

### 5.3 Mobile vs Start9 layout

| Mobile (Android) | Start9 / desktop companion |
|------------------|----------------------------|
| Week strip; tap day for sheet | **Full 7-column grid** + library sidebar |
| Composer full-screen route | Split pane: week grid + composer detail |
| Drag-reorder within session | Same + multi-select bulk edit (§6.3) |

The Start9 companion should implement the **week grid + sidebar** pattern when Phase 3 ships; Phase 2 web work focuses on the **composer** and library payloads only ([START9_COMPANION_V1.md](START9_COMPANION_V1.md)).

### 5.4 Dashboard — today’s planned workout (future)

**Not part of Phase 2 or Phase 3 MVP.** When the planner is live:

- **Dashboard** (or home) shows a read-only card when the **active plan** has a workout scheduled for **today’s calendar date** (respecting strategy: repeat / rotation / challenge).
- Copy example: “Today: Push Day A” with **Start** CTA → unified live run.
- Completion: plan day workout block marked done when a finished `UnifiedWorkoutSession` exists for that workout on that day ([PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) §2).

Requires reworking `programBlocksForDate`, dashboard deep links, and `LaunchPadTileOrder` to resolve **workout refs** from the merged plan — ship after Planner week view is stable.

---

## 6. Start9 companion package (`erv-plan-editor-startos`)

A **StartOS service** (same packaging model as [maple-proxy-startos](https://github.com/islandbitcoin/maple-proxy-startos)): static web UI + optional small API container, no ERV data stored on server beyond what the user opens.

### 6.1 Role in the ecosystem

| Surface | Best for |
|---------|----------|
| **Android** | Execute workouts, quick edits, BLE/GPS, offline |
| **Start9 editor** | Multi-week planning, bulk exercise entry, keyboard workflows, AI on large screen next to editor |

Philosophy matches [COMPANION_WEB_COMMUNITY_PITFALLS_BENEFITS.md](COMPANION_WEB_COMMUNITY_PITFALLS_BENEFITS.md): **compose on server/LAN, execute on device** — but scoped to **personal** use first (no social graph required for v1).

### 6.2 Package architecture

```
┌──────────────── Start9 ────────────────┐
│  erv-plan-editor (nginx or similar)    │
│    /           → SPA Plan + Workout UI │
│    /api/...    → optional local helpers│
│  maple-proxy   → AI (optional sibling) │
└──────────────────────────────────────┘
          ▲ WireGuard / LAN
          │
    ┌─────┴─────┐
    │ ERV Android│
    └───────────┘
```

**v1 transport (no custom backend required):**

1. **File exchange** — Android exports workout/plan JSON bundle; user opens in Start9 editor via upload; saves; imports back (same as today’s import/export, formalized).
2. **LAN REST (v1.1)** — optional thin sync API on Start9: `PUT /workouts/{id}`, `GET /library` with shared token; Android settings “Plan editor URL”. Still local-first; relay optional.

**v2:** Nostr-signed sync of workout library when user is signed in — same encrypted shapes as phone.

### 6.3 Desktop UI differences

Same storyboard model, **layout adapted for pointer + keyboard**:

| Mobile | Start9 / desktop |
|--------|------------------|
| Vertical storyboard | **Split pane:** segment list left, detail right |
| Thumb set steppers | **Editable table** for sets (Tab between cells, Enter to add row) |
| Week strip | **Full 7-column grid** with drag-drop from library palette onto days (§2.2) |
| AI sheet | **Side panel:** prompt + streaming JSON diff preview |

**Killer desktop features:**

- **Multi-select** exercises → bulk edit rest time or +1 rep across selection.
- **Diff view** when importing AI draft vs current library.
- **Print / PDF** week view for gym bag (offline, no account).

### 6.4 Shared frontend (optional engineering win)

One **web component library** (e.g. Svelte/React) bundled in Start9; Android WebView **not** required for v1 — native Compose implements parallel UX from the **same JSON schema docs**. Long term, shared WASM validation library for prescription parsing.

---

## 7. AI in the editor

AI is a **mode of the composer**, not a separate app area ([PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) §6).

| Entry | Behavior |
|-------|----------|
| **Generate workout** | Prompt → Maple/local → draft segments + prescriptions → storyboard preview |
| **Generate plan** | Prompt → week grid populated with workout refs (creating workouts as needed) |
| **Edit selection** | “Add finisher”, “Swap bench for dumbbell press” → patch selected segment |
| **Progression suggest** | Uses recent logs from context builder → proposes `ProgressionRule` |

**Critical:** AI must emit **full prescription objects**, not just exercise ids. Update import guides accordingly when schema ships.

On Start9, AI runs against sibling **Maple Proxy**; editor shows streaming output and validates before merge.

---

## 8. Live workout integration

Prescription → execution handoff:

1. User taps **Run** on workout W.
2. App builds `WeightWorkoutDraft` / cardio timer state from each `PrescriptionSet` / interval spec.
3. UI labels sets **“Target”** vs logged **“Actual”** (subtle; avoid guilt UX).
4. On finish, summary offers:
   - **Save log only**
   - **Update prescription from today** (per exercise or whole workout)
5. Unified multi-modality sessions walk **segments in order**, launching silo UIs with prescription context preserved in back stack.

**Rest timers:** auto-start `restBetweenSetsSeconds` from prescription unless user disables in settings.

---

## 9. Non-goals and deferred scope

### 9.1 Non-goals (v1 planner)

- Social sharing / public program marketplace (see PROTOCOL_GRAPH — later).
- Automatic periodization across mesocycles without user confirm.
- Video exercise demos in editor (link out only).
- Replacing silo-specific screens (weight/cardio) for **execution** — editor composes; silos still run timers and sensors.
- Excel-compatible export (CSV of sets is optional v1.1).

### 9.2 Explicitly deferred (later phases)

| Feature | Phase | Notes |
|---------|-------|-------|
| Date assignment on silo routines | **Never** | Use planner + workouts instead |
| Dashboard “today’s workout” card | **4** | §5.4 |
| Plan strategy + habit checklists in web UI | **3b** | After week grid MVP |
| AI generate plan / workout | **4+** | After composer grammar is stable (B8) |
| Multi-week pinch zoom grid | **3.1** | Nice-to-have after single-week planner |

### 9.3 Interim state (current shipping)

Until Phase 3, Android keeps separate Programs and Unified Workouts tiles. Web companion ships **silo routine editors** and **catalog editor** ([START9_COMPANION_V1.md](START9_COMPANION_V1.md)) — these are library ingredients, not the scheduling layer. **Do not** change top-level Android navigation or add calendar assignment to routines before the Planner ships.

---

## 10. Success metrics (qualitative)

- User can build a **3-exercise, 3-set** workout in **under 2 minutes** without tutorials.
- User can **reorder exercises** without opening a separate dialog.
- Starting a live workout shows **pre-filled targets** matching composer.
- Same workout JSON edited on Start9 imports on phone **without data loss**.
- AI-generated workout opens in composer with **editable set rows**, not just exercise names.
- **Reference program test:** any hybrid session expressible using the [construction schema](../import/workouts_import_schema.md) checklist (§12) without workarounds.
- **Planner test (Phase 3):** drag exercise + circuit template onto two days; week view shows both; run from day sheet succeeds.

---

## 11. Open decisions

| Question | Options / guidance |
|----------|-------------------|
| Segment vs block naming in code | Prefer **segment** in UX copy; internal may stay `WorkoutBlock` for migration |
| Prescription on silo `WeightRoutine` | Deprecate silo routine as authorable template over time; **Workout is canonical**; weight routine remains library ingredient + legacy/import alias |
| Nostr d-tag for workout library | `erv/workouts/library` (planned Phase 2) |
| Planner tile name | “Planner”, “Training plan”, or “Plans & workouts” — pick before Android nav merge |
| Day drop creates vs appends workout | Default: **one workout per day slot**; append segments on repeat drops; “Add another workout” for AM/PM |
| Start9 SPA stack | React (`apps/web/web/`) — in use for companion |
| Iron Neck / pattern exercises | Custom exercise + rich `notes` vs future `PATTERN` prescription mode (figure-8 loops) |

---

## 12. Construction grammar (any workout)

The builder is **not** tied to one program. Any session is built by **stacking segments** (storyboard chapters), each with a **kind** that defines round/rest behavior, containing **items** (weight / cardio / mobility / note / rest).

**Authoritative JSON schema:** [workouts_import_schema.md](../import/workouts_import_schema.md)  
**Copy-paste fragments:** [examples/workout_schema_minimal_examples.json](examples/workout_schema_minimal_examples.json)

### 12.1 How to think about assembly

```
Workout = segments[] in order
  each segment = kind + title + items[] + optional rounds/restPolicy
  each item = type + modality-specific prescription
```

| User intent | Segment `kind` | Item types |
|-------------|----------------|------------|
| Traditional lifting block | `straight_sets` | `weight` |
| Pair / antagonist work | `superset` | 2–4 × `weight` + `restPolicy` |
| Round-robin accessories / core | `circuit` | N × `weight` / `time_based` + `rounds` |
| Bike / walk / erg steady state | `cardio` | `cardio` (steady, optional HR) |
| Sprints or 4×4-style HIIT | `interval` | `cardio` (flat or nested legs) |
| Warm-up mix (cardio + drills + notes) | `composite` | `cardio` + `mobility` + `note` |
| Stretch / cooldown | `mobility` | `mobility` |
| Sauna / cold plunge | `recovery` or `composite` | `heat_cold` |
| Red light session | `recovery` | `light` |
| Contrast (sauna then cold) | `composite` | two `heat_cold` items |

A complex week = **multiple workouts** in the library, each its own ordered segment list, plus **one plan** that assigns those workouts to days via `workoutRefs` ([programs_import_ai_guide.md](../import/programs_import_ai_guide.md)). The week UI may *feel* like dropping exercises directly onto days; storage still normalizes to workouts + refs (§2.1).

### 12.2 Capability checklist (acceptance)

Real-world hybrid training (cardio + intervals + circuits + lifting + flex in one week) motivated this list — but the builder must satisfy it **generically**:

| Capability | Schema hook |
|------------|-------------|
| HR-target or HR-range cardio | `hrTargetBpm`, `hrTargetMinBpm` / `Max` on `cardio` |
| Nested multi-round intervals | `outerRounds` + `legs[]` |
| Flat sprint intervals | `mode: sprint_intervals`, `rounds`, work/rest seconds |
| Circuits with round count | `kind: circuit`, `rounds`, `restPolicy` |
| Time-based strength | `prescription.mode: time_based` |
| Reps per side | `repsPerSide`, `side` |
| Rep ranges + RIR | `repRangeMin/Max`, `targetRir` |
| Max reps | `mode: max_reps` |
| Superset rest (between vs after round) | `restPolicy` on `superset` |
| Composite warm-ups | `kind: composite`, mixed `items` |
| Exercise alternatives | `alternativeExerciseIds` |
| Equipment / log prompts | `equipmentNotes`, `logFields` |
| Per-side stretch holds | `holdSecondsPerSide` |
| Custom lifts | `customExercises[]` in envelope |
| Sauna / cold plunge | `heat_cold` items |
| Light therapy (device/routine) | `light` items |
| Cross-silo content pickers | Weight + cardio + stretch + recovery libraries |

When every row is implemented, **any** workout composable from the schema — not one fixed template.

### 12.3 Live run (segment order)

Unified session walks `segments[]` top to bottom:

1. **Cardio / interval** → timer + optional HR banner + log prompts  
2. **Circuit** → round counter; cycle items; apply `restPolicy`  
3. **Superset** → A → rest → B → rest after round  
4. **Straight sets** → weight live UI with pre-filled prescription  
5. **Mobility / composite** → hold timers; run items in order  
6. **Recovery (`heat_cold` / `light`)** → Hot+Cold or Light Therapy timer; prescribed duration; optional temp at log  

Structure is fixed at run time; user only confirms or adjusts **actuals**.

---

## 13. Segment template library (starter catalog)

Ship **reusable segment patterns** (matching [workouts_import_schema.md](../import/workouts_import_schema.md) §7), not fixed programs:

| Template | Segment `kind` | Inserts |
|----------|----------------|---------|
| Cardio warm-up | `cardio` or `composite` | Steady duration + optional HR |
| Cooldown cardio | `cardio` | Easy steady |
| Zone 5 / nested intervals | `interval` | `outerRounds` + work/rest legs |
| Sprint intervals | `interval` | Flat rounds × work/rest |
| Core / accessory circuit | `circuit` | User picks N exercises + round count |
| Main lift + RIR | `straight_sets` | Rep range + `targetRir` scaffold |
| Strength superset | `superset` | Two slots + dual rest fields |
| Zone 2 steady | `cardio` | Duration + HR range + log fields |
| Flexibility holds | `mobility` | Hold timer scaffold |
| Composite warm-up | `composite` | Cardio + mobility + note slots |
| Sauna or cold plunge | `recovery` | `heat_cold` item + duration |
| Red light / light therapy | `recovery` | `light` item + device or routine |
| Contrast heat/cold | `composite` | Sauna + cold items in sequence |

Templates are **insert segment** actions in the composer `+` menu; users rename, reorder, and fill items.

---

## 14. Implementation phasing

Phases align [START9_COMPANION_V1.md](START9_COMPANION_V1.md) web work, Android merge ([PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md)), and the construction checklist (§12.2).

### 14.1 Phase 2 — Workout composer (session level)

**Goal:** Ship the construction grammar and library palette. **Do not** change how workouts are presented in the Android app yet (no Planner tile, no date assignment).

| Sub-phase | Deliverable | Surfaces |
|-----------|-------------|----------|
| **B1 — Schema** | Types in §3 + [workouts_import_schema.md](../import/workouts_import_schema.md) | Android models, import/export |
| **B2 — Composer MVP** | Storyboard, straight sets, rep range + RIR, circuits, composite segments | Android Composer route |
| **B3 — Cardio + HR** | Steady, HR targets/ranges, log fields, nested + flat intervals (HIIT) | Android + web types |
| **B4 — Superset + time** | Rest policy, time-based, max reps, per-side reps | Android Composer |
| **B5 — Mobility + recovery** | Per-side holds, alternatives; **heat_cold** + **light** items and silo launch | Android Composer |
| **B7 (partial) — Web composer** | `erv/workouts/library` publish; segment templates; drag-reorder storyboard | Start9 companion |

**Phase 2 acceptance test:** Build two unrelated workouts (e.g. cardio-only + lift+circuit) from templates only; publish to relay; import JSON on Android; run one with segment auto-advance.

**Phase 2 anti-patterns (do not ship):**

- Weekly calendar or drag onto days
- Scheduling silo routine ids per day on `erv/programs/master`
- Merging Programs + Unified Workouts Launch Pad tiles
- Dashboard “today’s workout” card

Silo routine editors (web Phase 1) continue as **ingredients** — weight / stretch / cardio lists with edit/delete and multi-leg cardio.

### 14.2 Phase 3 — Weekly planner (presentation shift)

**Goal:** One **Planner** section showcasing **all workouts for the week**; week-first drag-and-drop (§2, §5). This is when the product changes how training is organized — not before the composer exists.

| Sub-phase | Deliverable | Surfaces |
|-----------|-------------|----------|
| **B6 — Plan calendar MVP** | `workoutRefs` on `PlanDay`; week grid; drop palette (§2.2); auto-create/update workouts on drop | Android Planner + web |
| **3a — Nav merge** | Retire separate Programs + Unified Workouts tiles → **Planner** | Android Launch Pad |
| **3b — Plan richness** | Strategy (repeat / rotation / challenge), habit checklists, rest/custom notes, multi-week templates | Android (+ web follow) |

**Phase 3 acceptance test:** Drag a circuit template and a cardio routine onto two days; sync plan + workouts via relay; Android week view matches; tap day → run live session.

### 14.3 Phase 4 — Today surfacing + AI

| Sub-phase | Deliverable |
|-----------|-------------|
| **Dashboard today card** | Read active plan → show planned workout for calendar today (§5.4) |
| **B8 — AI** | Generate valid workout/plan envelopes from construction grammar; Maple on Start9 |

### 14.4 Sub-phase reference (original B1–B8)

| Id | Name | Phase |
|----|------|-------|
| B1 | Schema | 2 |
| B2 | Composer MVP | 2 |
| B3 | Cardio + HR | 2 |
| B4 | Superset + time | 2 |
| B5 | Mobility + recovery | 2 |
| B6 | Plan calendar / week grid | 3 |
| B7 | Start9 SPA composer | 2 (partial) → 3 (week grid) |
| B8 | AI | 4+ |

---

## 15. References

| Doc | Relevance |
|-----|-----------|
| [START9_COMPANION_V1.md](START9_COMPANION_V1.md) | Web companion phase checklist (silo routines shipped; composer = Phase 2) |
| [PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) | Merge sequencing, AI providers, nav collapse |
| [workouts_import_schema.md](../import/workouts_import_schema.md) | **Construction grammar** — any workout |
| [programs_import_ai_guide.md](../import/programs_import_ai_guide.md) | Plan scheduling (`workout` day blocks) |
| [DATA_IMPORT_EXPORT.md](../import/DATA_IMPORT_EXPORT.md) | Canonical bundle rules |
| [examples/workout_schema_minimal_examples.json](examples/workout_schema_minimal_examples.json) | Segment pattern fragments |
| `WeightModels.kt` | Logged sets vs templates today |
| `WeightExerciseSetsCard.kt` | Existing set UI patterns to reuse in composer |

---

*Last updated: June 2026 — Planner phasing; week-first drag-and-drop; workout-backed storage; Phase 2 composer vs Phase 3 weekly planner vs Phase 4 dashboard today.*
