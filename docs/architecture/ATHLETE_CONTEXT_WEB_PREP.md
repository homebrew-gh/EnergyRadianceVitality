# Athlete context & web prep (pre-AI)

Build the **data layer and web UX** for individualized workout planning **before** Phase 4 AI
generation. Android stays the gym floor (live logging); the Start9 companion is the planning
desk (profile, history, analytics, authoring).

**Related:** [PHASES.md](../PHASES.md) · [START9_COMPANION_V1.md](START9_COMPANION_V1.md) ·
[PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md)

Last updated: June 2026.

---

## 1. Surface split

| Surface | Owns | Does not own |
|---------|------|--------------|
| **Start9 web** | Training profile, style presets, history review, training snapshot, workout/plan authoring, AI workout/plan generation | Live workout timer / set logging |
| **Android** | Live run, session logs, read-only profile summary, synced workout/plan execution | Full profile editor or AI generation (edit/generate on web) |

---

## 2. Hybrid context model

| Layer | Storage | Examples |
|-------|---------|----------|
| **Synced profile** | Nostr d-tag (user-edited) | Goals, experience, style presets, injuries/avoid list, HR zones, session duration |
| **Computed snapshot** | Derived from logs (optional future d-tag) | Working weights, weekly volume by muscle group, cardio load |
| **Optional HR load** | Derived from Android HR summaries in day logs | Zone time, avg/max/min HR, high-intensity exposure |
| **Ephemeral overrides** | Per-generation prompt only (Phase 4) | “Deload this week”, “45 min today only” |

Profile = who you want to be. Snapshot = what you’ve been doing. Generation needs both.

---

## 3. Web information architecture

| Nav tab | Purpose | Phase |
|---------|---------|-------|
| Workout Builder | Author `erv/workouts/library` | 2 (shipped) |
| Routines | Silo ingredients | 1 (shipped) |
| Equipment | `erv/equipment` | shipped |
| **Profile** | `erv/training-profile` | **W1** |
| **Progress** | History + training snapshot | **W2–W3** |
| Planner | Weekly assign (`erv/programs/master`) | W5 / Phase 3 |
| Catalog | `erv/catalog/*` | 1 (shipped) |
| Settings | Account / session only | — |

---

## 4. Nostr d-tags (this initiative)

| d-tag | Purpose | Publish (web) | Status |
|-------|---------|---------------|--------|
| `erv/training-profile` | Synced athlete profile + style presets | Yes | W1 |
| `erv/training-snapshot` | Computed baseline for AI (optional) | TBD | W3 |
| `erv/equipment` | Home gym + packs | Yes | shipped |
| `erv/weight/YYYY-MM-DD` | Weight day logs | No (Android) | W2 read on web |

Day-log tags remain **Android publish**; web **reads** them for Progress (W2).

---

## 5. Work packages (W1–W6)

Check boxes here as work lands. Mirror key items in [START9_COMPANION_V1.md](START9_COMPANION_V1.md).

### W1 — Athlete profile (synced)

- [x] JSON schema `TrainingProfileNostrPayload` (Android + web)
- [x] Nostr sync `erv/training-profile` (kind 30078)
- [x] Web **Profile** tab — full editor + publish
- [x] Server publish allowlist (`erv_tags.rs`)
- [x] Android — fetch/merge on relay sync; read-only Settings summary
- [x] Export bundle includes training profile (Settings → reference export)

**Acceptance:** Edit profile on web → publish → Android relay sync → Settings shows same summary.

### W2 — Training history on web (read-only)

- [x] Web fetches weight day logs (`erv/weight/YYYY-MM-DD`) from relay
- [x] Web fetches cardio day logs from relay
- [x] **Progress** tab — session timeline
- [x] Per-exercise history view (sets/reps/load)
- [x] Basic charts (volume, frequency, rep-bucket trends)

**Acceptance:** Log workout on Android → sync → visible on web Progress within one relay fetch.

### W3 — Training snapshot (computed baseline)

- [x] Snapshot builder (working weights, volume by muscle group, frequency map, cardio load)
- [x] **Progress** tab — “Your training baseline” panel
- [x] Staleness indicator (`computedAt`)
- [ ] Optional: publish `erv/training-snapshot` for offline web (deferred — local compute in v1)

**Acceptance:** Snapshot updates when logs change; user can inspect numbers before any AI use.

### W4 — Prescription-aware builder polish

- [x] Web exercise picker respects equipment (`isHomeReadyFor` equivalent)
- [x] Suggest loads from snapshot in composer ghost targets
- [x] Rule-based actions (“duplicate last week + increment”) without LLM

### W5 — Planner (Phase 3)

- [ ] Week grid on web; assign workouts to days
- [ ] `erv/programs/master` sync
- [ ] Android week view + run (Phase 3 acceptance test)

### W6 — Context export (AI dry run)

- [x] “Copy training context” bundle (profile + snapshot + equipment + catalog ids)
- [x] Extends today’s programs reference bundle pattern
- [x] Validates completeness before Phase 4 `AiContextBuilder`
- [x] Includes progression guardrails with optional heart-rate evidence when Android logs provide it

---

## 6. Training profile schema (W1)

Wire JSON (encrypted kind 30078, d-tag `erv/training-profile`):

| Field | Type | Notes |
|-------|------|-------|
| `profileVersion` | int | Current profile shape is `2`; v1 payloads remain readable |
| `primaryGoal` | enum | `general_fitness`, `strength`, `hypertrophy`, `endurance`, `longevity`, `sport` |
| `experienceLevel` | enum | `beginner`, `intermediate`, `advanced` |
| `typicalSessionMinutes` | int? | e.g. 45, 60 |
| `typicalTrainingDaysPerWeek` | int? | 1–7 |
| `stylePresetIds` | string[] | Curated ids, max 2 — see §7 |
| `styleNotes` | string? | Free-text nuance |
| `avoidMovementPatterns` | string[] | Structured tags — see §8 |
| `customAvoidNotes` | string? | Injury / limitation notes |
| `progressionStyle` | enum? | `conservative`, `moderate`, `aggressive` |
| `cardioBias` | enum? | `none`, `zone2_base`, `intervals`, `mixed` |
| `ageYears` | int? | Programming context |
| `heartRateMaxBpm` | int? | Optional; cardio prescriptions |
| `heartRateRestingBpm` | int? | Optional |
| `heartRateZoneMethod` | string? | `percent_max_hr` \| `karvonen_hrr` |
| `lastModifiedEpochSeconds` | long | Merge: newer wins |

---

## 7. Style preset ids (curated)

Presets map to fixed bullet lists in future `AiContextBuilder` — not raw influencer names alone.
Users may select up to **2** presets. The workout split is derived later from selected presets,
goal, training days, session length, equipment, recovery, and training history — it is not a
profile field.

| id | Label | Influence examples | Intent |
|----|-------|--------------------|--------|
| `longevity_recovery` | Longevity & Recovery | Bryan Johnson, Rhonda Patrick, Peter Attia, Andrew Huberman | Conservative progression, recovery, mobility, zone 2, full-body balance |
| `joint_durability` | Joint Durability / ATG | Ben Patrick, Knees Over Toes, ATG, Kelly Starrett | Knee/ankle capacity, controlled ROM, tendon resilience, progressive range |
| `hypertrophy_bodybuilding` | Hypertrophy / Bodybuilding | Renaissance Periodization, Mike Israetel, Jeff Nippard, Menno Henselmans | Volume, 8–15 rep ranges, accessories, proximity to failure |
| `strength_powerlifting` | Strength / Powerlifting | Starting Strength, Jim Wendler, Westside | Squat/bench/deadlift focus, heavier loading, planned progression |
| `zone2_endurance` | Zone 2 / Aerobic Base | Phil Maffetone, Inigo San Millan, Peter Attia | Aerobic base, low-intensity cardio, strength maintenance |
| `hiit_conditioning` | HIIT / Conditioning | Tabata, Norwegian 4x4, CrossFit-style conditioning | Intervals, circuits, work capacity, careful fatigue control |
| `general_athletic` | General Athletic Performance | Field-sport S&C, EXOS-style training | Strength, mobility, cardio, power, coordination |
| `mobility_movement` | Mobility & Movement Quality | FRC, GMB-style movement, yoga, pilates | Range of motion, control, prehab, lower intensity |
| `calisthenics_minimalist` | Calisthenics / Minimal Equipment | Bodyweight strength, Pavel-style simple strength, rings/bar basics | Bodyweight progressions, low equipment, skill strength |

Influence examples are UI copy only. **`stylePresetIds` drive deterministic context**.

---

## 8. Avoid-movement pattern tags

| id | Meaning |
|----|---------|
| `heavy_overhead_press` | Limit maximal overhead loading |
| `deep_knee_flexion` | Limit deep squat/lunge ROM under load |
| `spinal_axial_load` | Limit heavy spinal compression (squat/deadlift emphasis) |
| `jumping_plyometrics` | Limit jump/plyo volume |
| `hanging_from_bar` | Limit hanging / pull-up stress |
| `high_impact_cardio` | Limit running/jump rope intensity |

---

## 9. Android scope (stay thin)

- Sync `erv/training-profile` → local prefs on relay fetch (newer `lastModifiedEpochSeconds` wins)
- Settings → read-only summary + “Edit on web companion”
- **No** full profile editor on device (unless offline-only users demand later)
- Continue publishing weight/cardio logs (feeds W2/W3 on web)

---

## 10. Phase 4 AI hook (later)

When AI ships ([PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md](PROGRAMS_AND_WORKOUTS_MERGE_AND_AI.md) §6):

1. Web-only `AiContextBuilder` reads: profile + snapshot + equipment + catalogs + saved workout ids
2. Ephemeral user prompt appended at generate time
3. Output → existing workout import envelope → validate ids → preview in builder
4. Never auto-publish to Nostr

AI workout/plan generation is a Start9 web companion feature only. Android does not host prompts,
providers, generation queues, or draft validators; it only syncs saved workouts/plans and runs live
sessions after the user saves generated output on web.

Progression guardrails should treat heart-rate data as optional evidence:

- Missing HR data must not block generation.
- Avg/max/min HR summaries may add soft fatigue or cardio-load warnings.
- `heartRate.load.zoneSeconds` is preferred when available because it supports weekly Z1–Z5 comparisons.
- Whole-workout and segment HR are useful for circuits, supersets, intervals, and conditioning blocks.
- Per-exercise HR windows remain local-only / low confidence because HR lag makes attribution noisy.

**Do not** add LLM UI until W1–W3 (minimum) are usable without AI.

---

## 11. Code map (W1)

| Area | Path |
|------|------|
| Android model | `app/.../data/TrainingProfile.kt` |
| Android sync | `app/.../nostr/TrainingProfileSync.kt` |
| Web types | `apps/web/web/src/lib/trainingProfile.ts` |
| Web provider | `apps/web/web/src/lib/trainingProfileData.tsx` |
| Web UI | `apps/web/web/src/routes/ProfileTab.tsx` |
| Server allowlist | `apps/web/server/src/erv_tags.rs` |

### W2 code map

| Area | Path |
|------|------|
| Log parsers + analytics | `apps/web/web/src/lib/trainingHistory.ts` |
| History provider | `apps/web/web/src/lib/trainingHistoryData.tsx` |
| Web UI | `apps/web/web/src/routes/ProgressTab.tsx` |

### W3 code map

| Area | Path |
|------|------|
| Snapshot builder | `apps/web/web/src/lib/trainingSnapshot.ts` |
| Baseline panel UI | `apps/web/web/src/components/TrainingBaselinePanel.tsx` |

---

## 12. Risks

| Risk | Mitigation |
|------|------------|
| Web has no logs until W2 | W1 still valuable; snapshot blocked until W2 |
| Profile/log merge conflicts | Profile: lastModified wins; logs: existing day-log merge rules |
| Influencer names hallucinate protocols | Presets + validate → preview; influence examples are UI copy only |
| Settings clutter on Android | Read-only summary only |
