# ERV — Workout construction schema (import / export / AI)

This document defines **how to represent any single training session** in JSON: the grammar the **Workout Composer**, **Start9 plan editor**, and **AI import** will share.

It is **not** a fixed program. Use these **primitives** and **segment patterns** to build cardio-only days, lifting days, hybrid sessions, mobility, or anything else.

**Related:**

- Weekly **scheduling** (assign workouts to days) — [programs_import_ai_guide.md](programs_import_ai_guide.md) with `workout` day blocks (planned).
- Product UX — [WORKOUT_PLAN_EDITOR_SPEC.md](../architecture/WORKOUT_PLAN_EDITOR_SPEC.md).
- Minimal JSON fragments — [../architecture/examples/workout_schema_minimal_examples.json](../architecture/examples/workout_schema_minimal_examples.json).

**Status:** **`ervWorkoutImportVersion: 1` implemented** in Android + web (June 2026). Core library sync, merge-by-id, and multi-modality segments are live. Full grammar (intervals, recovery silos, plan refs) remains Phase 2–3 work — see [WORKOUT_PLAN_EDITOR_SPEC.md §12.2](../architecture/WORKOUT_PLAN_EDITOR_SPEC.md) and [START9_COMPANION_V1.md Phase 2](../architecture/START9_COMPANION_V1.md).

---

## 1. Mental model — how to construct any workout

A **Workout** is an **ordered list of segments** (storyboard scenes). Each segment has a **kind** that controls rest semantics and live-run behavior. Inside each segment are **items** — the smallest prescribable units (one lift, one cardio block, one stretch, one coach note).

```
Workout
  └── segments[]     ← top-to-bottom session order (this IS the storyboard)
        └── items[]  ← polymorphic: weight | cardio | mobility | note | rest
```

**Rules:**

1. **Order matters** — live run walks segments top to bottom.
2. **One segment = one UI “chapter”** — warm-up, main work, finisher, cooldown; user can collapse/reorder chapters.
3. **Prescription ≠ log** — targets in JSON; actual reps/weight/time are captured at run time and stored in day logs.
4. **Mix modalities freely** — composite segments exist so warm-ups can combine bike + mobility without inventing a new segment kind.
5. **Reuse segment templates** — same JSON shapes for “3× circuit”, “HR steady cardio”, “superset pair”; only items and numbers change.

Complex real-world sessions (HIIT + core + lifting + flex in one week) are just **different orderings of the same primitives** — not a separate schema per workout style.

### 1.1 Why this merges Programs and Unified Workouts

| Before (split) | After (merged) |
|----------------|--------------|
| Programs: inline blocks per modality | **Plan** assigns `workoutId` per day |
| Unified workouts: weight/cardio/stretch only | **Workout** = full session storyboard for **all** modalities |
| Three authoring paths for “today’s training” | One composer + one live run |

Any program week is: **N workouts in the library** + **one plan** that schedules them. Hybrid days (lift + sauna + red light) are one workout with multiple segment kinds — not three separate app areas.

### 1.2 Content sources (use what ERV already has)

The composer **must not invent parallel catalogs**. Pickers read from existing app state:

| Modality | Source in app | JSON reference |
|----------|---------------|----------------|
| Weight | Built-in list + user exercises | `exerciseId` → [weight_training_builtin_exercise_ids.md](weight_training_builtin_exercise_ids.md) |
| Cardio | `CardioBuiltinActivity` + saved routines | `activity` enum or `cardioRoutineId` |
| Stretch | Bundled `stretch_catalog.json` + saved routines | `catalogId` or `stretchRoutineId` |
| Sauna / cold | Hot+Cold silo (no exercise list) | `heatCold.mode`: `SAUNA` \| `COLD_PLUNGE` |
| Red light / light therapy | User `LightDevice` + `LightRoutine` library | `lightDeviceId`, `lightRoutineId` |

If the user has not added light devices yet, the composer can offer **“add device”** (deep link to Light Therapy) or a duration-only placeholder with `deviceId` null.

---

## 2. Import envelope

```json
{
  "ervWorkoutImportVersion": 1,
  "customExercises": [],
  "workouts": []
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `ervWorkoutImportVersion` | **Yes** | Integer `1` |
| `customExercises` | No | Same shape as weight import `WeightExercise`; use when an item needs a lift not in the built-in catalog |
| `workouts` | **Yes** | One or more workout objects |

Unknown top-level keys should be omitted.

**Merge:** keyed by `workout.id`; same id replaces existing library entry. **Implemented** in `WorkoutImport.kt` / `WorkoutRepository.kt` (Android) and web workout library publish.

---

## 3. `Workout` object

| Field | Required | Notes |
| --- | --- | --- |
| `id` | No | UUID; omit on first create |
| `name` | **Yes** | Display name |
| `description` | No | Free text |
| `sourceLabel` | No | e.g. `"Manual"`, `"AI · Maple"` |
| `tags` | No | String array for library filter |
| `segments` | **Yes** | Ordered array; may be empty only for placeholders |

---

## 4. `WorkoutSegment`

| Field | Required | Notes |
| --- | --- | --- |
| `id` | No | UUID per segment |
| `kind` | **Yes** | Wire string; see §5 |
| `title` | No | User label: "Warm-up", "Muscle base", "Core circuit" |
| `notes` | No | Segment-level coach text |
| `items` | **Yes** | Array of items; may be empty for titled spacer |
| `rounds` | For `circuit`, some `interval` | Integer ≥ 1 |
| `restPolicy` | For `superset`, `circuit` | See §8 |
| `restAfterSeconds` | No | Rest before the **next** segment starts |

---

## 5. Segment kinds (`kind`)

| Wire value | Use when | Live run behavior |
| --- | --- | --- |
| `straight_sets` | One or more lifts done as traditional sets | Weight silo; sets pre-filled from prescription |
| `superset` | 2–4 lifts alternated each round | Cycle items A→B→…; dual rest policy |
| `circuit` | Round-robin through all items N times | Round counter; optional short rest between items |
| `composite` | Mixed warm-up / transition (cardio + mobility + notes) | Run items in order; may cross silos |
| `cardio` | Single cardio prescription (often one item) | Cardio timer + optional HR cues |
| `interval` | Work/rest timer (sprints, 4×4, erg intervals) | Interval UI |
| `mobility` | Stretch / flex block | Hold timers; per-side advance |
| `emom` | Time-boxed recurring work | Clock-driven rounds (v1.1 if deferred) |
| `recovery` | Sauna, cold plunge, or light therapy | Hot+Cold timer or Light Therapy timer (existing silos) |
| `freestyle` | User picks weight or cardio at launch | FLEX picker |

**Choosing a kind:** pick the kind that matches **rest and round semantics**, not the sport name. "Core circuit" → `circuit`. "Bench then rows" → `superset`. "Bike then leg swings" → `composite`.

---

## 6. Items (`items[]`)

Every item has:

| Field | Required | Notes |
| --- | --- | --- |
| `type` | **Yes** | `weight` \| `cardio` \| `mobility` \| `heat_cold` \| `light` \| `note` \| `rest` |
| `id` | No | UUID |
| `title` | No | Override display name |

### 6.1 `type: "weight"`

| Field | Required | Notes |
| --- | --- | --- |
| `exerciseId` | **Yes** | Built-in `erv-weight-exercise-*` or custom id from `customExercises` |
| `alternativeExerciseIds` | No | Pick one at launch (e.g. two warm-up variants) |
| `prescription` | **Yes** | See §7 |

### 6.2 `type: "cardio"`

| Field | Required | Notes |
| --- | --- | --- |
| `cardio` | **Yes** | See §7.4 |

### 6.3 `type: "mobility"`

| Field | Required | Notes |
| --- | --- | --- |
| `mobility` | **Yes** | See §7.5 |

### 6.5 `type: "heat_cold"`

Sauna or cold plunge — uses the same modes as `HeatColdMode` in the app (`SAUNA`, `COLD_PLUNGE`).

| Field | Required | Notes |
| --- | --- | --- |
| `heatCold` | **Yes** | See §7.6 |

### 6.6 `type: "light"`

Red-light / NIR or other light therapy — references the user’s Light Therapy library.

| Field | Required | Notes |
| --- | --- | --- |
| `light` | **Yes** | See §7.7 |

### 6.7 `type: "note"`

| Field | Required | Notes |
| --- | --- | --- |
| `text` | **Yes** | Coach cue; no timer |

### 6.8 `type: "rest"`

| Field | Required | Notes |
| --- | --- | --- |
| `durationSeconds` | **Yes** | Passive rest item inside a segment |

---

## 7. Prescriptions

### 7.1 Weight — `prescription.mode`

| Mode | Wire value | Use for |
| --- | --- | --- |
| Straight sets | `straight` | Fixed or ranged reps; optional load; RIR/RPE |
| Intervals | `interval` | HIIT lift timer (work/rest rounds) |
| Time-based | `time_based` | Planks, carries, holds (seconds) |
| Max reps | `max_reps` | "Max reps" sets (e.g. AMRAP single set) |
| AMRAP | `amrap` | Time cap + rep target (v1.1 if deferred) |

**Shared fields (mode-dependent):**

| Field | Notes |
| --- | --- |
| `sets[]` | Array of `PrescriptionSet` |
| `setCount` | Shorthand when all working sets match |
| **`targetReps`** | **Shipped (June 2026).** Single rep target for live UI ghost display; use when all working sets share one rep count. Distinct from `repRangeMin`/`Max`. |
| `repRangeMin`, `repRangeMax` | When reps vary by feel (e.g. 8–12) |
| **`durationSeconds`** | **Shipped (June 2026).** Hold/plank target at exercise level; live run shows countdown (30s default if unset). Prefer `time_based` mode when full mode switch ships. |
| `targetRir` | Reps in reserve target for the exercise |
| `intensityLabel` | Display-only: `"strict 2 RIR"` |
| `restBetweenSetsSeconds` | Between sets of **this** exercise |
| `restAfterExerciseSeconds` | After finishing this exercise (supersets use segment `restPolicy` instead) |
| `warmupSets[]` | Optional lighter sets |

**`PrescriptionSet`:**

| Field | Notes |
| --- | --- |
| `reps` | Integer; omit if using range at exercise level |
| `repsPerSide` | Per-side / per-leg reps |
| `side` | `left` \| `right` \| `each` \| `alternating` |
| `weightKg` | Null = choose at session time (common for RIR work) |
| `rpe`, `rir` | Per-set intensity |
| `durationSeconds` | For `time_based` |
| `isWarmup`, `isDropSet` | UI flags |
| `notes` | Tempo, regression, pattern cues |

### 7.2 Minimal weight patterns (copy-paste building blocks)

**Fixed sets:**

```json
{
  "type": "weight",
  "exerciseId": "erv-weight-exercise-bench-v1",
  "prescription": {
    "mode": "straight",
    "setCount": 3,
    "restBetweenSetsSeconds": 90,
    "sets": [
      { "reps": 8, "weightKg": 60 },
      { "reps": 8, "weightKg": 60 },
      { "reps": 8, "weightKg": 60 }
    ]
  }
}
```

**Rep range + RIR (load chosen at run time):**

```json
"prescription": {
  "mode": "straight",
  "setCount": 3,
  "repRangeMin": 8,
  "repRangeMax": 12,
  "targetRir": 2,
  "intensityLabel": "strict 2 RIR",
  "restBetweenSetsSeconds": 120,
  "sets": [{ "reps": 8, "weightKg": null, "rir": 2 }]
}
```

**Per-side reps (circuit item):**

```json
"prescription": {
  "mode": "straight",
  "setCount": 1,
  "sets": [{ "repsPerSide": 10, "side": "each" }]
}
```

**Time-based (plank, farmer carry):**

```json
"prescription": {
  "mode": "time_based",
  "durationSeconds": 45
}
```

**Max reps:**

```json
"prescription": {
  "mode": "max_reps",
  "setCount": 3
}
```

**Lift intervals (HIIT timer):**

```json
"prescription": {
  "mode": "interval",
  "intervals": 8,
  "workSeconds": 40,
  "restSeconds": 20,
  "targetWeightKg": null
}
```

### 7.3 Segment-level: superset & circuit

**Superset** — use `kind: "superset"` + `restPolicy`:

```json
{
  "kind": "superset",
  "title": "Antagonist pair",
  "rounds": 3,
  "restPolicy": {
    "restBetweenItemsSeconds": 60,
    "restAfterRoundSeconds": 60
  },
  "items": [ "…exercise A…", "…exercise B…" ]
}
```

**Circuit** — use `kind: "circuit"` + `rounds`:

```json
{
  "kind": "circuit",
  "title": "Accessory circuit",
  "rounds": 3,
  "restPolicy": {
    "restBetweenItemsSeconds": 15,
    "restAfterRoundSeconds": 90
  },
  "items": [ "…", "…", "…" ]
}
```

Circuit items are often `setCount: 1` per round per exercise.

### 7.4 Cardio — `cardio` object

| Field | Notes |
| --- | --- |
| `activity` | `CardioBuiltinActivity` enum name: `RUN`, `BIKE`, `STATIONARY_BIKE`, `WALK`, … |
| `cardioRoutineId` | Optional saved routine instead of activity |
| `mode` | `steady` \| `interval_template` \| `sprint_intervals` |
| `targetMinutes` | Steady duration |
| `targetDistanceMeters` | Optional |
| `equipmentNotes` | e.g. weighted vest |
| `hrTargetBpm` | Single HR target |
| `hrTargetMinBpm`, `hrTargetMaxBpm` | HR range (Zone 2 style) |
| `hrZoneLabel` | Display: `"Zone 2"`, `"Zone 5"` |
| `logFields` | Array: `INCLINE`, `SPEED`, `DISTANCE`, `NOTES` — prompts at log time |
| `outerRounds` | For nested templates (e.g. 3 rounds of 4×4) |
| `legs[]` | `{ workSeconds, restSeconds, label?, hrTargetBpm? }` |
| `rounds`, `workSeconds`, `restSeconds` | Flat sprint-style intervals |

**Steady + HR range:**

```json
{
  "type": "cardio",
  "cardio": {
    "activity": "WALK",
    "mode": "steady",
    "targetMinutes": 45,
    "equipmentNotes": "weighted vest",
    "hrTargetMinBpm": 110,
    "hrTargetMaxBpm": 130,
    "logFields": ["INCLINE", "SPEED"]
  }
}
```

**Nested interval block (e.g. multiple rounds of long work/rest):**

```json
{
  "kind": "interval",
  "title": "Zone 5 intervals",
  "items": [{
    "type": "cardio",
    "cardio": {
      "activity": "STATIONARY_BIKE",
      "mode": "interval_template",
      "outerRounds": 3,
      "hrTargetBpm": 168,
      "legs": [{ "workSeconds": 240, "restSeconds": 240, "label": "4×4 work leg" }]
    }
  }]
}
```

**Flat sprints:**

```json
"cardio": {
  "activity": "STATIONARY_BIKE",
  "mode": "sprint_intervals",
  "rounds": 10,
  "workSeconds": 60,
  "restSeconds": 60
}
```

### 7.5 Mobility — `mobility` object

| Field | Notes |
| --- | --- |
| `catalogId` | Built-in stretch id from bundled catalog |
| `customName` | When not in catalog |
| `displayNameOverride` | Shown in UI instead of catalog name |
| `holdSeconds` | Single hold |
| `holdSecondsPerSide` | Hold each side (e.g. 120s/side) |
| `side` | `left` \| `right` \| `each` \| `alternating` |
| `notes` | Form cues |

**Composite warm-up** — segment `kind: "composite"` with multiple items (any silo):

```json
{
  "kind": "composite",
  "title": "Warm-up",
  "items": [
    {
      "type": "cardio",
      "cardio": { "activity": "STATIONARY_BIKE", "mode": "steady", "targetMinutes": 3 }
    },
    {
      "type": "mobility",
      "mobility": { "customName": "Leg swings", "holdSeconds": 30, "side": "alternating" }
    },
    {
      "type": "note",
      "text": "Build range gradually; no static stretching before main lift."
    }
  ]
}
```

### 7.6 Heat / cold — `heatCold` object

| Field | Required | Notes |
| --- | --- | --- |
| `mode` | **Yes** | `SAUNA` or `COLD_PLUNGE` (exact enum names) |
| `targetMinutes` | **Yes** | Prescribed session length; timer at run time |
| `targetTempValue` | No | Optional hint; user may adjust when logging |
| `targetTempUnit` | No | `FAHRENHEIT` or `CELSIUS` if temp set |
| `notes` | No | Hydration, contrast order, etc. |

**Example — sauna finisher after lifting:**

```json
{
  "kind": "recovery",
  "title": "Sauna",
  "items": [{
    "type": "heat_cold",
    "title": "Sauna",
    "heatCold": {
      "mode": "SAUNA",
      "targetMinutes": 15,
      "notes": "Hydrate; stop if dizzy."
    }
  }]
}
```

**Example — contrast (two items in one segment or back-to-back segments):**

```json
{
  "kind": "composite",
  "title": "Contrast",
  "items": [
    { "type": "heat_cold", "heatCold": { "mode": "SAUNA", "targetMinutes": 12 } },
    { "type": "heat_cold", "heatCold": { "mode": "COLD_PLUNGE", "targetMinutes": 3 } }
  ]
}
```

### 7.7 Light therapy — `light` object

| Field | Required | Notes |
| --- | --- | --- |
| `lightRoutineId` | No | Saved routine from Light Therapy (name, device, default duration) |
| `lightDeviceId` | No | Device from user catalog if not using a routine |
| `targetMinutes` | No | Overrides routine default; required if no routine |
| `timeOfDay` | No | `MORNING`, `AFTERNOON`, `NIGHT` — hint for UI |
| `notes` | No | Distance from panel, eyes closed, etc. |

At least one of `lightRoutineId`, `lightDeviceId`, or `targetMinutes` should be set.

**Example — red light after workout:**

```json
{
  "kind": "recovery",
  "title": "Red light",
  "items": [{
    "type": "light",
    "light": {
      "lightRoutineId": "<uuid-from-user-light-library>",
      "targetMinutes": 10,
      "notes": "Post-session recovery panel"
    }
  }]
}
```

If the user has no devices/routines yet, allow `targetMinutes` only with `notes` describing setup; prompt to configure Light Therapy on first run.

---

## 8. `restPolicy` (superset & circuit)

| Field | Notes |
| --- | --- |
| `restBetweenItemsSeconds` | Between exercises within one round |
| `restAfterRoundSeconds` | After completing all items in a round |
| `rounds` | May duplicate segment `rounds`; segment wins if both present |

Superset **rounds** = number of times the full A→B sequence repeats.

---

## 9. Assigning workouts to a plan

Workouts are **library objects**. The plan only references them:

```json
{
  "kind": "workout",
  "workoutId": "<uuid-from-workouts-library>",
  "title": "Optional override for Launch Pad"
}
```

Full plan envelope: [programs_import_ai_guide.md](programs_import_ai_guide.md). A week with six different sessions = six entries in `workouts[]` plus six `workout` blocks on `dayOfWeek` 1–6.

---

## 10. Validation rules (import / AI)

1. Every `exerciseId` must exist in built-ins, `customExercises`, or the device library after merge.
2. `segments` must be non-empty for a runnable workout (warn on empty).
3. `kind` must be a known wire string (§5).
4. `circuit` / `superset` should have ≥ 2 items (warn if 1).
5. `rounds` ≥ 1 when present.
6. Cardio `activity` must be exact enum name (see cardio import guide).
7. Do **not** put logged session data in workout templates (no finished timestamps, no actual HR samples).
8. Prefer **segment titles** for human readability; do not encode structure only in `notes`.

---

## 11. Composer ↔ JSON (UI mapping)

| UI action | JSON effect |
| --- | --- |
| Add segment from template | Insert object from §7 patterns |
| Drag segment | Reorder `segments[]` |
| Drag exercise | Reorder within `items[]` or move across segments |
| Expand set editor | Edit `prescription.sets[]` |
| Set circuit rounds | `segment.rounds` |
| Superset rest fields | `restPolicy` |
| Duplicate workout | Copy whole `Workout` with new `id` |
| Run | Read-only traverse `segments[]`; no structural mutation |

---

## 12. Capability checklist (acceptance for “any workout”)

The builder and schema are complete when **all** of the following are expressible without workarounds.

**Legend:** ✅ · 🟡 partial · ❌

- [x] Straight sets with fixed reps, rep ranges, RIR/RPE, and null weight — ✅ (🟡 no full per-set editor)
- [x] Superset with separate between-exercise and after-round rest — ✅
- [x] Circuit with round count — ✅ (🟡 weight items only in live run round-robin)
- [x] Composite segments (cardio + mobility + notes) — ✅ web Flow block
- [x] **`targetReps` ghost target** for live logging — ✅ *(see WORKOUT_PLAN_EDITOR_SPEC §15)*
- [x] Timed holds via `durationSeconds` — 🟡 live countdown; not full `time_based` mode
- [x] Per-side stretch holds — ✅ web (`holdSecondsPerSide`)
- [x] Cross-silo picker (weight + cardio + stretch in one builder) — ✅ web
- [x] Nostr sync `erv/workouts/library` — ✅
- [ ] Steady cardio with duration and optional HR target or range — 🟡 duration + single BPM; no range
- [ ] Flat work/rest intervals and nested multi-round interval templates — ❌
- [ ] Time-based and max-reps strength items — ❌ (enum exists; UI pending)
- [ ] Per-side reps (strength) — ❌
- [ ] Exercise alternatives (OR picker) — ❌
- [ ] Cardio log prompts (incline, speed, etc.) — ❌
- [ ] Custom exercises in envelope — ❌ (merge supports; no import UI)
- [ ] **Sauna and cold plunge** (`heat_cold` items → Hot+Cold timer) — ❌
- [ ] **Light therapy** (`light` items → device/routine picker + timer) — ❌
- [ ] Plan assigns workout by id only — ❌ Phase 3
- [ ] Live run launches silo timers for cardio/mobility/recovery — 🟡 weight ✅; others stub

Hybrid multi-day programs are **not** a special case — they are N workouts built from this checklist, assigned on a plan.

---

## 13. AI / Maple prompt hint

When generating workouts, output **one envelope** with `workouts[]`. Use §7 minimal patterns; combine segments in session order. Reference **real** built-in weight ids, cardio enum names, and stretch catalog ids where possible; use `customExercises` for non-catalog lifts. For light therapy, prefer `lightRoutineId` / `lightDeviceId` from a supplied device context bundle (same idea as programs AI bundle). Do not emit markdown fences. Include `customExercises` for any non-catalog lift names.

---

*Last updated: June 2026 — v1 implemented in app; checklist reflects partial Phase 2 completion.*
