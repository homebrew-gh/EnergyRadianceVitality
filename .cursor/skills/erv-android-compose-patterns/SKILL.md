---
name: erv-android-compose-patterns
description: Implements ERV Android features using Jetpack Compose, Material 3, silo repositories, and navigation conventions. Use when editing app/, Compose screens, ViewModels, silo features, ErvNavHost, repositories, or live workout flows.
---

# ERV Android Compose patterns

Android app: `app/` — Kotlin, Jetpack Compose, Material 3, Navigation Compose.

For labels/theme see **erv-cross-platform-ui**. For sync/d-tags see **erv-nostr-sync-contract**.

## Theme

| File | Purpose |
|------|---------|
| `ui/theme/Color.kt` | ERV sun palette (keep aligned with web `index.css`) |
| `ui/theme/ErvTheme.kt` | `ErvTheme { }` wrapper |
| `ui/theme/Type.kt` | Nunito typography |

Wrap screens in `ErvTheme`. Use `MaterialTheme.colorScheme` and typography — avoid one-off colors unless matching a token.

## Shared UI components

`ui/components/`:

| Composable | Use |
|------------|-----|
| `FieldLabel` | `OutlinedTextField` captions |
| `FormSectionLabel` / `FormSectionLabelMedium` | Section headings above control groups |
| `SectionHeader` | Settings / form section titles |
| `CompactIntWheel` | Numeric wheel pickers |

Multi-word captions: always use helpers in `ErvLabel.kt` — not raw `Text("multi word …")`.

## Navigation

**`ui/navigation/ErvNavHost.kt`** — main graph; **`Routes`** object holds route strings.

**`ui/navigation/CategorySheet.kt`** — Launch Pad tiles; `implementedCategoryIds` marks shipped categories.

Pattern for category screens:

```kotlin
composable(Routes.category("cardio")) {
    CardioCategoryScreen(
        repository = cardioRepository,
        relayPool = relayPool,
        signer = signer,
        onBack = { navController.popBackStack() },
        onOpenLog = { navController.navigate(Routes.cardioLog) { launchSingleTop = true } },
    )
}
```

Nested navigation example: `SettingsScreen` uses inner `NavHost` for settings sections.

## Silo layout

Each health category is a silo with its own package + UI folder:

| Silo | Data / sync | UI |
|------|-------------|-----|
| Weight training | `weighttraining/`, `WeightSync.kt` | `ui/weighttraining/` |
| Cardio | `cardio/`, `CardioSync.kt` | `ui/cardio/` |
| Stretching | `stretching/`, `StretchingSync.kt` | `ui/stretching/` |
| Workouts (Phase 2) | `workouts/`, `WorkoutSync.kt` | `ui/workouts/` |
| Programs | `programs/`, `ProgramSync.kt` | `ui/programs/` |
| Supplements | `supplements/` | `ui/supplements/` |

Keep changes scoped to one silo when possible.

## Repository + sync pattern

Typical silo stack:

1. **Models** — `@Serializable` data classes in silo package
2. **Repository** — holds `StateFlow` library state, local persistence
3. **Sync object** — `*Sync.kt`: `fetchFromNetwork`, `publish*`, kind **30078**, NIP-44 encrypt-to-self
4. **Screen** — collects repository state, calls sync on user actions

Sync conventions (see `WeightSync.kt`):

```kotlin
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// Publish bumps lastModifiedEpochSeconds
// Merge prefers newer lastModifiedEpochSeconds by id
```

Screens receive `relayPool: RelayPool` and `signer: EventSigner` from nav host — pass through, do not construct in composables.

## ViewModels

Used for **live workout** and BLE-heavy flows, not every screen:

- `WeightLiveWorkoutViewModel`, `CardioLiveWorkoutViewModel`
- HR / cycling BLE: `HeartRateBleViewModel`, `Concept2Pm5BleViewModel`, etc.

Prefer repository `StateFlow` + `collectAsState()` for library/editor screens unless the flow needs AndroidViewModel lifecycle.

## Common Compose patterns in silos

- **`ModalBottomSheet`** + `rememberModalBottomSheetState` for pickers/editors
- **`FilterChip` / `AssistChip`** for tabs and toggles
- **`Card`** for grouped content
- **`LazyColumn`** for long lists; **`FlowRow`** for chip groups
- **`RoutineReminderFormSection`** — shared reminder UI (`ui/reminders/`)

## Nostr integration

Shared infra under `nostr/`:

- `RelayPool`, `EventSigner`, `KeyManager`, `RelayPublishOutbox`
- `fetchLatestKind30078ByDTag`, publish via outbox

Category screens trigger sync after local edits; respect user's relay URLs from settings.

## Do not

- Add new d-tags without updating web server allowlist (`erv_tags.rs`) — see **erv-nostr-sync-contract**
- Break `ignoreUnknownKeys` tolerance without migration plan
- Put network I/O directly in composables — use repository/sync suspend functions
- Duplicate Programs + Unified workout authoring paths for Phase 2 work — see **erv-phase-scope**

## Verify

```bash
./gradlew assembleDebug --no-daemon
./gradlew testDebugUnitTest --no-daemon
```

See **erv-build-verify** for full matrix.
