# ERV — Weight training CSV import (human / spreadsheet)

The app accepts a **UTF-8 CSV** with a **header row** followed by **one row per set**. Import merges into existing logs; each generated workout session is stored as **Imported**.

**Reference set (same screen in Settings → Import And Export):** **Weight Training Import AI Guide** for JSON, optional `exercises`, and HIIT blocks; **this file** for flat set rows; **Weight Training Built-In Exercise IDs** for every `erv-weight-exercise-*` string.

---

## 1. Column reference

Header names are **case-insensitive**. Required columns:

| Column | Required | Description |
| --- | --- | --- |
| `date` | **Yes** | `YYYY-MM-DD` calendar date. |
| `session_key` | **Yes** | Any non-empty label grouping rows into one workout on that day. Same `date` + same `session_key` → one session. Use `a`, `b`, `morning`, UUIDs, etc. |
| `exercise_id` | **Yes** | ERV built-in id (see **Weight Training Built-In Exercise IDs** in Settings) or a **custom UUID** already present in the app. |
| `set_index` | **Yes** | Integer **≥ 1**, sort order for sets of that exercise in that session. Duplicate indices for the same exercise overwrite (last wins). |
| `reps` | **Yes** | Integer **≥ 0**. |

Optional columns:

| Column | Required | Description |
| --- | --- | --- |
| `weight_kg` | No | Load in **kilograms**; empty = bodyweight / unknown. Must be numeric if present. |
| `rpe` | No | Rate of perceived exertion; empty or numeric **0–10**. |

**Rules:**

- Do **not** put commas inside fields (the parser splits on commas only).
- You may wrap fields in double quotes.
- Every data row must have enough columns for all required fields.

---

## 2. Example

```csv
date,session_key,exercise_id,set_index,reps,weight_kg,rpe
2024-06-10,workout-a,erv-weight-exercise-squat-v1,1,5,100,
2024-06-10,workout-a,erv-weight-exercise-squat-v1,2,5,100,
2024-06-10,workout-a,erv-weight-exercise-bench-v1,1,8,60,8
2024-06-12,morning,erv-weight-exercise-deadlift-v1,1,3,140,
```

This creates **two** imported sessions (June 10 and June 12), each with the exercises built from grouped rows.

---

## 3. Custom exercises

CSV only references `exercise_id`. If you need **new** lifts not in the built-in list:

1. Add them in the app first (Exercises tab), **or**
2. Use **JSON import** and include an `exercises` array (see the **AI import guide**).

---

## 4. Built-in exercise table

Open **Weight Training Built-In Exercise IDs** from **Settings → Import And Export** for the full **name ↔ `exerciseId`** list (127 entries).

---

## 5. JSON vs CSV

- **JSON:** best for AI/tools, nested sessions, optional `hiitBlock`, and inline custom `exercises`.
- **CSV:** best for spreadsheet editing and simple set/rep/weight grids.

The file picker accepts either format; the app tries JSON first, then CSV.
