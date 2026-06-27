---
name: erv-nostr-sync-contract
description: Keeps ERV Nostr sync contracts aligned between web companion and Android — d-tags, kind 30078, JSON envelopes, merge rules, and catalog IDs. Use when changing sync models, import/export JSON, relay publish/fetch, d-tags, NIP-44 encryption, or web-to-phone data flow.
---

# ERV Nostr sync contract

ERV stores app data as **NIP-44 encrypt-to-self** events, **kind 30078**, keyed by **`erv/*` d-tags**. Web and Android must agree on tag, envelope shape, field names, and merge semantics.

## Canonical d-tags

Source of truth for server allowlist: `apps/web/server/src/erv_tags.rs`.

| d-tag | Purpose | Phase |
|-------|---------|-------|
| `erv/weight/routines` | Weight routine templates | 1 |
| `erv/stretching/routines` | Stretch routines | 1 |
| `erv/cardio/routines` | Cardio routines | 1 |
| `erv/catalog/weight` | Exercise catalog | 1 |
| `erv/catalog/stretch` | Stretch catalog | 1 |
| `erv/catalog/cardio` | Cardio activity catalog | 1 |
| `erv/workouts/library` | Workout storyboard library | 2 |
| `erv/programs/master` | Weekly plan | 3 |
| `erv/training-profile` | Athlete profile + style presets | W1 |
| `erv/training-snapshot` | Computed baseline (local v1; publish TBD) | W3 |
| `erv/equipment` | Home gym + exercise packs | — |
| `erv/weight/exercises` | Legacy/auxiliary weight exercises tag | — |

Day-log tags like `erv/weight/YYYY-MM-DD` are **read** on Android but are not in the web publish allowlist.

## Android sync entry points

| d-tag area | Kotlin |
|------------|--------|
| Weight routines | `app/.../weighttraining/WeightSync.kt` |
| Stretch routines | `app/.../stretching/StretchingSync.kt` |
| Cardio routines | `app/.../cardio/CardioSync.kt` |
| Catalogs | `app/.../nostr/CatalogSync.kt` |
| Workouts library | `app/.../workouts/WorkoutSync.kt` |
| Programs | `app/.../programs/ProgramSync.kt` |
| Training profile | `app/.../nostr/TrainingProfileSync.kt` |
| Equipment | `app/.../nostr/FitnessEquipmentSync.kt` |

## Web counterparts

| Area | TypeScript / Rust |
|------|-------------------|
| d-tag constants + publish allowlist | `apps/web/server/src/erv_tags.rs` |
| Workout library models | `apps/web/web/src/lib/workoutTraining.ts` |
| Catalog + built-in IDs | `apps/web/web/src/lib/catalog.ts` |
| Weight/stretch/cardio routine payloads | `apps/web/web/src/lib/weightTraining.ts`, `stretchTraining.ts`, `cardioTraining.ts` |
| NIP-44 + kind 30078 publish | `apps/web/server/src/nostr_support.rs`, `routes.rs` |

## Contract rules (every change)

1. **Same d-tag string** on web and Android (grep both codebases).
2. **Kind 30078** for app data payloads unless explicitly documented otherwise.
3. **Envelope field names** must match (e.g. `{ "routines": [...] }` ↔ `WeightRoutinesPayload(routines)`).
4. **Entity fields** use the same names and types; prefer additive changes.
5. **Android parses with `ignoreUnknownKeys = true`** — new web-only fields are OK; renamed/removed fields break old clients.
6. **Merge by `lastModifiedEpochSeconds`** (and id): newer wins; new ids from remote are added when local is null.
7. **Built-in catalog IDs** must match (e.g. `erv-weight-exercise-bench-v1`) — see `catalog.ts` and `WeightDefaultCatalog.kt`.
8. **Same npub/nsec + relay** on both ends for encrypt-to-self to decrypt.

## Verified example (weight routines)

From [`docs/architecture/START9_SCAFFOLD_AUDIT.md`](../../docs/architecture/START9_SCAFFOLD_AUDIT.md):

| Step | Web | Android |
|------|-----|---------|
| d-tag | `erv/weight/routines` | `WEIGHT_ROUTINES_D_TAG` |
| Kind | 30078 | 30078 |
| Envelope | `{ "routines": [...] }` | `WeightRoutinesPayload` |
| Routine fields | `id`, `name`, `exerciseIds`, `notes`, `lastModifiedEpochSeconds` | same |
| Crypto | NIP-44 encrypt-to-self | `decryptFromSelf` |

Apply the same checklist to stretch, cardio, catalogs, and `erv/workouts/library`.

## Workout / import JSON

When changing workout or plan JSON:

- Schema: [`docs/import/workouts_import_schema.md`](../../docs/import/workouts_import_schema.md)
- Web types: `apps/web/web/src/lib/workoutTraining.ts`
- Android models: `app/.../workouts/` (grep `WorkoutSync`, segment/item data classes)
- Import version field: `ervWorkoutImportVersion`

## Change checklist

- [ ] Updated d-tag constant on **both** sides (or server allowlist if new tag)
- [ ] JSON envelope and entity fields match
- [ ] Merge/publish bumps `lastModifiedEpochSeconds` on write
- [ ] Built-in IDs unchanged or migrated on both sides
- [ ] Grep for hard-coded tag strings — prefer shared constants
- [ ] If schema doc exists, update it for AI/import consumers

## Do not

- Add publishable d-tags only on one platform (server will reject or Android will never fetch).
- Rename JSON fields without a migration story.
- Embed `nsec`, refresh tokens, or API secrets in export JSON or skill text.

## Scope reference

Phase boundaries and tag rollout: [`docs/PHASES.md`](../../docs/PHASES.md). Full composer grammar (large file): `docs/architecture/WORKOUT_PLAN_EDITOR_SPEC.md`.
