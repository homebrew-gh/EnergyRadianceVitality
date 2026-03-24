# ERV — Cardio CSV import (human / spreadsheet)

The app accepts a **UTF-8 CSV** with a **header row** followed by **one row per cardio session**. Import merges into existing logs; each generated session is stored as **Imported**.

**Reference set (same screen in Settings → Import And Export):** **Cardio Training Import AI Guide** for the full JSON model, built-in activity enums, and Strava mapping; **this file** for spreadsheets; **Cardio Training Nostr Events Reference** only if you need relay/`d` tag background (not required for imports).

---

## 1. Column reference

Header names are **case-insensitive**. Required columns:

| Column | Required | Description |
| --- | --- | --- |
| `date` | **Yes** | `YYYY-MM-DD` calendar date (start of activity). |
| `duration_minutes` | **Yes** | Integer **≥ 1**. |

You must identify the activity with **one** of:

| Column | Required | Description |
| --- | --- | --- |
| `builtin` | One of three | Built-in enum: `WALK`, `RUN`, `SPRINT`, `RUCK`, `HIKE`, `BIKE`, `SWIM`, `ELLIPTICAL`, `ROWING`, `STATIONARY_BIKE`, `JUMP_ROPE`, `OTHER`. Do not combine with the custom columns. |
| `custom_activity_name` | One of three | Free-text name. First occurrence creates a **new** custom type; same name later reuses that id. |
| `custom_type_id` | One of three | UUID of a type already in the app **or** created earlier in the same file via `custom_activity_name`. |

Optional columns:

| Column | Description |
| --- | --- |
| `display_label` | UI label; defaults to built-in display name or custom name. |
| `distance_m` | Distance in **meters** (optional). |
| `modality` | `OUTDOOR` (default) or `INDOOR_TREADMILL` (only with built-in walk, run, sprint, or ruck). |
| `speed` | Treadmill belt speed (required for indoor walk/run/ruck; omit for indoor **SPRINT** to use app defaults). |
| `speed_unit` | `MPH` or `KMH` (default `MPH`). |
| `incline_pct` | Incline **%**; default `0`. |
| `pack_kg` | Ruck **pack mass in kg** on **indoor** treadmill rows (`RUCK` only). |
| `ruck_load_kg` | Pack **kg** for **outdoor** `RUCK`. |
| `estimated_kcal` | Optional kcal. |
| `start_epoch_seconds` | Unix start time. |
| `end_epoch_seconds` | Unix end time. |

**Rules:**

- Do **not** put commas inside fields (parser splits on commas only).
- You may wrap fields in double quotes.

---

## 2. Example

Outdoor:

```csv
date,duration_minutes,builtin,distance_m
2025-08-02,45,RUN,10000
2025-08-03,30,WALK,2400
```

Indoor treadmill (add `modality`, `speed`, `speed_unit`; optional `incline_pct`, `distance_m`):

```csv
date,duration_minutes,builtin,modality,speed,speed_unit,incline_pct,distance_m
2025-08-05,35,RUN,INDOOR_TREADMILL,5.2,MPH,2,0
```

---

## 3. JSON import

For multi-segment (brick) workouts, heart-rate scaffolding, or GPX-derived fields, use **JSON** per **Cardio import (AI guide)** in Settings.
