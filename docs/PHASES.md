# ERV phased roadmap (short)

Lightweight summary for day-to-day use. Full specs live under
`docs/architecture/` but some of those files are large; if Cursor freezes,
read this file instead.

**Single-owner rule:** this file holds **status summaries only**. Each spec under
`architecture/` owns the **checkboxes** for its initiative (see the "Owner" column below).
Do not mirror checklists between files — link to the owner instead.

| Initiative | Checklist owner |
|------------|-----------------|
| Phase 1–3 (Start9 companion, composer, planner) | `architecture/START9_COMPANION_V1.md` |
| Pre-AI web prep W1–W6 | `architecture/ATHLETE_CONTEXT_WEB_PREP.md` §5 |
| Programs + Unified merge (Android nav, migration) | `architecture/PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md` §9 |
| My Day health schedule + Today view | `architecture/MY_DAY_HEALTH_SCHEDULE.md` §10 |
| Phase 4 AI coach M0–M6 | `architecture/AI_COACH_MAPLE_INTEGRATION.md` §9 |
| Privacy hardening P0–P3 | `architecture/CYPHERPUNK_PRIVACY_CHECKLIST.md` |

Last updated: September 2026.

## Shipped — Phase 1 (silo routines)

- Web editors for weight / stretch / cardio routines
- Catalog sync (`erv/catalog/*`) + catalog editor
- Silo routines are **ingredients**, not the schedule

## In progress — Phase 2 (~90%) workout composer

**Goal:** `erv/workouts/library` — storyboard workouts, sync web ↔ Android.

### Done

- Workout models (segments, weight/cardio/mobility/note/rest items)
- Android: library, composer, live run, Training → Workouts
- Web: workout builder (default landing), templates, relay publish
- Sync web → Android; JSON import; HR + intervals + alternatives
- Live run: weight logging, cardio timer, stretch player

### Remaining (Phase 2 tail)

- Live run: full coverage for `interval`, `recovery`, `freestyle`, `emom`
- Android composer: time-based mode tab
- Deeper nested cardio legs / per-set prescription polish

### Explicitly not Phase 2

- Weekly calendar / drag onto days
- Merging Programs + Unified Workouts tiles
- Dashboard “today’s workout” card
- Sauna / red-light inside workout storyboard (separate silos after session)

## In progress — Phase 3 weekly planner

Status per `START9_COMPANION_V1.md` (owner):

| Item | Status |
|------|--------|
| MVP week grid: assign saved workouts to weekdays on web, publish `workout` blocks (`workoutId`) | Shipped |
| Android: show synced saved-workout blocks in Programs / Launch Pad, launch live runner | Shipped |
| Week grid: drop exercises, routines, templates, and saved workouts onto days | Planned |
| Android: merge Programs + Unified Workouts → single Planner tile (Training is the hub) | Planned |
| Plan strategy, habits, rest notes (3b) | Planned |

**Acceptance test:** Assign two different workouts to two days on web → sync →
Android week view matches → tap day → run live session.

## Planned — My Day (health schedule + Today)

Spec: **[My Day — unified health schedule](architecture/MY_DAY_HEALTH_SCHEDULE.md)** (draft for review).
Schedules supplements, light, sauna/cold, stretching, fasting, body check-ins, and habits by
weekday and part of day (with single-day changes); Today view checks items off from existing
logs; any activity can be logged after the fact, planned or not, and counts toward goals;
routine reminders migrate into it. Training stays in Training and appears as one "Today's
training" row. Category sections later become libraries + history. Android first (M1–M7), web
later (M8). New d-tag `erv/myday/schedule`.

## Pre-AI web prep (parallel track)

Build athlete context on the **Start9 companion** before Phase 4 AI. Android stays live
logging and live session execution; web owns profile, history, analytics, and all AI generation.

See **[Athlete context & web prep](architecture/ATHLETE_CONTEXT_WEB_PREP.md)** (W1–W6).

| Package | Status |
|---------|--------|
| W1 Training profile (`erv/training-profile`) | Shipped |
| W2 History on web | Shipped |
| W3 Training snapshot | Shipped (local compute; relay publish deferred) |
| W4 Prescription-aware builder polish | Shipped |
| W5 Planner | = Phase 3 above |
| W6 "Copy training context" bundle (AI dry run) | Shipped |

## Later — Phase 4

- Dashboard card: planned workout for today (read-only)
- Web-only AI plan/workout generation (Maple / optional on StartOS) — requires W1–W3 baseline

### Phase 4 AI coach (web/Start9 only) — planned

Spec: **[AI coach — Maple Proxy integration](architecture/AI_COACH_MAPLE_INTEGRATION.md)**.
Server relays `/api/ai/*` to the `maple-proxy` StartOS sibling; web builds context and validates
drafts; model chosen from `/v1/models`; off by default; never auto-publish.

| Milestone | Scope | Status |
|-----------|-------|--------|
| M0 | Web workout import envelope + preview (prereq) | Shipped — `aiMeta` stays a web-only sidecar |
| M1 | Maple detection, AI settings, connection test, model picker | Implemented — confirm on StartOS with Maple Proxy installed |
| M2 | Server relay (`/api/ai/*`, queue, caps, SSE) | Implemented — Settings relay check sends one message |
| M3 | Coach review (read-only analysis) on Progress | Implemented — Progress streams a sectioned review |
| M4 | Workout suggest-changes / generate → diff preview → draft | Planned |
| M5 | Planner week suggestions | Planned (after Phase 3) |
| Later | Insights synced to Android, scheduled generation, agent key | Not scheduled |

## Key Nostr d-tags

| d-tag | Purpose |
|-------|---------|
| `erv/weight/routines` | Weight routine templates |
| `erv/stretching/routines` | Stretch routines |
| `erv/cardio/routines` | Cardio routines + custom types |
| `erv/weight/YYYY-MM-DD` | Strength day log; split as `erv/weight/YYYY-MM-DD/session/<workoutId>` if too large |
| `erv/cardio/YYYY-MM-DD` | Cardio day log; split as `erv/cardio/YYYY-MM-DD/session/<sessionId>` if too large |
| `erv/catalog/weight` | Exercise catalog |
| `erv/catalog/stretch` | Stretch catalog |
| `erv/catalog/cardio` | Cardio activity catalog |
| `erv/workouts/library` | Workout storyboard library (Phase 2); index + per-workout shards |
| `erv/workouts/library/workout/<id>` | One cohesive workout; segment shards under `.../segment/<id>` if needed |
| `erv/programs/master` | Weekly plan (Phase 3) |
| `erv/myday/schedule` | My Day health schedule (planned) |
| `erv/training-profile` | Athlete profile + style presets (pre-AI web) |
| `erv/equipment` | Home gym + exercise packs |
| `erv/media/library` | Encrypted manifest for Blossom-backed media blobs |

## Where to look in code

| Area | Path |
|------|------|
| Web workout builder | `apps/web/web/src/routes/WorkoutsTab.tsx` |
| Web workout JSON | `apps/web/web/src/lib/workoutTraining.ts` |
| Android workout sync | `app/.../workouts/WorkoutSync.kt` |
| Android workout UI | `app/.../ui/workouts/` |
| Start9 package build | `packages/start9/build.sh` |

## Active specs (architecture/)

| Doc | Purpose |
|-----|---------|
| `ATHLETE_CONTEXT_WEB_PREP.md` | Pre-AI web prep (W1–W6) — track checkboxes here |
| `START9_COMPANION_V1.md` | Start9 + Phase 1–4 checklist |
| `WORKOUT_PLAN_EDITOR_SPEC.md` | Composer grammar (large) |
| `PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md` | Phase 3 planner merge + AI principles (§6) |
| `AI_COACH_MAPLE_INTEGRATION.md` | Phase 4 AI coach — Maple Proxy on Start9, milestones M0–M6 |
| `START9_SCAFFOLD_AUDIT.md` | Build / Cursor freeze notes |

## Full architecture docs (archived — open with care)

Open in an external editor or plain-text mode if Cursor freezes:

- `docs/archive/PLAN_OF_ACTION.md` — whole-app Nostr + silo reference (~104 KB)
- `docs/archive/WEIGHT_TRAINING_SPEC.md` — weight silo implementation diary (shipped)
- `docs/archive/vision/PROTOCOL_GRAPH.md` — web-of-trust vision (Phase 4+)

See `docs/archive/README.md` for the full archive index.
