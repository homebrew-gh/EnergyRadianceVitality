# ERV — AI assistant guide: cardio history → import JSON

Use this document when you (or your user) want an **LLM or script** to turn cardio exports (Strava archive, GPX/TCX, CSV, spreadsheets, notes) into JSON shaped like **ERV’s cardio day logs**. This matches the Kotlin models in the app (`CardioDayLog`, `CardioSession`, etc.). The user imports the file from **Settings → Import And Export** (JSON is tried first; ERV cardio CSV is the fallback).

**Status:** Cardio **JSON and CSV** import is implemented with preview and merge. Sessions are stored with `source`: **`IMPORTED`**. For spreadsheet-only workflows, see **Cardio Training Import CSV Guide** on the same screen.

**Privacy:** Full Strava archives and GPX files contain **where and when** the user moved. Cloud models see that if the user pastes or uploads there. Prefer **local/offline** models, **redacted** samples, or **aggregate** fields only (date, duration, distance) when sensitivity matters.

### Which reference document to use (same idea as weight training)

| Document | Purpose |
| --- | --- |
| **This guide** | **JSON** import: `ervCardioHistoryImportVersion`, optional `customActivityTypes`, required `dayLogs` / `sessions` / `activity` fields. Includes **Strava → ERV** mapping hints. |
| **Cardio Training Import CSV Guide** | Same sessions as a **flat CSV** (one row per session). |
| **Cardio Training Built-In Activity Reference** | Shipped **inside this guide** (§3 table): fixed enum strings for `activity.builtin`—the cardio analogue of the weight **built-in exercise id** list. |
| **Cardio Training Nostr Events Reference** | *Optional only.* How cardio is labeled on **Nostr** when sync is on. **Not** required to produce import files. |

---

## 1. How Strava exposes data (so you know what to convert)

Strava does **not** ship a single “standard fitness JSON” everyone else reads. In practice:

| Path | What you get | Notes for conversion |
|------|----------------|----------------------|
| **Bulk account export** | User requests an archive from Strava (web: **Settings → My Account → Download / request your archive**). After processing, they receive a **ZIP** with **activities** (often **per-activity GPX** and/or metadata files), **social** data, **profile** fields, etc. | Best for **migrating history** without calling the API. Exact folder layout can change; scan for `*.gpx`, `*.tcx`, and any bundled **CSV/JSON** activity summaries inside the archive. |
| **Single activity** | From an activity page: **Export GPX** (menu). **TCX**: append `/export_tcx` to the activity URL (see [Strava help — Exporting your data](https://support.strava.com/hc/en-us/articles/216918437-Exporting-your-Data-and-Bulk-Export)). **Original file** (often **FIT** from a watch) when the user uploaded from a device. | GPX/TCX give **trackpoints, time, distance**; good for **outdoor** duration + distance + optional HR in extensions. **Indoor treadmill** activities may have **little or no GPS** — rely on Strava’s **elapsed time** and **distance** fields from metadata when present. |
| **Strava API** (developer apps) | OAuth apps can read **activity** resources and **streams** (latlng, time, distance, heartrate, etc.). | Powerful but **not** something ERV implements as a built-in sync; users would use a **separate script** that outputs this guide’s JSON. |

**Why not a dedicated “Strava importer” in ERV?** Vendor export layouts and API fields change often. ERV defines one **canonical import JSON** (and CSV); you map Strava or any other source into that contract with a script or LLM. That keeps the app maintainable.

---

## 2. Output contract

- Emit **one UTF-8 JSON file** (no comments, no trailing commas).
- Root object:

| Field | Required | Value |
| --- | --- | --- |
| `ervCardioHistoryImportVersion` | **Yes** | Integer `1` |
| `customActivityTypes` | No | Array of `{ id, name, optionalMet? }` for any `customTypeId` you reference under `sessions[].activity`. Omit if every session uses a **built-in** enum only. |
| `dayLogs` | **Yes** | Array of per-day logs (see §5). |

Unknown top-level keys should be omitted.

**In the app:** Import **merges** into existing cardio logs (sessions are **appended** per day). **`gpsTrack`** and **`routeImageUrl`** from files are **not** kept (imports are summary-safe). Optional relay sync after import uses the same logical day logs (see §9)—nothing extra belongs in the file you build here.

---

## 3. Units and enums

- **Distance:** always **`distanceMeters`** on sessions/segments (double). Strava often gives **meters** or **km** in exports — convert to meters (`km × 1000`, `mi × 1609.344`).
- **Dates:** `date` on each day log is **`YYYY-MM-DD`** (calendar day in the user’s intended timezone for that activity’s **start**).
- **Duration:** `durationMinutes` is **integer** minutes (round sensibly from seconds: `(seconds + 30) / 60` or floor, but **be consistent** and state your rule in a side note if the user cares).

**`activity.builtin` (fixed vocabulary - like weight's built-in exercise ids):** use one of these strings, or omit `builtin` and use a **custom** activity type instead (`customTypeId` / `customActivityTypes`). If Strava’s sport does not map cleanly, prefer `OTHER` plus a custom type (see §6). Avoid `ACTIVE_RECOVERY` for ordinary single-session imports; it is mainly for interval-template legs.

| `builtin` value | Typical use |
| --- | --- |
| `WALK` | Walking |
| `RUN` | Running, trail run, virtual run |
| `SPRINT` | Sprint / interval-style run work |
| `RUCK` | Rucking (pack load: `ruckLoadKg` outdoor, treadmill pack fields indoor) |
| `HIKE` | Hiking (slower than run; user preference if borderline) |
| `BIKE` | Cycling (road, MTB, gravel, e-bike unless user wants `OTHER`) |
| `SWIM` | Swimming |
| `ELLIPTICAL` | Elliptical machine |
| `ROWING` | Rowing (machine or boat—user context) |
| `STATIONARY_BIKE` | Indoor spin / stationary bike |
| `JUMP_ROPE` | Jump rope |
| `BATTLE_ROPE` | Battle ropes / rope intervals |
| `BURPEES` | Burpees as conditioning work |
| `JUMPING_JACKS` | Jumping jacks / calisthenic cardio |
| `AIR_BIKE` | Assault / fan bike |
| `SKI_ERG` | SkiErg |
| `ACTIVE_RECOVERY` | Special recovery leg for interval-style segments; usually omit for ordinary imports |
| `OTHER` | Anything else; pair with **custom** type name/id when possible |

- **`CardioModality`** (`modality`): `OUTDOOR` or `INDOOR_TREADMILL`. Treadmill modality in ERV is only meaningful for **walk, run, sprint, ruck**; for other built-ins, use **`OUTDOOR`** (non-treadmill indoor bike/rower is still `OUTDOOR` in the app model).
- **`CardioSessionSource`** (`source`): Omit or use `MANUAL` in the file; the importer **always** stores merged sessions as **`IMPORTED`**. (`DURATION_TIMER` is for in-app timers only.)
- **`CardioSpeedUnit`** (inside `treadmill`): `MPH` or `KMH`.

---

## 4. `customActivityTypes` (optional)

Each element:

| Field | Required | Notes |
| --- | --- | --- |
| `id` | **Yes** | UUID string; must match `activity.customTypeId` where used. |
| `name` | **Yes** | Display name. |
| `optionalMet` | No | Double; only if the user supplied a MET estimate. |

---

## 5. `dayLogs` array

Each element:

| Field | Required | Notes |
| --- | --- | --- |
| `date` | **Yes** | `YYYY-MM-DD`. |
| `sessions` | **Yes** | Array of sessions (may be empty; importers may skip empty days). |

### 5.1 Each `sessions[]` object (`CardioSession`)

Align fields with the app’s `CardioSession` (camelCase):

| Field | Required | Notes |
| --- | --- | --- |
| `id` | No | Omit or random UUID; importer may assign fresh ids. |
| `activity` | **Yes** | Object (see §5.2). |
| `modality` | No | Default `OUTDOOR`. |
| `treadmill` | No | `CardioTreadmillParams` if `INDOOR_TREADMILL`: `speed`, `speedUnit`, `inclinePercent`, optional `distanceMeters`, optional `loadKg` (**kg** for pack on treadmill ruck). |
| `durationMinutes` | **Yes** | Integer ≥ 1 typically. |
| `distanceMeters` | No | Omit if unknown. |
| `estimatedKcal` | No | Omit unless source provided it and user wants it stored. |
| `routineId`, `routineName` | No | Usually omit for imports. |
| `source` | No | Ignored on import; sessions are stored as **Imported** (`IMPORTED`). |
| `startEpochSeconds`, `endEpochSeconds` | No | Unix seconds if known from GPX/Strava. |
| `loggedAtEpochSeconds` | No | Omit; app can set “now” on import. |
| `heartRate` | No | `{ avgBpm?, maxBpm?, minBpm? }` if known. |
| `segments` | No | Non-empty → multi-leg brick session; each segment matches segment shape (activity, modality, treadmill?, durationMinutes, distanceMeters?, estimatedKcal?, orderIndex). |
| `gpsTrack` | No | **Omit** for AI imports unless the user explicitly wants a full track in the file (large). Future importer may accept simplified tracks: `points[]` with `lat`, `lon`, `epochSeconds`, optional `altitudeMeters`. |
| `routeImageUrl` | No | Omit. |
| `ruckLoadKg` | No | Outdoor ruck **pack mass in kg**. |
| `elevationGainMeters`, `elevationLossMeters` | No | If Strava or GPX summary provides them. |

### 5.2 `activity` (`CardioActivitySnapshot`)

| Field | Required | Notes |
| --- | --- | --- |
| `builtin` | Yes* | Enum string, or `null` if custom. |
| `customTypeId` | Yes* | UUID if custom; else `null`. |
| `customName` | No | Helps humans; can match custom type name. |
| `displayLabel` | **Yes** | What the UI shows, e.g. `Running` or `Alpine Ski` (for custom). |

\* Either `builtin` is set **or** `customTypeId` + `customName` / name from library.

---

## 6. Strava → ERV mapping hints (non-exhaustive)

Strava’s **sport type** / activity name strings vary (and change). Use this as a **starting point**, not a strict API mapping:

| Strava-like label | Suggested `builtin` | Notes |
| --- | --- | --- |
| Run, Virtual Run, Trail Run | `RUN` | Trail → still `RUN` unless user prefers `HIKE` for walking pace. |
| Ride, Virtual Ride, EBikeRide, MountainBikeRide, GravelRide | `BIKE` | E-bike/gravel still `BIKE` unless user asks for `OTHER`. |
| Walk | `WALK` | |
| Hike | `HIKE` | |
| Swim | `SWIM` | |
| Workout, WeightTraining, Yoga, AlpineSki, etc. | `OTHER` + **custom** type | Set `customActivityTypes[].name` from Strava’s type or user label. |

**Modality:** If Strava indicates **treadmill** (e.g. activity name or subtype contains “treadmill”) **and** `builtin` is `WALK`, `RUN`, `SPRINT`, or `RUCK`, set `modality` to `INDOOR_TREADMILL` and fill `treadmill` if speed/incline are known; otherwise minimal plausible `speed`/`speedUnit` only if the user wants distance-from-speed estimates.

---

## 7. Quality rules for the model

1. Output **valid JSON only** (no prose before or after).
2. **Do not fabricate** GPS paths, HR, or calories not present in the source.
3. Prefer **omitting** unknown optional fields over null-stuffing.
4. **One Strava activity** → typically **one** `sessions[]` entry on the **local calendar date** of the start (respect user timezone if given).
5. If the source is ambiguous, **prefer** `OTHER` + custom type rather than guessing a built-in.

---

## 8. Minimal valid example

```json
{
  "ervCardioHistoryImportVersion": 1,
  "customActivityTypes": [],
  "dayLogs": [
    {
      "date": "2025-08-02",
      "sessions": [
        {
          "activity": {
            "builtin": "RUN",
            "customTypeId": null,
            "customName": null,
            "displayLabel": "Running"
          },
          "modality": "OUTDOOR",
          "durationMinutes": 52,
          "distanceMeters": 10000.0,
          "startEpochSeconds": 1754140800,
          "endEpochSeconds": 1754143920
        }
      ]
    }
  ]
}
```

---

## 9. Nostr sync (optional — not part of the import file)

The JSON you produce for import is **only** the structure in §§2–5. It does **not** include relay kinds, `d` tags, or ciphertext.

If the user has **Nostr sync** enabled, the app may publish the same logical **`CardioDayLog`** data as encrypted **kind `30078`** events (replaceable identifiers such as `erv/cardio/YYYY-MM-DD` per day). That layout is documented for curiosity or tooling in **Cardio Training Nostr Events Reference** on **Settings → Import And Export**. **You do not need that document** to build a valid import file.
