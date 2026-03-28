# ERV — AI / coach guide: weekly programs → import JSON

Use this document when an **LLM, coach tool, or script** should output JSON that **Energy Radiance Vitality (ERV)** can import as **Programs** (weekly plans with day blocks for strength, cardio, stretching, sauna/cold, rest, or notes).

The user imports from **Settings → Import And Export → Import Programs File**. The app **parses, validates, shows a preview, then merges** into local program storage (same merge rules as the Programs category upload).

**Privacy:** Program names and notes may include health or training context. If the user pastes into a **cloud model**, that content may leave the device. Prefer **local/offline** tools or **redacted** copies when appropriate.

---

## 1. Output contract

- Emit **one UTF-8 JSON file** (no comments, no trailing commas).
- Root must be **one** of:
  - **Envelope object** (recommended for multiple programs + optional active id), or
  - **Single `FitnessProgram` object** (must include `name` and usually `weeklySchedule`), or
  - **JSON array** of `FitnessProgram` objects.

### 1.1 Envelope (recommended)

| Field | Required | Value |
| --- | --- | --- |
| `ervImportVersion` | No | Integer; use `1` for current parsers |
| `programs` | **Yes** (envelope path) | Non-empty array of program objects |
| `activeProgramId` | No | If set, must equal the `id` of one program in `programs`; after merge, that program becomes the **active** program in the Launch Pad |

Unknown top-level keys are ignored where safe.

### 1.2 Merge behavior (what the app does)

- Each program is keyed by **`id`**. If a program with the same `id` already exists locally, the **imported** program **replaces** that entry for matching ids.
- New ids are **added** to the library.
- If `activeProgramId` is present and valid, it is applied **after** merge.

There is **no relay/Nostr upload** for programs in the current build; data stays **on device** (and any future sync would follow the same JSON shapes).

---

## 2. Calendar: `dayOfWeek` (ISO)

ERV uses **ISO-8601** weekday integers, **Monday = 1** through **Sunday = 7**.

| dayOfWeek | Day |
| --- | --- |
| 1 | Monday |
| 2 | Tuesday |
| 3 | Wednesday |
| 4 | Thursday |
| 5 | Friday |
| 6 | Saturday |
| 7 | Sunday |

Each `ProgramWeekDay` has:

| Field | Required | Notes |
| --- | --- | --- |
| `dayOfWeek` | **Yes** | 1–7 only |
| `blocks` | No | Array of `ProgramDayBlock`; omit or `[]` for rest |

You may supply **multiple** `ProgramWeekDay` rows with the same `dayOfWeek`; the app **concatenates** blocks in order. Prefer **one row per day** for clarity.

---

## 3. `FitnessProgram`

| Field | Required | Notes |
| --- | --- | --- |
| `id` | No | Stable UUID string recommended so re-imports update the same program |
| `name` | **Yes** | Non-empty display name |
| `description` | No | Free text |
| `sourceLabel` | No | e.g. coach name, `"ChatGPT"`, `"TrainingPeaks export"` |
| `weeklySchedule` | No | List of `ProgramWeekDay`; empty = blank week template |
| `createdAtEpochSeconds`, `lastModifiedEpochSeconds` | No | Optional metadata |

---

## 4. `ProgramDayBlock`

| Field | Required | Notes |
| --- | --- | --- |
| `id` | No | UUID per block; omit to let the client assign |
| `kind` | **Yes** | One of the **serial names** in §5 |
| `title` | No | Short label (Launch Pad / UI) |
| `notes` | No | Longer text; shown for **rest**, **custom**, and other kinds as context |
| `weightExerciseIds` | For **weight** | List of **ERV weight exercise ids** (see Weight Training built-in id doc in the same Import screen) |
| `weightRoutineId` | No | Optional saved **weight routine** id if the user already has that routine |
| `cardioActivity` | For **cardio** (no routine) | **Exact** `CardioBuiltinActivity` enum name: `RUN`, `BIKE`, `WALK`, … (see **Cardio Training Import AI Guide** §3 table in-app) |
| `cardioRoutineId` | No | Saved **cardio routine** id for multi-leg or single-leg timer |
| `stretchRoutineId` | For **stretch_routine** | Saved **stretch routine** id from the user’s library |
| `stretchCatalogIds` | For **stretch_catalog** | List of **built-in stretch catalog ids** bundled with ERV (e.g. `builtin_hip_flexor_lunge`) |
| `heatColdMode` | For **heat_cold** | `SAUNA` or `COLD_PLUNGE` |
| `targetMinutes` | No | Hint for cardio countdown timer, stretch session length, or heat/cold duration (app interprets per feature) |
| `checklistItems` | For **other** | Array of strings; each line is a habit the user checks off on the **dashboard** for the selected calendar day (diet, water, reading, progress photo reminder, etc.). Not synced as structured “tasks” in JSON import beyond this list. |

**Rule:** For each `kind`, populate the fields that matter for that type; leave others empty or omit them.

---

## 5. Block `kind` values (JSON serial names)

These are the **wire strings** (lowercase with underscore). They map to ERV `ProgramBlockKind`:

| `kind` value | Purpose |
| --- | --- |
| `weight` | Resistance session: `weightExerciseIds` and/or `weightRoutineId` |
| `cardio` | `cardioRoutineId` **or** `cardioActivity` (+ optional `targetMinutes`) |
| `stretch_routine` | Guided routine: `stretchRoutineId` |
| `stretch_catalog` | Built-in poses: `stretchCatalogIds` |
| `heat_cold` | `heatColdMode` + optional `targetMinutes` |
| `rest` | Recovery: `title` / `notes` |
| `custom` | Free-form plan text: `title` / `notes` |
| `other` | Habit checklist: `title` / `notes` plus **`checklistItems`** (array of strings). User marks completion per day in the app (Launch Pad → Programs sheet). |

---

## 6. Minimal examples

### 6.1 Envelope — two programs, set active

```json
{
  "ervImportVersion": 1,
  "activeProgramId": "550e8400-e29b-41d4-a716-446655440001",
  "programs": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Off-season base",
      "sourceLabel": "Coach Ada",
      "weeklySchedule": [
        {
          "dayOfWeek": 1,
          "blocks": [
            {
              "kind": "weight",
              "title": "Lower",
              "weightExerciseIds": ["erv-weight-exercise-squat-v1", "erv-weight-exercise-deadlift-v1"]
            }
          ]
        },
        {
          "dayOfWeek": 3,
          "blocks": [
            {
              "kind": "cardio",
              "title": "Aerobic",
              "cardioActivity": "RUN",
              "targetMinutes": 40
            }
          ]
        }
      ]
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "name": "Deload week",
      "weeklySchedule": [
        {
          "dayOfWeek": 7,
          "blocks": [{ "kind": "rest", "title": "Full rest" }]
        }
      ]
    }
  ]
}
```

### 6.2 Single program object (no envelope)

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "Hypertrophy block A",
  "description": "Mesocycle 1",
  "weeklySchedule": [
    {
      "dayOfWeek": 2,
      "blocks": [
        {
          "kind": "stretch_catalog",
          "title": "Warm-up mobility",
          "stretchCatalogIds": ["builtin_cat_cow", "builtin_worlds_greatest"],
          "targetMinutes": 12
        },
        {
          "kind": "weight",
          "title": "Upper",
          "weightExerciseIds": ["erv-weight-exercise-bench-v1", "erv-weight-exercise-ohp-v1"]
        }
      ]
    }
  ]
}
```

### 6.3 Sauna / cold

```json
{
  "kind": "heat_cold",
  "title": "Contrast",
  "heatColdMode": "SAUNA",
  "targetMinutes": 15,
  "notes": "Hydrate; stop if dizzy."
}
```

---

## 7. Cross-references (other in-app guides)

| Need | Open in ERV |
| --- | --- |
| **Weight exercise ids** (e.g. `erv-weight-exercise-bench-v1`) | **Weight Training Built-In Exercise IDs** |
| **Cardio `cardioActivity` enum strings** | **Cardio Training Import AI Guide** (built-in activity table) |

Custom user-defined exercise ids from the user’s library are allowed in `weightExerciseIds` if the id exists on the device after merge.

---

## 8. Prompt checklist (for LLMs)

1. Output **only valid JSON** (no markdown fences unless the user will strip them).
2. Use **ISO `dayOfWeek` 1–7**.
3. Use **exact** `kind` strings from §5.
4. For **weight**, prefer **stable built-in ids** from the weight reference doc when possible.
5. For **cardio** without a routine, use **exact** `RUN`, `BIKE`, etc.
6. Include **`id`** on programs you expect to **update** on re-import.
7. Set **`activeProgramId`** only when the user should **switch** their active program immediately after import.
8. For **`other`** blocks, use **`checklistItems`** as an array of short, actionable strings (habits the user checks off in the app per day).
9. Do **not** fabricate medical claims in `notes`; keep text aligned with what the user or coach provided.

---

## 9. Versioning

- **`ervImportVersion`**: use `1` today. Parsers ignore unknown fields where safe; breaking changes would bump a documented version in app release notes.
