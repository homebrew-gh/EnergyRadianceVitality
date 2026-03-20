# Weight training — implementation spec

**Full design**: `.cursor/plans/weight_training_silo_ea7a0146.plan.md`

## Implementation progress

Use this section so work is **not repeated** across sessions. Update it when you complete a checkpoint.

| Stage | Status | What landed (high level) |
|-------|--------|---------------------------|
| **A** | Done | `WeightModels.kt`, `WeightRepository.kt` (DataStore), seeded compounds when store empty, `WeightModelsSerializationTest` |
| **B** | Done | `WeightSync.kt` (kind **30078**, d-tags `erv/weight/exercises`, `erv/weight/routines`, `erv/weight/<date>`), `MainAppShell` parallel fetch + `replaceAll` |
| **C** | Done | Activity-scoped `DashboardViewModel` in `MainActivity` → `ErvNavHost` → `DashboardScreen`; route `category/weight_training` + `WeightTrainingCategoryScreen` (placeholder: shows **dashboard date** + library counts); **Weight Training** removed from generic Coming Soon |
| **D** | Done | `WeightTrainingCategoryScreen`: **Exercises** / **Routines** tabs, theme-aware red header (matches cardio therapy reds), muscle-group **sticky** sections, add/edit/delete exercises, build/edit/delete routines (ordered picks, reorder), **FAB** per tab; CRUD publishes `erv/weight/exercises` + `erv/weight/routines` via `WeightSync` |
| **E** | Done | Activity-scoped `WeightLiveWorkoutViewModel`; full-screen **Live workout** (elapsed timer, add/reorder/remove exercises, per-exercise set editor); **Start workout** (blank) + **Play** on routines; **Finish** → `addWorkout` for **`LocalDate.now()`** only if ≥1 logged set; **Cancel** with confirm when draft non-empty; `WeightSync.publishDayLog`; blocks second start with snackbar |
| **F** | Done | Post-finish **`WeightWorkoutSummaryFullScreen`**: log **date** (`YYYY-MM-DD`, live = today), elapsed, sets/volume, exercise lines; **Share** → Nostr **kind 1** (`t`: erv, workout, fitness) with date line in body; if `session.routineId` matches a routine, **Update routine** (confirm) → `exerciseIds` from session order with consecutive dedupe + `pushMasters` |
| **G** | Done | **Log** tab on `WeightTrainingCategoryScreen`: list workouts for **`selectedDate`** (dashboard date); **Add workout** (full-screen manual editor, `MANUAL` source) + **edit** (keeps `LIVE` timestamps/source when editing live sessions) + **delete** + **Share** (kind 1, **`selectedDate`** in note); `buildSessionFromLogEditor`; shared **`WeightExerciseInlineSetsCard`** / pick dialog with live screen |
| **H** | Done | **`DashboardScreen`**: **Weight Training** routine tile (with Cardio on second row); bottom sheet **New workout** (`tryStartBlank` + nav) or pick **weight routine** (`tryStartFromRoutine` + nav); **Activity** card lists **Weight training** lines for **`selectedDate`** via `weightActivityRowsFor` + `dashboardSummaryLine` (respects kg/lb pref) |
| **I–K** | Not started | … |

**How to verify stage A–C:** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Dashboard date on weight screen; **4** default exercises.

**How to verify stage D:** Open Weight Training → **Exercises**: add a custom lift, edit a compound, confirm grouped list + sticky header. **Routines**: create a routine with two exercises, reorder with ↑↓, save; pull-to-refresh data on second device optional (relay). Delete flows ask for confirmation.

**How to verify stage E:** Start blank workout → add exercise → log sets (reps required) → Finish → today’s log contains session (check relay or re-open app). Start from routine → list prefilled. Try second start → snackbar. Cancel with exercises → confirm discard.

**How to verify stage F:** Finish a live workout → full-screen **Workout logged** summary (date, timer, sets list). **Share workout** publishes kind 1 (relay connected). Start from a routine, reorder or change exercises, finish → **Update “…” to match** → confirm → Routines tab shows new order; masters sync if relays on.

**How to verify stage G:** Open Weight → **Log** tab; list matches **dashboard date** (not necessarily today). **Add workout** → full-screen editor → save → appears in list; **Edit** changes entries; **Delete** confirms; **Share** uses that date in the note body. With a live workout open, **Add workout** FAB is hidden.

**How to verify stage H:** Dashboard **Routines** shows **Weight Training** next to Cardio; sheet offers **New workout** and saved routines. Completing either navigates to Weight Training with live session started when allowed. **Activity** shows weight sessions for the **selected dashboard date** (switch date to confirm). With a live workout already running, choosing new/routine shows snackbar and does not start a second session.

**Resume next:** Stage **I** (exercise detail / progress from logs).

## Locked product rules

- **Multiple workouts / day**: `workouts[]` on `erv/weight/<date>` holds each session; each **LIVE** session has **start/end** timestamps on the day’s timeline.
- **One live session at a time**: No second **Start Workout** / **Start from routine** while a live draft is active—finish or cancel first. **Manual Add workout** is **disabled** while a live session is open (same gate).
- **Empty workout**: **Never** call `addWorkout` / persist if there is nothing logged (no exercises with sets); live finish requires at least one logged exercise or stays draft / cancel.
- **Manual share**: **Allowed**; kind **1** body includes **log date** (`YYYY-MM-DD` for `selectedDate`).
- **selectedDate**: Must match **dashboard calendar** when opening Weight from the **category sheet** — hoist `DashboardViewModel` to nav/activity scope (or pass date on every category navigation).
- **Background / FGS disclosure**: Tell users **before or at first live start** that **live workouts** use a **foreground service** and Android may show a **persistent ongoing notification** so the timer keeps running—not optional to hide without ending the workout. Plain-language copy in Settings + one-time dialog/snackbar acceptable.
- **Live workout bubble setting**: **`UserPreferences.workoutBubbleEnabled`**, **default ON**. **OFF** = no bubble only; **ongoing notification remains** during live workouts (FGS).

## Settings UI copy (`SettingsScreen`) — live workout

Use a small **grouped section** so the distinction between **bubble** vs **required notification** is obvious.

**Section title (e.g.):** “Live weight workout”

**Explanatory text (always show above the switch):**  
“During an **active live workout**, ERV runs in the background so your **session timer stays accurate**. **Android requires a persistent, ongoing notification** for this—it is **not** optional inside the app while the workout is running, and it is **not** used for ads or tracking.”

**Switch label:** “**Show workout bubble**” (or “Floating bubble for live workouts”)  
**Switch helper/subtitle (critical):**  
“**On** (default): on supported Android versions, a **bubble** can appear when you leave the workout screen, so you can jump back quickly. **Off**: **only the bubble is disabled**. You will **still see the ongoing workout notification** until you **finish or cancel** the workout—that is required by Android for background timers. **To minimize or change sounds**, use **Settings → Apps → ERV → Notifications** on your device.”

**If `POST_NOTIFICATIONS` denied:** Short line: “Notifications are off—live workout timer and return-to-workout actions may not work. Enable in system Settings.”

**API &lt; 30:** Hide the switch **or** show disabled text: “Floating bubbles are not available on this version of Android. An ongoing notification still appears during live workouts.”

Implement strings in `strings.xml` for localization.

## Multi-device sync (“merge rules”) — v1

ERV currently does **last event wins per `d` tag** after fetch (`replaceAll`), like cardio. **No field-level merge**: if phone A and phone B both edit `erv/weight/2026-03-20`, the **newer** published event replaces the older when pulled. **Smarter merge rules** (e.g. union `workouts[]` by id) are future work if multi-device same-day editing matters.

## Stages

| # | Scope | Done when |
|---|--------|-----------|
| A | `Weight*` models, DataStore `WeightRepository`, seed 4 compounds | Unit tests serialize/deserialize state |
| B | `WeightSync`, publish/fetch 30078, `MainAppShell` LaunchedEffect | Pull restores masters + day logs |
| C | Nav routes, remove Coming Soon, hoist `selectedDate` / `DashboardViewModel` | Weight sees dashboard date from sheet |
| D | Exercises + Routines tabs (CRUD), theme | Add exercise, build routine |
| E | Live workout: timer, list UI, blank + routine start, `addWorkout` only if non-empty | Finish saves to today |
| F | Summary + kind-1 share (+ date line) + **Update routine** | End-to-end live |
| G | Log tab: by `selectedDate`, manual add, edit/delete, share with date | Backfill |
| H | Dashboard Activity + weight routine tile | Routines row + Activity lines for selected date |
| I | Exercise detail / progress from logs | List history per lift |
| J | `PLAN_OF_ACTION.md` + `PROTOCOL_GRAPH.md` | Docs match shipped JSON (see **Documentation timing** below) |
| K | FGS + notification + Bubble ([PLAN §13](PLAN_OF_ACTION.md)); Settings switch (default on); user disclosure | After E–F |

### Documentation timing (stage J)

Avoid implementing from stale [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md) / [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md) while stages B–I are in progress:

- **Early checkpoint (after stage A or A+B):** Update PLAN §2.3 / §2.4 (and PROTOCOL nodes/edges as needed) with **locked d-tags** (`erv/weight/exercises`, `erv/weight/routines`, `erv/weight/<date>`) and the **`workouts[]` / session JSON shape** as defined in code—**prose and field lists** first; diagrams can stay rough until the feature set stabilizes.
- **Final J (after I, before or with release):** Reconcile **diagrams**, cross-links, and §7.5 / §13 mentions so published docs match **shipped** encrypted payloads and UI behavior.

**Optional parallel work:** `UserPreferences.workoutBubbleEnabled` and Settings copy can be added anytime after A; stage **K** must wire the flag into the notification builder and FGS.

**Todos vs letters:** Map work items to stages explicitly (e.g. “nav + hoist date” = **C**) so merges don’t duplicate or invert **C** before **D**.

## Stage K — Bubble + foreground service (detail)

- **`ActiveSessionService`** holds live weight session + timer; **ongoing notification** (FGS).
- **`UserPreferences.workoutBubbleEnabled`** (default **`true`**): if **true** and API 30+, add **`BubbleMetadata`** when promoting bubble; if **false**, **omit** bubble — user still sees notification + actions.
- **Educate**: Use **Settings UI copy** section above + **first live workout** prompt; **POST_NOTIFICATIONS** hint when denied.
- **When bubble eligible** (setting on, API 30+): live active and user **left** live workout route **or** app backgrounded; suppress while on live screen foreground.
- **API &lt; 30**: notification only; hide bubble toggle or show “Not available”.
- Bubble `Activity` + main UI share service `StateFlow` ([§13](PLAN_OF_ACTION.md)).
- **Update [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md) §7.5** with the new setting row when implementing.

## Deferred (do not block MVP)

- Charts / PR badges; bar/plate inventory; per-exercise rest persistence (beyond §13 timer). Goals wiring. **Note:** stage **K** is required for trustworthy background timer + bubble UX; can ship MVP E/F with in-process timer first, then K.
