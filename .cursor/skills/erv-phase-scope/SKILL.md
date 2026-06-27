---
name: erv-phase-scope
description: Scopes ERV feature work to the current roadmap phase and silo boundaries. Use when planning features, implementing Programs, Planner, workout composer, dashboard, AI, or when unsure whether a change belongs in Phase 2 vs 3 vs 4.
---

# ERV phase scope

**Read [`docs/PHASES.md`](../../docs/PHASES.md) first.** It is the lightweight source of truth. Avoid opening huge docs unless necessary — these freeze Cursor and are listed in `.cursorignore`:

- `docs/archive/PLAN_OF_ACTION.md`
- `docs/architecture/WORKOUT_PLAN_EDITOR_SPEC.md`

## Current focus (June 2026)

| Phase | Status | Goal |
|-------|--------|------|
| **1** | Shipped | Silo routines + catalog sync (`erv/catalog/*`, weight/stretch/cardio routines) |
| **2** | ~90% | Workout composer + `erv/workouts/library`, web ↔ Android sync, live run |
| **3** | Next | Weekly planner, `erv/programs/master`, merge Programs + Unified tiles |
| **4** | Later | Dashboard “today” card, AI plan/workout generation |

## Phase 2 — in scope

- Workout storyboard (segments, weight/cardio/mobility/note/rest items)
- Live run gaps: `interval`, `recovery`, `freestyle`, `emom`
- Android composer polish (time-based mode tab, nested cardio legs)
- Web workout builder (`apps/web/web/src/routes/WorkoutsTab.tsx`)

## Phase 2 — explicitly out of scope

Do **not** implement these while closing Phase 2 unless the user explicitly requests Phase 3+ work:

- Weekly calendar / drag workouts onto days
- Merging Programs + Unified Workouts Launch Pad tiles
- Dashboard “today’s workout” card
- Sauna / red-light **inside** workout storyboard (separate silos after session)

## Phase 3 acceptance test (when that work starts)

Assign two different workouts to two days on web → sync → Android week view matches → tap day → run live session.

## Silo boundaries

From [`CONTRIBUTING.md`](../../CONTRIBUTING.md): health categories (Stretching, Weight Training, Cardio, Sauna, Cold Plunge, Light Therapy, Supplements, Sleep) are separate silos. Prefer scoped PRs — one silo or shared infra (Nostr, theme, navigation) at a time.

## Where to look

| Area | Path |
|------|------|
| Web workout builder | `apps/web/web/src/routes/WorkoutsTab.tsx` |
| Web workout JSON | `apps/web/web/src/lib/workoutTraining.ts` |
| Android workout sync | `app/.../workouts/WorkoutSync.kt` |
| Android workout UI | `app/.../ui/workouts/` |
| Planner merge plan | `docs/architecture/PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md` |
| Pre-AI athlete context | `docs/architecture/ATHLETE_CONTEXT_WEB_PREP.md` |
| Start9 checklist | `docs/architecture/START9_COMPANION_V1.md` |

## Before implementing

1. Confirm which phase the request belongs to.
2. If it spans phases, propose splitting the PR or ask the user to confirm scope expansion.
3. For sync/schema changes, also read **erv-nostr-sync-contract**.
4. For Profile / Progress / training history on web, read
   **[`docs/architecture/ATHLETE_CONTEXT_WEB_PREP.md`](../../docs/architecture/ATHLETE_CONTEXT_WEB_PREP.md)**.
5. For UI on both platforms, also read **erv-cross-platform-ui**.

## Do not

- Pull in Phase 3 planner UI because it “fits naturally” with composer work.
- Open archived megadocs when `PHASES.md` + targeted grep suffice.
- Refactor unrelated silos in the same PR as a focused feature.
