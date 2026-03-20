# Weight training — implementation spec

**Full design**: `.cursor/plans/weight_training_silo_ea7a0146.plan.md`

## Implementation progress

Use this section so work is **not repeated** across sessions. Update it when you complete a checkpoint.

| Stage | Status | What landed (high level) |
|-------|--------|---------------------------|
| **A** | Done | `WeightModels.kt`, `WeightRepository.kt` (DataStore), seeded compounds when store empty, `WeightModelsSerializationTest` |
| **B** | Done | `WeightSync.kt` (kind **30078**, d-tags `erv/weight/exercises`, `erv/weight/routines`, `erv/weight/<date>`), `MainAppShell` parallel fetch + `replaceAll` |
| **C** | Done | Activity-scoped `DashboardViewModel` in `MainActivity` → `ErvNavHost` → `DashboardScreen`; route `category/weight_training` + `WeightTrainingCategoryScreen` (placeholder: shows **dashboard date** + library counts); **Weight Training** removed from generic Coming Soon |
| **D** | Not started | … |
| **E–K** | Not started | … |

**How to verify this checkpoint:** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. In the app: change the dashboard date, open **Categories → Weight Training**, confirm the screen shows the **same date** and **Exercises loaded: 4** (defaults) on first install.

**Resume next:** Stage **D** (Exercises + Routines tabs, CRUD, theme).

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
| H | Dashboard Activity + weight routine tile | Tiles match plan |
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
