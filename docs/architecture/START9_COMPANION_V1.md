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
- [ ] StartOS: same flow on LAN relay (optional Nostr RS Relay package)

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

### Phase 2 — Workout composer (`erv/workouts/library`)

- [ ] Workout schema + segment kinds (circuits, HIIT intervals, prescriptions)
- [ ] Android Workout Composer (storyboard, live run)
- [ ] Web composer + publish `erv/workouts/library`
- [ ] Segment templates (circuit shell, HIIT block, etc.) as drag payloads

**Not in Phase 2:** weekly planner, Programs tile merge, dashboard today card.

### Phase 3 — Weekly planner (`erv/programs/master`)

- [ ] Week grid: drag exercises, routines, templates, and saved workouts onto days
- [ ] Android: merge Programs + Unified Workouts → **Planner** tile
- [ ] Plan strategy, habits, rest notes (3b)

### Phase 4 — Dashboard + AI

- [ ] Dashboard: surface planned workout for today (read-only)
- [ ] AI plan/workout generation (Maple / optional)

### Other

- [ ] Read-only analytics (day logs, route images via Blossom)

## Test plan (routine publish)

1. Install **Nostr RS Relay** on StartOS (or use external `wss://`).
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
```

## Key files

| Area | Path |
|------|------|
| Server routes | `apps/web/server/src/routes.rs` |
| Nostr crypto | `apps/web/server/src/nostr_support.rs` |
| d-tag policy | `apps/web/server/src/erv_tags.rs` |
| Routine UI | `apps/web/web/src/routes/RoutinesTab.tsx` |
| JSON contract | `apps/web/web/src/lib/weightTraining.ts` |
| Android merge | `app/.../LibraryStateMerge.mergeWeight` |
