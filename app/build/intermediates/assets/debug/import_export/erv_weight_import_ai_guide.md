# ERV — AI assistant guide: weight training history → import JSON

Use this document when you (or your user) want an **LLM or script** to turn arbitrary workout exports (CSV, app dump, spreadsheet, notes) into a file **Energy Radiance Vitality** can import. The app only accepts data that matches the shape below; it validates strictly.

**Privacy:** Sending years of training logs to a **cloud** model exposes health data. Prefer **local/offline** models, **redacted** samples, or manual editing when sensitivity matters.

---

## 1. Output contract

- Emit **one UTF-8 JSON file** (no comments, no trailing commas).
- Root object fields:

| Field | Required | Value |
| --- | --- | --- |
| `ervWeightHistoryImportVersion` | **Yes** | Integer `1` |
| `exercises` | No | Array of exercise definitions (see §3). Omit if every `exerciseId` in logs already exists in the app or is a built-in id from the bundled list. |
| `dayLogs` | **Yes** | Array of per-day logs (see §4). |

Unknown top-level keys should be omitted (the app may ignore some in future versions).

---

## 2. Units and identifiers

- **Weight:** always **`weightKg`** (kilograms) inside JSON. Convert lb → kg when needed (`kg = lb × 0.45359237`).
- **Dates:** `date` on each day log must be **`YYYY-MM-DD`** (calendar date in the user’s intended locale; the app stores one `erv/weight/<date>` document per day).
- **`exerciseId`:** must be either:
  - A **built-in** stable id from the app (see **Built-in exercise IDs** in Settings → Import / export), or
  - A **UUID** string for a custom exercise the user already has, or that you add under `exercises` in this same file.

**Do not** invent new stable `erv-weight-exercise-*` ids. For new lifts, use a random UUID and include a full object in `exercises`.

---

## 3. Optional `exercises` array

Each element matches the app’s `WeightExercise` model:

| Field | Required | Notes |
| --- | --- | --- |
| `id` | **Yes** | UUID (custom) or existing built-in id. |
| `name` | **Yes** | Display name. |
| `muscleGroup` | **Yes** | Lowercase slug: e.g. `chest`, `back`, `legs`, `shoulders`, `biceps`, `triceps`, `core`, or a custom label. |
| `pushOrPull` | **Yes** | `"push"` or `"pull"`. |
| `equipment` | **Yes** | `"barbell"`, `"dumbbell"`, `"kettlebell"`, `"machine"`, or `"other"`. |
| `hiitCapable` | No | Boolean; default false. |
| `sessionSummaries` | No | Omit (the app strips it on import). |

---

## 4. Required `dayLogs` array

Each element:

| Field | Required | Notes |
| --- | --- | --- |
| `date` | **Yes** | `YYYY-MM-DD`. |
| `workouts` | **Yes** | Array of sessions (may be empty in theory; the importer skips empty sessions). |

Each **workout session** object:

| Field | Required | Notes |
| --- | --- | --- |
| `id` | No | Ignored on import; the app assigns a new id. |
| `source` | No | Ignored; import always stores sessions as **Imported** (`IMPORTED`). |
| `startedAtEpochSeconds` | No | Unix seconds if known. |
| `finishedAtEpochSeconds` | No | Unix seconds if known. |
| `durationSeconds` | No | Integer seconds if known. |
| `routineId` | No | Stripped on import. |
| `routineName` | No | Stripped on import. |
| `entries` | **Yes** | Non-empty array of logged exercises. |

Each **entry** (`entries[]`):

| Field | Required | Notes |
| --- | --- | --- |
| `exerciseId` | **Yes** | Must resolve after merging `exercises` (§3). |
| `sets` | Yes* | Array of `{ "reps": int, "weightKg": number \| null, "rpe": number \| null }`. Use `[]` only if using `hiitBlock`. |
| `hiitBlock` | Yes* | For interval-timer style logs: `{ "intervals", "workSeconds", "restSeconds", "weightKg?", "rpe?" }`. **Mutually exclusive** with non-empty `sets`. |

\* Exactly one of: non-empty `sets`, or `hiitBlock` present.

---

## 5. Behaviour in the app (so you do not contradict the UI)

- Import **merges** into existing data: new sessions are **appended** to the matching calendar day.
- Every imported session is tagged **Imported** in the log UI.
- If Nostr sync is enabled, the app publishes updated **exercise list** and **day logs** for affected dates.

---

## 6. Quality rules for the model

1. **Output valid JSON only** (no prose before or after).
2. **Do not fabricate** sets, reps, or weights that are not implied by the user’s source material.
3. **Map lifts** to the closest built-in `exerciseId` when reasonable; otherwise create a custom exercise with a new UUID in `exercises`.
4. Prefer **leaving optional fields out** rather than null-stuffing.
5. If the source is ambiguous (e.g. unknown exercise), **omit** that block or ask the user rather than guessing.

---

## 7. Minimal valid example

```json
{
  "ervWeightHistoryImportVersion": 1,
  "exercises": [],
  "dayLogs": [
    {
      "date": "2024-06-10",
      "workouts": [
        {
          "entries": [
            {
              "exerciseId": "erv-weight-exercise-squat-v1",
              "sets": [
                { "reps": 5, "weightKg": 100.0, "rpe": null },
                { "reps": 5, "weightKg": 100.0, "rpe": null }
              ]
            },
            {
              "exerciseId": "erv-weight-exercise-bench-v1",
              "sets": [
                { "reps": 8, "weightKg": 60.0, "rpe": 8.0 }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## 8. Built-in exercise names and ids

The full table of **127** built-in lifts ships in the app as **Built-in exercise IDs** (same folder as this guide in Settings). Use those rows as the only authoritative list of `erv-weight-exercise-*` strings.
