# My Day — unified health schedule and Today view

**Status:** Draft for review (September 2026). Android first; web companion later.
**Checklist owner:** this file (§10). `PHASES.md` holds the one-line status only.

---

## 1. Problem

ERV tracks many parts of a health routine, but only training can be planned by day:

| Category | How it is planned today | Gap |
|----------|------------------------|-----|
| Training | Weekly plan in **Training → Weekly Planner** (`erv/programs/master`) | None — keep as is |
| Supplements | Routine has a time of day; optional per-routine reminder | No day plan; reminder lives inside the silo |
| Light therapy | Routine has time of day + repeat days; optional reminder | Same |
| Sauna / cold plunge | Only as a Heat/Cold block inside a training plan | Mixed into training |
| Stretching | Only as a block inside a training plan | Mixed into training |
| Fasting | Timer and intermittent protocol, no schedule | No plan at all |
| Body tracking | None | No check-in cadence |
| Habits | Free-text checklist block inside a training plan | Mixed into training; manual only |
| Goals | Weekly totals (`UserGoalDefinition`) | No breakdown by day |

There is no single place that answers **"what am I doing today?"** across the whole app.

## 2. Decision

Add a new section, **My Day**, that schedules every category together and shows the result as a
**Today** view. The **Training** tab is unchanged: it remains where workouts and training weeks
are designed. My Day *references* the active training plan instead of copying it.

| Surface | Owns |
|---------|------|
| Training (existing) | Workouts, weekly training plans, plan strategy |
| **My Day** (new) | When non-training routines and habits happen, reminders, the daily checklist, logging any activity (planned or not) |
| Category sections (existing) | Libraries and history: supplement catalog, light devices, stretch catalog, routines, timers, past sessions |

### 2.1 Role of the category sections

Once My Day exists, it is where the user **plans and does** things. The category sections
(Supplements, Light Therapy, Hot + Cold, Stretching, Fasting, Body Tracker) stop being daily
destinations and become **libraries and history**:

- They still own what My Day cannot: supplement catalog and barcode lookup, light device
  catalog, stretch catalog, routine editors, the timer screens themselves, and per-category
  history and stats.
- My Day rows open those timers and quick logs directly, so the user rarely has to navigate
  into a section to do something.
- **Light therapy, sauna, and cold plunge** have little beyond a timer and a log, so they are the
  first candidates to lose their standalone entry points.

Staged consolidation (M7, after My Day is in daily use):

1. **Launch Pad is rethought** (see §9). Its role shrinks once My Day and the Training planner
   cover "what do I do today"; whether it is reduced, repurposed, or removed is decided before
   M7 starts.
2. A single **History** screen with category filters replaces the standalone log screens.
3. Every section stays reachable from the **category menu** regardless of Launch Pad, as it is
   today, and from My Day row details.
4. The Training block editor stops offering **Heat/Cold** and **Habits** blocks; existing ones
   are offered a one-time move into My Day (§7.2).

All session data stays in its category's existing log and d-tags. My Day never duplicates it,
so history, stats, sync, and the web companion keep working unchanged.

## 3. Goals and non-goals

**Goals (v1)**

- Schedule any category on chosen weekdays, in a part of the day, with an optional exact time.
- Change a single day of a repeating item (time, target, skip, or move to another day).
- Today view with one-tap actions and automatic completion from existing logs.
- Log any activity for any date, planned or not, including after the fact.
- Weekly goals are fulfilled by what is logged, whether it was planned or not.
- One reminder system for the whole app (existing routine reminders migrate into My Day).
- Sync the schedule across devices with the same encryption as other ERV data.

**Non-goals (v1)**

- Editing on the web companion (read-only or none until M8).
- Streaks, scores, or gamification beyond a daily progress ring.
- Multi-week rotations for non-training items (training rotations stay in Training).
- AI-generated schedules (Phase 4 may suggest items later; never auto-publish).
- Calendar app integration.

## 4. UX

### 4.1 Entry points

- **Home screen:** a compact **Today** card at the top of Launch Pad (progress ring, next two
  items, "Open My Day"). Tapping opens My Day on the Today tab.
- **Launch Pad tile** and **category menu** entry: "My Day".
- **Reminder notifications** deep-link to the Today tab with the item highlighted.
- Later (bottom navigation work): My Day becomes the **Today** tab.

### 4.2 Today tab

```text
My Day                                       [Today] [Week]
Sunday, Sep 27                               ◔ 3 of 6 done

Morning
  ✓  Morning stack · Supplements           logged 7:42
  ○  Red light · 10 min              [Start]
Afternoon
  ○  Today's training · Push A       [Start]      ← from Training plan
Evening
  ○  Sauna · 15 min                  [Start]
  ○  Evening stack · Supplements     [Log]
Anytime
  ○  10 min sunlight                 [ ]           ← habit, manual check
  ○  16:8 fast · eating window 12–8  In progress

Also logged
  ✓  Cold plunge · 3 min             logged 6:10   ← not planned, still counts

                         [ + Log activity ]

This week   Cardio 2/3 · Strength 3/4 · Sauna 2/3 · Light 40/60 min   ← weekly goals
```

- Sections: **Morning, Afternoon, Evening, Anytime**. Items with an exact time sort by time
  within their section and show it.
- Each row: category accent icon, title, detail, and **one** action:
  - **Start** — timer or live session (light, sauna/cold, stretch, fasting, training)
  - **Log** — opens the silo's quick log (supplements, body tracker)
  - **Checkbox** — habits
- Done rows show a check and the logged time; they move below open rows in their section.
- Long-press or swipe a row: **Skip today** (counts as resolved, shown struck through),
  **Change today only** (§4.6), or **Undo**.
- **Also logged** lists activities logged that day that did not match a planned item
  (§6), including cardio and strength sessions logged outside the Training plan.
- **+ Log activity** records something that was not planned (§4.5). **Plan for today** (in the
  same menu) adds a one-off item for this date without logging it yet.
- Past and future dates reachable with the existing date navigator; past days show what was
  done vs planned. The day runs from midnight to midnight, local time.
- My Day's Today tab replaces the dashboard **Activity** tab as the place to see a day's log.
- Empty state: "Plan your day — add supplements, light, sauna, fasting, or habits," with one
  **Add item** button. If a Training plan is active, "Today's training" appears automatically.

### 4.3 Week tab

- Monday to Sunday list (vertical on phones), each day showing its items as small chips in the
  category accent color, grouped by part of day.
- Tap a day to see its items; tap an item to edit; **+** adds an item pre-set to that day.
- Items that repeat show on every applicable day. Editing asks **This day only** or **Every
  day**; "This day only" stores a per-date override (§4.6).

### 4.4 Add / edit item sheet

1. **What:** category grid (Supplements, Light, Sauna, Cold plunge, Stretching, Fasting,
   Body check-in, Habit). Training is not listed — it comes from the Training plan.
2. **Which:** routine picker for the category (or "Any session" where it makes sense), or a
   title for a habit.
3. **When:** weekday chips (default every day) and part of day. Part of day defaults from the
   routine's own `timeOfDay` when it has one. Optional exact time.
4. **Target:** minutes (light, sauna, cold, stretch) or hours (fasting), prefilled from the
   routine.
5. **Reminder:** off or on; on requires an exact time.
6. **Notes** (optional).

Labels follow the title-case rule (`FieldLabel` / `FormSectionLabel*`).

### 4.5 Log an activity (planned or not)

Available from **+ Log activity** on Today (for the date being viewed), from any planned row,
and from the home screen Today card.

1. **What:** the same category grid as §4.4, plus **Cardio**, **Strength**, and **Saved
   workout** (these are planned in Training but can always be logged here).
2. **How:**
   - **Start now:** opens the category's timer or live session. The session is logged when it
     finishes, exactly as today.
   - **Already did it:** a short form: start time (defaults to now; any time on the viewed
     date), duration or amount, routine (optional), and category-specific fields
     (temperature for sauna/cold, device for light, protocol for fasting). Cardio and strength
     reuse the existing backfill screens (`onOpenCardioLogBackfill`,
     `onOpenWeightLogBackfill`).
3. **Where it goes:** the entry is written to the **category's own log** for that date (for
   example `HeatColdRepository.logSaunaSession(date, …)`), never to My Day. It therefore shows
   in that category's history, syncs with that category's d-tag, and counts toward goals.
4. **How it shows on Today:** if it matches an open planned item (same kind and routine, or
   "any session" items), it completes that item. Otherwise it appears under **Also logged**.
5. **Habits** have no category log, so "Log a habit" completes a planned habit, or creates a
   one-off habit item for that date with a `DONE` mark.

Repository changes needed: category `log*` functions accept an optional `loggedAtEpochSeconds`
(today they stamp "now"), and fasting gains `logPastFast(start, end, kind, protocol)`.

### 4.6 Change one day of a repeating item

From a row's menu (Today) or the edit prompt (Week), **This day only** allows:

- a different part of day or exact time (and reminder time),
- a different target,
- **Move to another day** (the item disappears from the original date and appears on the new
  one, marked "moved from Tue"),
- **Skip** (same as §4.2).

Overrides never change the repeating item, and they are cleared automatically once the date is
more than 90 days in the past.

## 5. Data model

New file `app/.../myday/MyDayModels.kt`. All fields additive-friendly; parsed with
`ignoreUnknownKeys = true`.

```kotlin
@Serializable
enum class MyDayPart { MORNING, AFTERNOON, EVENING, ANYTIME }

@Serializable
enum class MyDayItemKind {
    SUPPLEMENT_ROUTINE,   // ref = supplement routine id; null ref = any supplement intake
    LIGHT_ROUTINE,        // ref = light routine id; null = any light session
    SAUNA,
    COLD_PLUNGE,
    STRETCH_ROUTINE,      // ref = stretch routine id; null = any stretch session
    FASTING,              // targetHours; intermittent protocol or extended fast
    BODY_CHECK_IN,
    HABIT,                // title only, manual completion
}

@Serializable
data class MyDayItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: MyDayItemKind,
    val refId: String? = null,
    val title: String? = null,              // habit title, or override for display
    val daysOfWeek: List<Int> = emptyList(), // ISO 1..7; empty = every day
    val onlyOnDate: String? = null,          // YYYY-MM-DD one-off; ignores daysOfWeek
    val part: MyDayPart = MyDayPart.ANYTIME,
    val timeMinutes: Int? = null,            // minutes after midnight, local time
    val targetMinutes: Int? = null,
    val targetHours: Int? = null,
    val reminderEnabled: Boolean = false,
    val notes: String? = null,
    val archived: Boolean = false,           // soft delete so sync can propagate removal
    val createdAtEpochSeconds: Long,
    val lastModifiedEpochSeconds: Long,
)

@Serializable
enum class MyDayMarkState { DONE, SKIPPED, CLEARED }

@Serializable
data class MyDayMark(
    val state: MyDayMarkState,
    val updatedAtEpochSeconds: Long,
)

/** "This day only" change to one occurrence of a repeating item. Null fields keep the item's value. */
@Serializable
data class MyDayOverride(
    val part: MyDayPart? = null,
    val timeMinutes: Int? = null,
    val targetMinutes: Int? = null,
    val targetHours: Int? = null,
    val reminderEnabled: Boolean? = null,
    val movedToDate: String? = null,         // YYYY-MM-DD; occurrence shows there instead
    val updatedAtEpochSeconds: Long,
)

@Serializable
data class MyDayState(
    val items: List<MyDayItem> = emptyList(),
    /** Key "itemId|YYYY-MM-DD". Manual checks for habits, skips, and "mark done anyway". */
    val marks: Map<String, MyDayMark> = emptyMap(),
    /** Key "itemId|YYYY-MM-DD" (the original date). */
    val overrides: Map<String, MyDayOverride> = emptyMap(),
    val masterUpdatedAtEpochSeconds: Long = 0L,
)
```

- Automatic completion is **derived** from silo logs at read time and never stored. Unplanned
  activities live only in their category logs (§4.5).
- `marks` holds only manual state (habit checks, skips, "mark done anyway"). `marks` and
  `overrides` entries older than 90 days are pruned on write.
- A moved occurrence is keyed by its original date; its mark is keyed by the date it moved to.
- Part-of-day mapping from existing routines: supplements `MORNING→MORNING`,
  `MIDDAY→AFTERNOON`, `NIGHT→EVENING`, `OTHER→ANYTIME`; light `MORNING`, `AFTERNOON`,
  `NIGHT→EVENING`.

### 5.1 The Training row

Not stored. For a date, if `ProgramsLibraryState.programBlocksForDate(date)` returns blocks,
Today shows one **Today's training** row that expands to those blocks. Completion reuses the
block-progress logic currently in `DashboardScreen.RoutinesSection` (move it into a shared
`programs/ProgramDayProgress.kt` so the dashboard, Training, and My Day agree). Heat/Cold and
Habits blocks that already live in a training plan keep working there until the user moves them
to My Day (§7.2).

## 6. Completion rules

Evaluated per calendar date, midnight to midnight in local time, after applying overrides.
A log belongs to the date it was logged under in its category (the date the user picked when
logging after the fact). When a date has several items for the same routine (for example the
same stack morning and evening), matching logs are consumed in time order so the first log
completes the earliest item. Logs left over after matching are shown under **Also logged**.

| Kind | Done when (for the date) | Partial |
|------|--------------------------|---------|
| Supplement routine | Day log contains a routine run with `routineId == refId` (any intake if `refId` null) | — |
| Light routine | A `LightSession` with `routineId == refId` (any session if null) | Minutes so far vs target |
| Sauna / Cold plunge | Sessions in that mode total ≥ `targetMinutes` (any session if no target) | Minutes so far |
| Stretch routine | A `StretchSession` with `routineId == refId` (any if null) | — |
| Fasting | A completed fast **ending** on the date with duration ≥ `targetHours` (a fast that crosses midnight counts on the day it ends) | **In progress** while an active fast covers the date |
| Body check-in | Any body tracker entry on the date | — |
| Habit | Manual mark `DONE` | — |
| Any kind | Manual `DONE` overrides; `SKIPPED` resolves without completing | — |

Progress ring = (done + skipped) / scheduled, with skipped shown differently. Unplanned
activities do not change the ring's denominator; they are credited through goals (§6.1).

### 6.1 Goals

Weekly goals are fulfilled by **what was logged**, planned or not. A sauna session logged under
"Also logged" counts toward a sauna goal exactly like a planned one.

- Goal progress keeps reading category logs (`computeWeeklyGoalProgress`), so unplanned and
  after-the-fact entries count automatically.
- New goal metrics so every My Day category can be a goal: **sauna sessions**, **cold plunge
  sessions**, **fasts completed**, **body check-ins**, **habit days** (per habit item), and
  **My Day adherence days** (days where every planned item was done or skipped).
- **Make this a goal** on any My Day item creates a goal of the matching metric
  (for example "Sauna · 3 per week"), prefilled from how many days the item is scheduled.
- Goals appear as the strip at the bottom of Today and in the existing Goals sheet.

## 7. Reminders

- New `MyDayReminderScheduler` generalizes `RoutineReminderScheduler`: one exact alarm per item
  with `reminderEnabled && timeMinutes != null`, rescheduled after each fire, on boot, and when
  the schedule changes (including changes arriving from sync).
- Before firing, check completion: **skip the notification if the item is already done** for
  the day.
- Notification tap opens My Day → Today with the item highlighted.
- Alarms are scheduled locally on each Android device. The reminder *settings* sync with the
  item; the alarms themselves do not.
- Notification channel: rename "Routine reminders" to "My Day reminders" (keep the channel id
  so users' existing notification settings carry over).

### 7.1 Migration of existing routine reminders (one time)

On first launch of the new version, for each `RoutineReminder` in `erv_routine_reminders`:

1. Resolve the routine (supplement, else light). If not found, drop the reminder.
2. Create a `MyDayItem` of the matching kind with `refId`, `part` from the routine's time of
   day, `timeMinutes` from the reminder's hour/minute, and `reminderEnabled` from `enabled`.
3. Days: `DAILY`→every day; `WEEKLY`/`CUSTOM_DAYS`→`repeatDays`; `ONCE`→`onlyOnDate` set to
   the next trigger date.
4. Light routines with non-empty `repeatDays` but no reminder also get an item (days from the
   routine, reminder off), so their existing schedule appears in My Day.
5. Cancel the old alarms, set a `myDayRemindersMigrated` preference, and keep the old DataStore
   for one release as a rollback safety net.

After migration, the reminder section in supplement and light routine editors is replaced by
"Scheduled in My Day: Mon, Wed, Fri · 7:30 AM · Edit", which opens the item sheet.

### 7.2 Moving Heat/Cold and Habit blocks out of training plans (M7)

For each training plan that contains **Heat/Cold** or **Habits** blocks, offer once:
"Move 3 sauna and habit blocks to My Day?"

- Each block becomes a `MyDayItem` on the same weekdays (`SAUNA` / `COLD_PLUNGE` with
  `targetMinutes`, or one `HABIT` item per checklist line). Part of day is `ANYTIME`.
- Blocks are removed from the plan only after the items are created and synced.
- Plans using a rotation or challenge strategy map by weekday only; the prompt says so.
- Declining leaves the blocks working in the Training plan as today.

## 8. Sync, backup, and privacy

- **d-tag:** `erv/myday/schedule` — kind 30078, NIP-44 encrypt-to-self, envelope
  `{ "items": [...], "marks": {...}, "overrides": {...}, "masterUpdatedAtEpochSeconds": ... }`.
- **Merge:** items by `id`, newer `lastModifiedEpochSeconds` wins; archived items win over
  older live copies; marks and overrides by key, newer `updatedAtEpochSeconds` wins.
- **Server:** add `MY_DAY_SCHEDULE_D_TAG` to `apps/web/server/src/erv_tags.rs` in M1 so the web
  companion recognizes the tag, even before it has UI (sync contract rule: no one-platform
  tags).
- **Backup / export / delete:** include My Day in ERV backup JSON, data export, and
  Settings → Data Management (new "My Day" section; deleting it also cancels alarms).
- **Privacy:** the schedule reveals routines and habits, so it stays encrypted like other ERV
  data. Reminder alarms and notification content are device-local.

## 9. Decisions and open questions

**Decided (September 27, 2026)**

1. **Day boundary:** midnight, local time.
2. **Single-day changes** to repeating items are in v1 (§4.6).
3. **Recording:** My Day is where sauna, cold plunge, light, habits, and the other categories are
   planned and logged. Category sections become libraries and history, consolidated in stages
   (§2.1, §7.2).
4. **Goals** are fulfilled by logged activity, planned or not, and can be created from My Day
   items (§6.1).
5. **Unplanned activity** can be logged for any date, including after the fact, and counts
   toward goals (§4.5).

6. **Also logged → schedule:** an **Also logged** row's menu offers **Edit schedule**, which
   opens the add-item sheet prefilled from that activity (category, routine, part of day from
   the logged time, weekday). Low priority; lives in the row menu, not as a prominent prompt.
7. **Section access:** every section remains reachable from the category menu whether or not it
   has a Launch Pad tile (unchanged from today).

**Still open**

1. **Launch Pad's future.** With My Day answering "what's today" and the Training planner
   covering workouts, Launch Pad's quick-start role shrinks. Options to evaluate before M7:
   - keep it as a small, user-picked **quick start** row (for unplanned sessions),
   - fold quick start into **+ Log activity** and remove Launch Pad,
   - turn the home screen into My Day's Today view plus the category menu.
   Decide after M3 ships, based on how the Today view feels in daily use.

## 10. Milestones (checklist owner)

- [ ] **M1 — Model and sync:** `MyDayModels` (items, marks, overrides), `MyDayRepository`,
      `MyDaySync` (`erv/myday/schedule`), merge + unit tests, server tag constant,
      backup/export/delete integration.
- [ ] **M2 — Week tab and item sheet:** My Day screen, Launch Pad tile + category entry,
      add/edit/archive items, "This day only" overrides and move-to-day.
- [ ] **M3 — Today tab:** completion engine (§6) with unit tests, actions (Start / Log /
      check), skip/undo, Also logged, shared `ProgramDayProgress`, home screen Today card
      (replaces the dashboard Activity tab).
- [ ] **M4 — Log activity:** §4.5 flow for every category, optional `loggedAtEpochSeconds` on
      category log functions, `logPastFast`, habit logging.
- [ ] **M5 — Reminders:** `MyDayReminderScheduler`, done-check before notify, deep link,
      migration (§7.1), routine editors link to My Day.
- [ ] **M6 — Goals:** new metrics (§6.1), "Make this a goal", goals strip on Today.
- [ ] **M7 — Section consolidation:** Launch Pad decision (§9) applied, unified History
      screen, Heat/Cold and Habit block move (§7.2).
- [ ] **M8 — Web companion:** read-only Today/Week on Start9, then editing.

**Acceptance test (M4):** Plan sauna Mon/Wed/Fri → on Tuesday, log an unplanned 15 min sauna
from 6 PM "Already did it" → it shows under Also logged on Tuesday, appears in Hot + Cold
history, and moves a "Sauna 3 per week" goal to 1/3.

**Acceptance test (M5):** Create a supplement routine with a 7:30 AM reminder on the old build
→ update → the reminder appears as a My Day item and still fires → log the routine → the Today
row is checked and the next day's reminder still fires; the same day's does not fire again.
