---
name: erv-web-companion-patterns
description: Implements ERV Start9 web companion features using established React routing, auth, API, and builder layout patterns. Use when editing apps/web/web, companion SPA, SetupRoute, relay publish, TrainingProvider, routine tabs, or catalog editor.
---

# ERV web companion patterns

Start9 companion SPA: `apps/web/web/` (React 18, Vite, Tailwind, React Router 6). Backend: `apps/web/server/` (Rust, session cookie, Nostr publish).

For theme/labels see **erv-cross-platform-ui**. For d-tags/JSON see **erv-nostr-sync-contract**.

## Routing (`App.tsx`)

| Route | Screen |
|-------|--------|
| `/setup` | First-run: nsec + passphrase + relay |
| `/unlock` | Unlock sealed key |
| `/app/workouts` | Workout builder (default landing) |
| `/app/routines/weight\|stretch\|cardio` | Silo routine editors |
| `/app/catalog` | Catalog editor |
| `/app/equipment` | Home gym profile |
| `/app/profile` | Training profile (pre-AI) |
| `/app/progress` | Training history + charts (read-only) |
| `/app/settings` | Relay, lock, wipe |

`Gate` redirects: no state → setup; locked → unlock; no relay → setup; else → `/app`.

`AppShell` wraps authenticated routes with `TrainingProvider` + `CatalogEditorProvider`.

## Auth and API

- **`lib/auth.tsx`** — `AuthProvider`, `useAuth()`, lock/wipe/refresh; registers 401 handler
- **`lib/api.ts`** — typed `fetch` to `/api/*`, `credentials: "same-origin"`, `ApiError`
- Setup posts `{ nsec, passphrase, relay_url? }`; server seals key in `state.json`
- **Same nsec + relay as Android** required for encrypt-to-self sync

Local dev:

```bash
cd apps/web/server && ERV_COOKIE_SECURE=0 cargo run   # port 3000
cd apps/web/web && npm run dev                         # port 5173, proxies /api
```

## Data layer (`lib/trainingData.tsx`)

Central provider for relay-backed library state:

- Fetches kind-30078 app data via API
- Parses payloads from `weightTraining.ts`, `stretchTraining.ts`, `cardioTraining.ts`, `workoutTraining.ts`
- Publishes via `api.publishAppData(dTag, plaintext)` → server outbox → relay
- Dispatches `ROUTINES_PUBLISHED_EVENT` / listens for `CATALOG_PUBLISHED_EVENT` after publish

**Pattern for new tabs:** extend types + parse/payload helpers in the relevant `lib/*Training.ts`, then wire fetch/publish in `TrainingProvider` — do not duplicate fetch logic in route components.

## Builder layout

Use **`RoutineBuilderLayout`** for editor pages with a catalog sidebar:

```tsx
<RoutineBuilderLayout
  sidebarKinds={["weight-exercise", "stretch", "cardio"]}
  weightCatalog={...}
  stretchCatalog={...}
  cardioCatalog={...}
  onPick={...}
>
  {/* main editor */}
</RoutineBuilderLayout>
```

Supporting components:

| Component | Use |
|-----------|-----|
| `LibrarySidebar` | Pick exercises/stretches/cardio from catalog |
| `SavedRoutinesPanel` | List + select saved routines |
| `ReorderableList` | Drag/reorder ordered items |
| `RoutineFormAlerts` | Validation / publish feedback |
| `SecretInput` | Passphrase / sensitive fields |
| `AuthCard` | Setup/unlock card chrome |
| `RelayStatus` | Connection / outbox status banner |
| `DetectedRelayNotice` | Suggested relay from server |

## Route files

| Tab | File |
|-----|------|
| Workouts | `routes/WorkoutsTab.tsx` |
| Weight routines | `routes/WeightRoutinesTab.tsx` |
| Stretch routines | `routes/StretchRoutinesTab.tsx` |
| Cardio routines | `routes/CardioRoutinesTab.tsx` |
| Catalog | `routes/CatalogEditorTab.tsx` |
| Profile | `routes/ProfileTab.tsx` |
| Progress | `routes/ProgressTab.tsx` |
| Settings | `routes/SettingsTab.tsx` |
| Routines shell | `routes/RoutinesLayout.tsx` |

## Styling

- CSS variables + components in `src/index.css` (`.card`, `.btn-primary`, `.btn-ghost`, `.input`, `.label`)
- Use `FieldLabel` / `SectionHeader` from `components/FieldLabel.tsx` for form captions
- Nav active state: `navLinkClass` pattern in `AppShell.tsx`

## Publish flow checklist

- [ ] Payload built with shared `*Payload()` helper matching Android envelope
- [ ] `lastModifiedEpochSeconds` bumped on write
- [ ] Publish uses correct `*_D_TAG` constant from lib module
- [ ] UI refreshes via provider or custom event after publish
- [ ] Outbox errors surfaced (Settings / `RelayStatus`)

## Do not

- Call relay WebSockets directly from the SPA — go through `/api`
- Store nsec in localStorage — server holds sealed key
- Add one-off pages outside `AppShell` without updating `Gate` redirects
- Hard-code d-tag strings — import from `lib/*Training.ts` or `catalog.ts`
