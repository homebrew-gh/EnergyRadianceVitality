# ERV — Plan of Action

This document outlines how to structure data, Nostr events, and the application so ERV (Energy Radiance Vitality) meets its requirements: local-first health logging, Nostr-backed storage with NIP-42 and NIP-44, support for nsec and Amber login, and a sun-themed UI with category silos.

---

## 1. Goals and constraints

| Goal | Constraint / approach |
|------|------------------------|
| All health data on device first | Local DB (e.g. Room) as source of truth; relay as backup/sync target |
| Off-device storage on personal relay | Nostr events; user configures relay URL |
| Private relay access | NIP-42 authentication before subscribe/publish |
| Privacy of health data | NIP-44 encrypt all event content (encrypt-to-self) |
| Login options | (1) Nostr nsec on device, (2) Remote signer (e.g. Amber) |
| No conflict with other apps on same relay | Namespace all events with `erv/` and use a single, well-known kind |
| Compatibility with remote signers (Amber) | Use a standard parameterized replaceable kind (e.g. 30078), avoid obscure custom kinds |
| Many categories, clear structure | One “silo” per category; each silo developed and coded separately |
| **No Google / developer data sharing** | App must work **without Google Play**. Device data (e.g. heart rate) via **direct BLE** (standard Heart Rate Profile) or **direct device APIs** (e.g. libpebble3). No Health Connect dependency. Supports GrapheneOS; no data shared with developer or Google. |

Reference projects:

- **FiatLife** — [github.com/homebrew-gh/fiatlife](https://github.com/homebrew-gh/fiatlife): NIP-42, NIP-44, kind 30078, d-tags `fiatlife/...`, Amber-style flows.
- **Runstr** — [github.com/RUNSTR-LLC/RUNSTR](https://github.com/RUNSTR-LLC/RUNSTR): Kind 1301 for workout records; useful for ideas on workout/cardio event shape, but ERV will use its own kind and namespace to avoid clashes.
- **Nostr NIPs** — [github.com/nostr-protocol/nips](https://github.com/nostr-protocol/nips): NIP-42 (auth), NIP-44 (encryption), NIP-33/01 (parameterized replaceable events).
- **Core Devices / Pebble Time 2** — [github.com/coredevices](https://github.com/coredevices): Open-source Pebble ecosystem (PebbleOS, mobile companion app in Kotlin, [libpebble3](https://github.com/coredevices/mobileapp) KMP library). Target for heart-rate monitoring during workouts/cardio; see §12.

**Companion website and web-of-trust sharing** — This plan does **not** lay out a separate web product in detail. The product direction for trust-filtered routine discovery, forking, and a richer web surface lives in [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md). Tradeoffs for fitness promotion and community are in [COMPANION_WEB_COMMUNITY_PITFALLS_BENEFITS.md](COMPANION_WEB_COMMUNITY_PITFALLS_BENEFITS.md). [DATA_IMPORT_EXPORT.md](../import/DATA_IMPORT_EXPORT.md) ties import/export contracts to that graph at a technical level.

---

## 2. Nostr event design

### 2.1 Kind and namespace

- **Kind**: Use **30078** (application-specific parameterized replaceable events, same as FiatLife). This is in the 30000–39999 range, well-supported by clients and signers (e.g. Amber), and avoids “custom kind” issues.
- **Namespace**: All ERV events use a `d` tag that starts with **`erv/`** so they never collide with FiatLife (`fiatlife/...`) or other apps.

### 2.2 Event shape (high level)

- **kind**: `30078`
- **tags**: First tag is `["d", "<identifier>"]` where `<identifier>` starts with `erv/`; the exact pattern varies by category (e.g. `erv/<category>/<date>`, `erv/bodyweight`, `erv/goals`). See §2.3 for the full list.
- **content**: **NIP-44 encrypted** JSON. Encrypt to the user’s own pubkey (encrypt-to-self) so only they can read it on the relay.
- **pubkey**: User’s Nostr pubkey (local key or Amber-managed key).
- **created_at**: Unix timestamp.

Replaceable semantics: for a given `(kind, pubkey, d)`, only the latest event is relevant; older ones are logically replaced.

### 2.3 Category silos and `d`-tag scheme

**Canonical reference for implementation:** §2.3 lists every ERV d-tag and what it stores at a high level. §2.4 defines the exact JSON field names, merge/sync behavior, and UI for each. Use both: §2.3 for structure and routing; §2.4 for data models and app behavior.

Each category is a silo: its own screens and local models, but the same Nostr kind (30078) and NIP-44 encryption. D-tag patterns:

| Category      | d-tag pattern         | Content (encrypted JSON) |
|---------------|------------------------|---------------------------|
| Stretching    | `erv/stretching/poses` (optional) + `erv/stretching/routines` (optional) + `erv/stretching/<date>` | Bundled **general stretch** catalog + user-defined routines + session log by date; optional synced custom stretches; see §2.4 |
| Weight training | `erv/weight/exercises` + `erv/weight/routines` + `erv/weight/<date>` | Exercise list + routine templates + daily log with `workouts[]`; see §2.4 and [WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md) |
| Cardio        | `erv/cardio/routines` (optional master) + `erv/cardio/<date>` or `erv/cardio/<uuid>` | Master: saved routines + custom activity types. Daily log: sessions (duration, activity, modality outdoor/treadmill, treadmill params, optional distance, optional estimatedKcal, optional GPS track, optional HR scaffolding when monitor connected); see §2.4, §12 |
| Sauna         | `erv/sauna/<date>`     | Sessions with duration + optional temp; entries may reference a contrast routine; see §2.4 |
| Cold plunge   | `erv/cold/<date>`      | Sessions with duration + optional temp; entries may reference a contrast routine; see §2.4 |
| **Heat/cold routines (shared)** | `erv/heatcold/routines` (optional) | User-defined contrast routines (alternating sauna/cold steps); used by Sauna and Cold plunge; see §2.4 |
| Light therapy | `erv/light/list` + `erv/light/<date>` | Master list (devices + routines) + daily log (sessions with minutes, optional device/routine); see §2.4 |
| Supplements   | `erv/supplements/list` + `erv/supplements/<date>` | Master list (what to take) + daily log (what was taken); see §2.4 |
| Sleep         | `erv/sleep/<date>`     | Sleep window (bed/wake or duration), quality, notes; optional `source` and `deviceMetrics` when from device; see §2.4 |
| **Body weight (optional)** | `erv/bodyweight`      | Diary: date → weight in **lb or kg** (default lb); store in kg for formulas; MET and Pandolf use kg internally (convert from lb when needed); see §2.4 |
| **Goals**     | `erv/goals`            | Single replaceable event: all category goals; see §7.1 |

**Identifiers:** Use **date-based** (e.g. `erv/sleep/2026-03-07`) for one replaceable event per user per category per day, or **UUID-based** (e.g. `erv/cardio/<uuid>`) where a category needs multiple distinct replaceable events per day. **Weight training daily log** is **not** UUID-scoped: the shipped app uses **`erv/weight/<YYYY-MM-DD>`** with a **`workouts[]` array** inside the encrypted JSON so **multiple sessions on the same calendar day** live in one replaceable event (see §2.4 Workout log and [WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md)). Prefer date-scoped d-tags where the payload can hold multiple sessions; use per-session UUID d-tags only when the design requires separate replaceable events per session (see §2.4 per category).

### 2.4 Category detail: data models and features

This section is the **authoritative source** for JSON field names, payload shapes, merge/sync behavior, and UI for each category. The d-tags themselves are listed in §2.3; here each category’s **content** (encrypted JSON), **application behavior** (timers, auto-log, merge rules), and **UI** are defined so implementers and AI agents can build correctly.

**Build order:** Light therapy and Supplements first; then Cardio; then Workout. See §9 for the full implementation phase order.

**Light therapy** — Track **time exposed** per session, with an in-session timer and automatic logging. Extended with **device catalog** and **routines** (like Supplements).

- **d-tags**: `erv/light/list` (one replaceable event: devices + routines); `erv/light/<date>` (one replaceable event per day: sessions).
- **Content (encrypted JSON)** — **Master** (`erv/light/list`): `{ "devices": [ { "id", "name", "brand", "deviceType", "wavelengths", "powerOutput", "recommendedDurationMinutes", "notes" } ], "routines": [ { "id", "name", "timeOfDay", "durationMinutes", "deviceId", "repeatDays", "notes" } ] }`. **Daily log** (`erv/light/<date>`): `{ "sessions": [ { "minutes", "deviceId", "deviceName", "routineId", "routineName", "loggedAtEpochSeconds" } ] }`.
- **UI**: **Timer** tab — red-themed countdown; set duration (presets or custom), optional routine/device; start → full-screen countdown, audible “time’s up”, auto-log. **Routines** tab — create routines (name, time of day, duration, assigned device, repeat days); run (start timer) or edit/delete. **Log** tab — sessions by date (name + duration). **Lights** tab — catalog devices (name, brand, type e.g. red/NIR/UV/circadian, wavelengths, power, recommended duration); assign to routines. Routines and logged sessions appear on the dashboard (Routines section + Activity with “name • X min”).
- **Session timer** — Red-themed full-screen countdown; user sets duration (e.g. 5, 10, 15, 20, 30 min or custom). At **time’s up**, **audible notification** (ToneGenerator) and **auto-log** (merge into `erv/light/<date>`). Cancel option. Can start from Timer tab or from a routine (dashboard or Routines tab).

**Supplements** — Track what the user takes, dosage, frequency, when to take, and optionally enrich with API data (brand, nutrition facts, benefits).

- **Master list (what the user is taking)** — One replaceable event so the list syncs across devices. **d-tag**: `erv/supplements/list`. **Content**: array of supplement entries, e.g. `{ "supplements": [ { "id": "uuid", "name": "Vitamin D3", "dosage": "2000 IU", "frequency": "daily" | "weekly" | custom, "whenToTake": "morning" | "with breakfast" | "before bed" | free text, "productId": "optional-external-api-id" } ] }`. User can add/edit/remove supplements, set dosage, frequency, and when to take.
- **Daily log (what was taken)** — **d-tag**: `erv/supplements/<date>`. **Content**: e.g. `{ "taken": [ { "supplementId": "uuid", "takenAt": "ISO time or null", "dosageTaken": "2000 IU" } ] }` for that day. Used for "did I take my supplements today?" and goal (daily habit).
- **Supplement info API (optional but desirable)** — Fetch extra data for a given supplement: **brand**, **general nutrition facts**, **what it helps with**. Candidate: **[NIH Dietary Supplement Label Database (DSLD)](https://dsld.od.nih.gov/)** — free, label information for supplements marketed in the U.S., searchable by product/brand/ingredient; [API guide](https://dsld.od.nih.gov/api-guide) for programmatic access. Use to show brand, ingredients, dosage from label, and claims/benefits when the user adds a supplement or views details. Optional: let user link a list entry to a DSLD product (e.g. by search) and store `productId` so the app can display rich info without overloading the stored payload.

**Stretching** — **Initial implementation: general stretches only** — a small, **bundled** catalog of everyday stretches (stable **stretch IDs**, display name, short instructions or cues, optional tags such as target body area, optional static image). Users build **routines**: named ordered lists of stretch IDs for activities such as pre-workout, post-workout, pre-run, pre-swim, or custom labels (e.g. “Morning mobility”). **Yoga-specific content** (large third-party pose libraries, Sanskrit names, etc.) is **deferred** to a later iteration; see **Future: yoga / expanded library** below.

- **Stretch catalog (v1 — bundled)** — Ship as **read-only JSON (or equivalent) in the APK** — a curated list of general stretches, not dependent on external yoga APIs. Each entry has at least a **stable `id`** (do not use array index as the key) and fields such as **name**, **procedure** or cues, optional **target body parts** for filtering, and optional **image** (local asset or bundled file). The UI should read as **Stretching** / **Stretches**, not “yoga,” until the yoga pass ships.
- **Routines** — The user **creates routines**: each has a **name** plus an **ordered list of stretch IDs** from the catalog. Examples: **Pre-workout**, **Post-workout**, **Pre-run**, **Pre-swim**, **Pre-bike**, or any custom label. No fixed enum of routine types. **d-tag**: `erv/stretching/routines`. **Content**: e.g. `{ "routines": [ { "id": "uuid", "name": "Pre-workout", "stretchIds": [ "stretchId1", "stretchId2" ] }, { "id": "uuid2", "name": "Pre-run", "stretchIds": [ "stretchId3", "stretchId4" ] } ] }`. User can add, edit, reorder, and delete routines.
- **Custom / synced stretches (optional)** — If users can add **custom** stretches that must sync across devices, store them in a replaceable event **d-tag**: `erv/stretching/poses`. If all stretches remain app-shipped only, this event can be omitted.
- **Session log** — **d-tag**: `erv/stretching/<date>`. **Content**: e.g. `{ "sessions": [ { "routineId": "uuid" | null, "stretchIds": [ "id1", "id2" ], "totalMinutes": 10 } ] }` — each session references a routine by id or is ad hoc (`routineId` null + `stretchIds`). Used for goals and history.
- **Guided routine player (differentiator)** — When a routine is selected, the user starts a **guided flow** with **play**. The app steps through each stretch in order: **(1)** Show the current stretch (name, image, and/or instructions) with a **large countdown** for hold time (e.g. 30 seconds; optional per-stretch or default for the routine). **(2)** At **zero**, play an **audible cue** (tone/chime). **(3)** **~5 seconds** transition: “Next: [name]” / get ready. **(4)** Next stretch; repeat until done. **v1:** static image or text-only is fine; no Lottie.
- **Groundwork for future: Lottie animations** — **Lottie is not in v1.** Use a **single “stretch visual” slot** in the guided player and stretch-detail UI (static image now; optional Lottie later). Resolve via an optional **animation asset key** (repository returns `null` until assets exist). Keep a stable **stretch id** for lookups. Document a future mapping stretch id → Lottie asset name (e.g. under `res/raw/` or `assets/lottie/`). No `lottie-compose` in the first stretching release.
- **Future (not in initial build): Lottie animations** — Same approach as above: **[LottieFiles](https://lottiefiles.com)** + **lottie-compose** when implemented; static mapping (stretch id → asset); fallback to image or text. Confirm LottieFiles license when used.
- **Future: yoga / expanded library** — Optional later upgrade: import or sync a larger **yoga** pose dataset (e.g. **[LunaticPrakash/yoga-api](https://github.com/LunaticPrakash/yoga-api)** `yoga-api.json` with `english_name`, `sanskrit_name`, `procedure`, `target_body_parts`, benefits, contraindications, `image_url`, `yt_videos`; optional **[rebeccaestes/yoga_api](https://github.com/rebeccaestes/yoga_api)** for SVG-style icons where names match). **Confirm licenses** for any third-party dataset before shipping. New or merged entries should still use **stable IDs** so existing routines remain valid; consider namespacing (e.g. `builtin:` vs `yoga:`) if both bundled stretches and yoga poses coexist.

**Cardio** — **d-tag**: `erv/cardio/<date>` or `erv/cardio/<uuid>` (see below for when to use which). **Content**: sessions with duration, **activity type**, optional **estimatedKcal**, optional **GPS track**, and optional **HR/HR zones** when a monitor is connected (see below). **Event shape choice:** Use **one replaceable event per day** with a `sessions` array, `erv/cardio/<date>`, for simplicity; or **one event per session** with `erv/cardio/<uuid>` when the app must support multiple distinct sessions per day. Document the chosen approach in code and keep §2.3 in sync. Build after Light therapy and Supplements.

- **Heart rate per activity** — When a **heart rate monitor** (e.g. **smart watch** or **external HR monitor**) is **connected to the application**, the app **tracks heart rate per activity**: each cardio session can record HR for that session (e.g. min, max, average, or time-series samples). Store HR with the session’s encrypted payload so it is associated with that activity only. If no monitor is connected, the session is logged without HR. **Heart rate zone analysis** — When HR is recorded (especially **time-series samples** during the session), the app should compute a **breakdown of how much of the activity was in each heart rate zone**. Use the standard **five zones** (as a percentage of the user's max heart rate): **Zone 1** (50–60% max HR) warm-up/recovery, **Zone 2** (60–70%) aerobic/endurance, **Zone 3** (70–80%) tempo/moderate, **Zone 4** (80–90%) high intensity, **Zone 5** (90–100%) max effort. See e.g. [Cleveland Clinic – Exercise heart rate zones](https://health.clevelandclinic.org/exercise-heart-rate-zones-explained). **Max HR** can be estimated from age (e.g. 220 − age) or set by the user in Settings; optionally support the **Karvonen (heart rate reserve)** formula for finer zones. Store the zone breakdown (e.g. minutes or percentage per zone) in the session payload or compute on demand from HR samples; display in session detail (e.g. bar chart or list: "45% Zone 2, 30% Zone 3, 25% Zone 4"). This helps the user see intensity distribution (e.g. mostly Zone 2 for endurance vs time in Zone 4/5 for intensity). **Connection is via direct BLE and/or direct device APIs only** — no Google Play or Health Connect (§8). **(1) BLE Heart Rate Profile** — Use Android's built-in **Bluetooth LE** APIs (AOSP) to connect to monitors that support the standard **Heart Rate Profile** (service 0x180D, characteristic 0x2A37); works on any Android with BLE, including GrapheneOS. **(2) Pebble** — For Pebble Time 2, use **libpebble3** to receive HR directly from the watch (AppMessage or HealthService). See §12. Health Connect is not used so the app remains usable without Google services.
- **Activity types** — Activity types are **not fixed**: provide a **built-in list** drawn from activities we can accurately estimate (see table below): e.g. walking, running, sprint, hiking, swimming, rucking, bike, rowing, **elliptical**, **stair treadmill**, **rope skipping**, **stationary bike/rowing**, **HIIT**, **ice skating**, **cross-country/downhill skiing**, **snowshoeing**, **kayaking**, **basketball**, **soccer**, **tennis**, **racquetball**, **squash**, **boxing**, **martial arts**, **hockey**, **roller skating**, and other Compendium-backed types — and **allow the user to add custom types** (e.g. elliptical, stair climber, dance). Each activity burns calories at different rates; the app computes a **rough calorie estimate** per session where possible (see Calorie estimation below). **For custom (user-added) activities** we typically do not have a known MET value in the Compendium, so **calorie expenditure may not be calculable accurately** — either skip the estimate for custom types, use a generic default MET (e.g. moderate 5), or let the user optionally assign a MET or intensity when defining the custom type; document the chosen behavior in implementation.
- **Calorie estimation (open source formulas)** — Use **MET (Metabolic Equivalent of Task)** for most activities: **Calories ≈ MET × body weight (kg) × duration (hours)**. Body weight is always used in **kg** in the formula; the user may track in **lb or kg** (default lb), so convert from lb to kg when needed (1 lb ≈ 0.453592 kg). **Body weight** comes from the optional **body weight diary** (see Body weight diary in this section)—entries are stored in kg—or from Settings fallback (value + unit; convert to kg if unit is lb). If the user does not use the diary, allow a simple **fallback** in Settings (single weight value and unit) or skip estimate and prompt. One MET = resting metabolic rate (~1 kcal/kg/hour). MET values come from the **[Compendium of Physical Activities](https://pacompendium.com/)** (free for research and commercial use; cite the compendium). Examples: walking 2.5–4 MET (pace-dependent), running ~6–16 MET (speed-dependent), hiking ~6.5 MET, swimming laps ~4.5–13+ MET (stroke/speed). **Walk/run:** [ExRx.net Walk/Run MET Calculator](https://exrx.net/Calculators/WalkRunMETs) uses ACSM guidelines (speed, grade, weight, duration); logic can be ported or approximated with Compendium METs. **Sprint sessions:** use high MET (e.g. 12–16) or model as intervals if session structure is logged. **Rucking:** when the user logs load (kg) and optional terrain/speed, use the **Pandolf equation** for load carriage: metabolic rate (Watts) from body mass, load, speed, grade, and terrain factor (η); convert to kcal/hr (Watts × 0.8604). Pandolf is well-validated for military load carriage; calculators often apply a ~1.15–1.25× correction. If load is unknown, approximate with hiking MET. Store **estimated calories** in the session payload (e.g. `estimatedKcal`) so goals or dashboard can show weekly/monthly totals; treat as approximate and document that individual variation applies. **Custom activity types** (user-added) do not have Compendium MET mappings, so for those sessions either do not set `estimatedKcal`, use a conservative default MET, or use a user-assigned MET if the app allows it when defining the custom type.

**Activities we can accurately calculate (same resources)** — The [Compendium of Physical Activities](https://pacompendium.com/) (2024 Adult Compendium) and Pandolf (rucking) support accurate calorie estimation for many more cardio activities than the minimal built-in list. Prefer expanding the **built-in list** with Compendium-backed types so users get accurate estimates without adding custom entries. The following all have MET values or a dedicated formula in the resources already cited:

| Source | Activities (examples; MET or formula in Compendium / Pandolf) |
|--------|----------------------------------------------------------------|
| **Walking, running** | Compendium Walking (17), Running (12); ExRx/ACSM for walk/run with speed/grade. |
| **Hiking** | ~6.5 MET (Compendium). |
| **Swimming** | Compendium Water (18): laps by stroke/speed (freestyle 5.8–9.8, backstroke 4.8–9.5, breaststroke 5.3–10.3, butterfly 13.8), water jogging (2.5–9.8), water aerobics (3.8–7.5). |
| **Rucking** | **Pandolf** when load/speed/grade known; else hiking MET. |
| **Bicycling** | Compendium Bicycling (01): road 3.5–16.8 MET by speed, mountain, **stationary** (3.5–16.3 by watts), e-bike, spin/RPM class. |
| **Rowing** | Compendium: **stationary ergometer** (5–14 MET by watts); Water: crew/sculling (12–15.5). |
| **Conditioning (indoor)** | Compendium Conditioning (02): **elliptical** (5–9 MET), **stair treadmill** (9.3), **rope skipping** (11), ski machine (6.8), ski ergometer (10.5–18), **rowing stationary** (5–14), **HIIT** (7–11 MET), aerobic/step (5.5–9), Zumba (5.5–6.5). |
| **Winter** | Compendium Winter (19): **ice skating** (5.5–13.8), **cross-country skiing** (6.8–16), **downhill skiing / snowboarding** (4.3–8), **snowshoeing** (5.3–10), rollerskiing. |
| **Water (other)** | Kayaking (5–13.5), canoeing, stand-up paddle (2.8–11). |
| **Sports (cardio)** | Compendium Sports (15): basketball (6–9.3), soccer (7–9.5), tennis (4.5–8), racquetball (7–10.3), squash (7.3–12), boxing (5.8–12.3), martial arts (5.3–14.3), hockey (8–10), race walking (10–15.5), roller/in-line skating (6.8–15.5). |

Implementers can map built-in activity types to Compendium codes (or MET ranges) and optionally allow intensity or speed to refine the MET used. Custom (user-added) types remain without a guaranteed MET unless the user assigns one.

- **GPS track and map** — For **run, hike, bike, ruck** (and similar outdoor activities), the user’s **phone** can **record GPS location during the activity** when they have the device with them (the Pebble watch does not have GPS; see §12). The app records the track (e.g. list of lat/lon + timestamp) for the session and stores it with the session payload. **Payload size:** long activities can produce large tracks; consider **downsampling or thinning** points for Nostr sync (e.g. keep full resolution locally; sync a simplified track to stay within event size limits) or store track in a separate event if needed; define during Cardio implementation. **After the workout is complete**, the app provides a **visual overlay** of the track on an **OSM (OpenStreetMap) tile-based** map (e.g. in session detail or activity history). Use an open source Android map library that supports OSM tiles and polylines, e.g. **[OsmDroid](https://github.com/osmdroid/osmdroid)** (offline-friendly, no API key) or **[MapLibre Android](https://maplibre.org/)** (vector tiles, styling). Include **OSM attribution** as required. **Sharing / privacy:** For social relay posts, also support a **track-only** export (polyline on a neutral background, **no** map tiles) so approximate location is not revealed — see **[CARDIO_GPS_PLAN.md](CARDIO_GPS_PLAN.md)**. **Bike speed / distance via Bluetooth:** For **cycling**, optional **BLE** accessories (e.g. wheel magnet **speed sensors** such as Garmin / other brands) that implement the standard **Cycling Speed and Cadence (CSC)** profile can supply **speed and distance** without GPS (direct GATT from the app, same philosophy as BLE HR — §8). Integrate alongside GPS per **[CARDIO_GPS_PLAN.md](CARDIO_GPS_PLAN.md)**. Swimming is typically not GPS-tracked in water; other activities can optionally record a track when the user starts a “tracked” session. **User control:** The **GPS track** option must be **explicitly shown** to the user: **(1)** **Per activity** — the user can **turn off** GPS tracking for **any given activity** (e.g. a toggle when starting or editing a session). **(2)** **App-wide** — the user can **disable GPS tracking for the application as a whole** (e.g. in Settings), so no track is recorded unless they re-enable it. Default can be off or on depending on product choice; ensure the user can opt out at both levels.

**Workout (weight training)** — Prioritize the **four main compound lifts**: **Bench press**, **Deadlift**, **Squat**, **Military press**. User can add any other exercise; each exercise is assigned a **muscle group** and **push or pull** so workouts can be arranged by **muscle group** or **push/pull days**. Primary equipment: **kettlebell**, **dumbbell**, **free weight (barbell)**; secondary: **machine / targeted** work.

- **Exercise list (master)** — One replaceable event so the list syncs. **d-tag**: `erv/weight/exercises`. **Content**: e.g. `{ "exercises": [ { "id": "uuid", "name": "Bench Press", "muscleGroup": "chest" | "back" | "legs" | "shoulders" | "biceps" | "triceps" | "core" | custom, "pushOrPull": "push" | "pull", "equipment": "barbell" | "dumbbell" | "kettlebell" | "machine" | "other" } ] }`. **Built-in / default** list includes the four compounds (Bench press, Deadlift, Squat, Military press) with suggested muscle group and push/pull; user can add custom exercises with the same fields. Filtering and workout templates by muscle group or push/pull use this list.
- **Routines (master)** — **d-tag**: `erv/weight/routines`. **Content**: e.g. `{ "routines": [ { "id": "uuid", "name": "Push A", "exerciseIds": [ "uuid", ... ], "notes": null } ] }`. User starts a live session from a routine (full product behavior in app; see [WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md)).
- **Workout log (daily)** — **d-tag**: `erv/weight/<date>` — one replaceable event per calendar day (multiple sessions that day live inside the payload). **Content**: e.g. `{ "date": "YYYY-MM-DD", "workouts": [ { "id": "uuid", "source": "LIVE" | "MANUAL" | "IMPORTED", "startedAtEpochSeconds?", "finishedAtEpochSeconds?", "durationSeconds?", "routineId?", "routineName?", "entries": [ { "exerciseId": "uuid", "sets": [ { "reps": 8, "weightKg": 60, "rpe": null } ] } ] } ] }`. **Field names in shipped app** match Kotlin `WeightDayLog` / `WeightWorkoutSession` in [WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md). Analytics derive from `entries` + exercise list; per-exercise **in-app history** is computed by scanning day logs (not a separate `erv/` event).
- **UI**: Log sessions with exercises (pick from list or quick-add), sets/reps/weight. Organize or filter workouts by **muscle group** or **push/pull** (e.g. “Push day”, “Pull day”, “Legs”). **Shipped:** Exercises tab: tap a lift → detail with chronological occurrences from stored logs. Build after Cardio; refer to Runstr for log structure ideas; keep kind 30078 and `erv/` namespace.

**Workout analytics (open source and formulas)** — For analytics on logged workouts you can use:

- **1RM (one-rep max) estimation** — Well-known formulas from submaximal sets (weight × reps). No library required: **Epley** `1RM = weight × (1 + reps/30)` (good for 1–10 reps), **Brzycki** `1RM = weight × (36 / (37 − reps))` (good for higher reps). [1rm.js](https://github.com/bendrucker/1rm.js) (JS, MIT) implements Epley, Brzycki, Lander, Lombardi, Mayhew, O'Conner, Wathan — easy to port to Kotlin or implement the two you need.
- **Open source apps as reference** — [OpenLift](https://openlift.app/) (workout tracker with volume, PRs, muscle distribution, progression) and [LIFT](https://github.com/parkerdgabel/lift) (Python CLI: PR detection, volume by muscle group, progressive overload) show which metrics to compute (volume over time, estimated 1RM/PRs, distribution). Use their logic as reference; implement analytics in ERV from your own `erv/weight/...` data.
- **R package "strength"** — RPE ↔ %1RM ↔ reps for planning; useful for program design, less for in-app analytics.
- **Charting** — Use an Android/Compose charting library (e.g. [Vico](https://github.com/patrykandpatrick/vico), MPAndroidChart, or Compose Canvas) to graph volume, estimated 1RM over time, or sessions per week.

No single open source “workout analytics library” in Kotlin; combine 1RM formulas (handful of lines), your own aggregation over `erv/weight` events, and a charting library for visuals.

**Body weight diary (optional)** — If the user wants to track **body weight over time** (e.g. for workout progress or general health), offer an optional **body weight diary / tracker**. Not required; the user can enable and use it when they want.

- **Units**: User can track in **lb or kg**. **Default is lb.** Store weight in **kg** in the encrypted payload so MET and Pandolf formulas (which expect kg) work without ambiguity; the UI displays and accepts input in the user's chosen unit (Settings: **Body weight unit** = lb or kg, default lb) and converts to/from kg on save and display (1 lb = 0.453592 kg).
- **d-tag**: `erv/bodyweight` (one replaceable event so the diary syncs across devices).
- **Content (encrypted JSON)**: e.g. `{ "entries": [ { "date": "YYYY-MM-DD", "weightKg": 75.5 }, ... ] }` — list of date + weight stored in **kg**, sorted by date. (Display and input use the user's unit from Settings.) **Payload growth:** daily logging for 2+ years means 730+ entries (~36 KB JSON); within most relay limits but monitor size over time. Optionally cap, prune old entries, or shard by year (e.g. `erv/bodyweight/2026`, `erv/bodyweight/2027`) if the single event grows too large for reliable sync.
- **UI**: User can **log weight** (e.g. daily or when they choose) in lb or kg (per Settings); view **history** (list by date) and optionally a **simple trend** (e.g. line chart over time). Access from Settings, a “Profile” area, or a small “Body weight” entry point; need not be a full dashboard tile unless desired.
- **Use elsewhere**: **Cardio calorie estimation** (MET × weight_kg × time) and **Rucking** (Pandolf) use body mass in **kg**; use the latest diary entry or the entry for the session date (already stored in kg), or fall back to the single weight in Settings (convert to kg if user's unit is lb). If the user does not use the diary, fall back to Settings or skip estimate.

**Sauna** — Track **sessions** with **duration** and optional **temperature**. Same pattern as Light therapy: built-in **session timer** (themed, user-settable duration), **audible “time’s up”** at end, **auto-log** when timer completes.

- **d-tag**: `erv/sauna/<date>` (one replaceable event per day).
- **Content (encrypted JSON)**: e.g. `{ "sessions": [ { "minutes": 15, "tempF": 180 }, { "minutes": 20, "tempC": 82 } ] }` — each session has duration (minutes) and optional temperature. Store **either** °F or °C per session (or both); let user choose unit in Settings or per-session. Optional: `routineId` and `roundIndex` when the session was part of a contrast routine (see Heat/cold routines in this section).
- **Session timer** — User sets **duration** (e.g. 5, 10, 15, 20, 30 minutes or custom) and optional **temperature** (for display/log only; no device control). Timer runs (countdown or elapsed); at **time’s up**, play **audible notification** and **auto-log** the session (merge into day’s `erv/sauna/<date>`). Same UX pattern as Light therapy (above): “Session in progress” with countdown, stop/cancel.
- **Typical protocols (reference)** — Common practice: **15–20 minutes** at **175–185°F (80–85°C)** for most users; beginners 5–10 min, experienced up to 20–30 min. Higher temps usually mean shorter duration. Multiple rounds (e.g. 15 min heat, 5 min cool, repeat) are supported via **contrast routines** below.

**Cold plunge** — Same structure as Sauna: **sessions** with **duration** and optional **temperature**, **session timer**, **audible at end**, **auto-log**.

- **d-tag**: `erv/cold/<date>` (one replaceable event per day).
- **Content (encrypted JSON)**: e.g. `{ "sessions": [ { "minutes": 3, "tempF": 52 }, { "minutes": 1.5, "tempC": 11 } ] }` — duration (minutes; allow fractional e.g. 0.5 for 30 sec) and optional temperature (°F or °C). Optional: `routineId` and `roundIndex` when part of a contrast routine.
- **Session timer** — User sets **duration** (e.g. 30 sec, 1, 2, 3, 5 minutes or custom) and optional **temperature**. Timer, audible, auto-log; merge into `erv/cold/<date>`.
- **Typical protocols (reference)** — Standalone cold: often **2–5 minutes** at **45–55°F (7–13°C)**. In **contrast therapy** (alternating with sauna), cold phases are often **30–90 seconds** to **2–3 minutes**; finishing on cold is common for recovery.

**Heat/cold routines (contrast therapy)** — Many users alternate **sauna → cold plunge → sauna → …** for several rounds (contrast therapy / hot-cold cycling). Support **user-defined routines** so they can run a guided sequence without re-entering times each time.

- **Routines** — **d-tag**: `erv/heatcold/routines`. **Content**: e.g. `{ "routines": [ { "id": "uuid", "name": "3 rounds contrast", "steps": [ { "type": "sauna", "durationMinutes": 15, "tempF": 180 }, { "type": "cold", "durationMinutes": 3, "tempF": 52 }, { "type": "sauna", "durationMinutes": 15, "tempF": 180 }, { "type": "cold", "durationMinutes": 3, "tempF": 52 }, { "type": "sauna", "durationMinutes": 15, "tempF": 180 }, { "type": "cold", "durationMinutes": 3, "tempF": 52 } ] } ] }`. Each **step** is either `sauna` or `cold` with **duration** and optional **temperature**. User can create **sauna-only** (one step), **cold-only** (one step), or **contrast** (alternating steps). Add, edit, reorder, delete routines.
- **Guided routine player** — When the user starts a routine (e.g. “3 rounds contrast”), the app runs each step in order: **(1)** Show current step (e.g. “Sauna — 15 min @ 180°F”) with **countdown timer**. **(2)** At **zero**, play **audible** cue, then short transition (e.g. “Next: Cold plunge — 3 min”, 5–10 s). **(3)** Start timer for next step; repeat. When the routine finishes, **log each step** to its category: each sauna step → one entry in `erv/sauna/<date>`, each cold step → one entry in `erv/cold/<date>`, so **goals** (sauna 4x/week, cold 3x/week) count correctly. Optionally tag entries with `routineId` and `roundIndex` so history can show “Part of: 3 rounds contrast.”
- **Reference** — Common contrast protocols: **3–6 cycles**; hot 3–5 min (or 10–15 min sauna), cold 30–90 sec (or 2–3 min); **finish on cold** for recovery. Beginner: 2–3 min hot / 30 s cold, 3–4 cycles; intermediate: 3–4 min hot / 45–60 s cold, 4–6 cycles.

**Sleep** — Log **sleep window** (bedtime and wake time, or duration) and optional **quality** and **notes**. Used for goals (e.g. “at least 7 hrs per night”, §7.1) and history.

- **d-tag**: `erv/sleep/<date>` (one replaceable event per day; one sleep period per night).
- **Content (encrypted JSON)**: e.g. `{ "window": { "start": "23:00", "end": "06:15" }, "durationHours": 7.25, "quality": "good" | "ok" | "poor" | 1-5 | null, "notes": "optional text", "source": "manual" | "pebble" | "manual+pebble", "deviceMetrics": { "durationMinutes": 435, "startTime": "ISO", "endTime": "ISO" } }`. **window**: start/end as time-of-day (HH:mm) or ISO if needed; **durationHours**: derived or user-entered; **quality**: optional simple scale; **notes**: free text. **source**: whether the entry came from manual input, a connected device (e.g. Pebble), or both. **deviceMetrics** (optional): when a Pebble or compatible watch is connected, store sleep data from the device (duration, start/end if available) so it can be shown and used for goals; user can still add or override quality and notes.
- **UI**: User enters **bedtime** and **wake time** (or duration only); optional **quality** (e.g. good/ok/poor or 1–5); optional **notes**. List by date; goal progress = “met min hours” per night or per week. No timer (sleep is logged after the fact).
- **Pebble (or compatible device) sleep tracking** — When a **Pebble** is connected to the app (§12), ERV should **show and track sleep metrics** from the watch. libpebble3 **HealthService** provides sleep data (datalogging tag 83); ERV can call `requestHealthData()` and read sleep records for the relevant night, then **populate or enrich** the Sleep log for that date: e.g. pre-fill **duration** and **window** (start/end) from the device, and optionally display device-derived metrics (total sleep, consistency) in the Sleep silo. User can keep manual entry as primary or use device data as primary and add quality/notes. Store device-derived sleep in the same `erv/sleep/<date>` payload (e.g. `source: "pebble"` or `"manual+pebble"`, and `deviceMetrics`) so goals and history use a single sleep record per night. Implement as part of Pebble integration (Phase 10b or when Sleep silo is built); same direct-device approach as HR — no Health Connect.

### 2.5 Avoiding conflicts with Runstr and others

- **Runstr** uses kind **1301** for workout records. ERV uses **30078** with `erv/...` d-tags, so there is no kind or identifier overlap.
- Other apps using 30078 are distinguished by their d-tag prefix (e.g. `fiatlife/`, `erv/`). No coordination needed beyond choosing a unique prefix.

### 2.6 Amber and remote signers

- Amber (and NIP-46 signers) can restrict which **kinds** an app may sign. Kind **30078** is a standard application-data kind and should be allowed; confirm during implementation.
- All ERV events use the same kind; only the `d` tag and encrypted content differ. No “custom” or rarely used kinds, which keeps remote signer compatibility.

---

## 3. NIP-42 authentication

- Relays that require auth will send `AUTH` with a challenge. The client must respond with a signed **kind 22242** ephemeral event (relay + challenge tags), then retry the failed `REQ`/`EVENT`.
- Implementation approach (aligned with FiatLife):
  - On connect, listen for `AUTH` and store the challenge.
  - On `OK(..., false, "auth-required: ...")` or `CLOSED(..., "auth-required: ...")`, sign the NIP-42 auth event (with local nsec or via Amber), send `AUTH` with that event, then resend the original request.
- Use a single WebSocket connection (or connection pool) per relay and reuse it for all ERV subscriptions and publishes so NIP-42 is done once per connection.

---

## 4. NIP-44 encryption

- **Algorithm**: NIP-44 v2 (ChaCha20, HKDF, etc.). Use a well-tested library (e.g. from [paulmillr/nip44](https://github.com/paulmillr/nip44)).
- **Usage in ERV**: Encrypt event **content** (JSON string) to the **user’s own pubkey** so that:
  - Only the user can decrypt it on another device or from the relay.
  - Relays and other users only see ciphertext.
- Key handling:
  - **Local nsec**: Derive shared secret with your own pubkey, encrypt/decrypt on device.
  - **Amber**: Signing is remote; decryption still needs the same key. Either use a local key derived from the same identity or follow FiatLife’s pattern for “encrypt to self” with an Amber-backed identity (e.g. request decryption key material only when needed, or use a local cache per session). Exact flow should mirror FiatLife’s NIP-44 + Amber integration.

---

## 5. Application architecture (high level)

### 5.0 Language and UI stack

**Recommendation: native Kotlin + Jetpack Compose** for Android. This gives the best UI expression and the strongest handling of differing datatypes on Android, without committing to a cross-platform framework you may not need.

| Concern | Why Kotlin + Compose fits |
|--------|---------------------------|
| **Interesting UI expression** | Compose is declarative, composable, and supports custom layouts, animations, and theming. You can build a distinct sun-themed dashboard and per-category screens without fighting the framework. Material 3 and Compose make it straightforward to express “many entry points, each with its own data shape” in one place. |
| **Differing datatypes** | Kotlin’s type system is a good fit for many category silos with different shapes: **sealed classes** for “one of N category log types,” **data classes** per category (e.g. `SleepLog`, `WeightLog`, `SupplementLog`), and **generics** for shared sync/encryption over a common `erv/` event envelope. Serialization (e.g. kotlinx.serialization) maps cleanly to the encrypted JSON you store in Nostr events. You get compile-time safety and clear models per category. |
| **Native Android** | First-class tooling, Room, DataStore, Hilt, and Coroutines all integrate cleanly. FiatLife (your reference) uses this stack, so patterns and libraries are proven. |

**More “flexible” options (if you value cross-platform or one codebase):**

- **Kotlin Multiplatform (KMP)** — Shared Kotlin for Nostr client, NIP-44, models, sync, and repository logic; Android UI in Compose, iOS UI in SwiftUI. Same types and business logic on both platforms; only UI is written twice. Best choice if you want to keep Kotlin and share the complex parts (crypto, relay, datatypes) when you add iOS.
- **Flutter (Dart)** — One codebase for Android and iOS, expressive UI (widgets, custom paint). Strong typing. You’d leave the Kotlin/Android-native ecosystem and maintain a Dart codebase instead.
- **React Native + TypeScript** — One codebase, flexible UI via React. Runstr uses this. TypeScript handles differing datatypes well. You’d be in the JS/TS ecosystem rather than Kotlin.

**Summary:** For an Android-only app with rich UI and many differing datatypes, **Kotlin + Compose** is the strongest fit. If you want to share logic (and possibly UI later) with a future iOS app while staying in Kotlin, **KMP** is the path that aligns with your current design.

### 5.1 Layers

- **UI**: Jetpack Compose, single Activity, Navigation component. Main screen = dashboard with entry points to each category.
- **Domain**: Use cases / business rules (e.g. “log sleep”, “sync category to relay”). One silo per category in code as well.
- **Data**:
  - **Local**: Room DB + DataStore (relay URL, auth state, etc.). Local DB is source of truth; sync pushes to Nostr and pulls from Nostr on app open or refresh.
  - **Remote**: Nostr client (WebSocket), NIP-42 handler, NIP-44 encrypt/decrypt, publish and subscribe for kind 30078 with `d` prefix `erv/`.
- **Service layer (active sessions)**: Cardio and Workout sessions are **time-bound**: the user starts a session, may leave the app (music, messages, etc.), and returns. Session state (elapsed time, current exercise, set progress, rest timer) **must survive** the app being backgrounded or the Activity being destroyed. Use an Android **Foreground Service** to own the active session lifecycle: the service holds the timer, session state, and exposes it via a bound interface (e.g. `StateFlow`) to both the main Compose UI and, in the future, a **Bubble activity** (§13). The ViewModel observes the service; the service is the source of truth while a session is active. When no session is active, the service stops. This architecture is required for the **Workout Bubble** (§13) and must be in place when Cardio (Phase 7) and Workout (Phase 8) are built — not retrofitted later.

### 5.2 Data flow (sync)

- **Write path**: User logs data in a category → save to Room → enqueue “publish” job → build event (kind 30078, d-tag, NIP-44 encrypted JSON) → sign (local or Amber) → send to relay (after NIP-42 if needed).
- **Read path**: On launch or refresh → NIP-42 if required → subscribe to kind 30078 with `#d` filter prefix `erv/` (or per-category `erv/<category>/`) → receive events → decrypt with user key → merge into Room (by d-tag, keep latest).
- **Conflict**: Replaceable semantics: same (kind, pubkey, d) → keep latest `created_at`; no need for multi-device conflict resolution beyond that.

### 5.3 Relay routing and efficiency

- **Single primary relay**: Most users will have one personal relay; one WebSocket, one NIP-42 flow, one subscription for `kind 30078` and `d` starting with `erv/`.
- **Multiple relays (optional)**: If supporting multiple relays later, either:
  - Subscribe to the same filters on each relay and merge by event id / (kind, pubkey, d), or
  - Publish to all configured relays and subscribe from all; deduplicate by event id.
- **Filtering**: Use minimal filters to reduce load: e.g. `{"kinds": [30078], "#d": ["erv/"]}` or per-category `#d` prefixes. Avoid broad time ranges if the relay supports limit; use incremental sync by `since` if needed.
- **Batching**: Prefer batching many events in one subscription rather than one REQ per event. One REQ for all ERV data (or per category) is enough.

---

## 6. Authentication and signer flows

### 6.1 Local nsec

- User pastes or imports nsec (or generates in-app). Store key material in Android Keystore (or equivalent) and use it for:
  - Signing NIP-42 auth events.
  - Signing ERV events (kind 30078).
  - NIP-44 encrypt/decrypt (encrypt-to-self).

### 6.2 Amber (or other NIP-46 remote signer)

- Reuse the same flow as in **FiatLife**: connect to Amber, request permission to sign the kinds used by ERV (e.g. 22242 for auth, 30078 for data). No need to re-design; copy patterns from FiatLife and adapt to ERV’s event shapes.
- Ensure the app requests only the minimal kinds needed: **22242** (NIP-42 auth) and **30078** (ERV data). No custom kinds.

---

## 7. UI/UX and theming

### 7.1 Goals: data model and types

Users can set **goals** for any of the eight categories; goals are optional (no need to set one for every section). Goals drive progress feedback on the main screen and within each silo.

**Storage:** One replaceable event, d-tag **`erv/goals`**. Encrypted JSON contains a map keyed by category; categories with no goal are omitted or `null`. Same kind (30078), NIP-44 encrypted, syncs like other ERV data.

**Goal types** (oriented around each category's inputs):

| Category        | Example goal              | Type / shape |
|-----------------|---------------------------|--------------|
| **Sleep**       | At least 7 hrs per night  | `minHoursPerNight: number` |
| **Supplements** | Take listed supplements every day | `daily: true` (or checklist if we support a fixed list) |
| **Sauna**       | 4x per week               | `timesPerWeek: { min: number, max?: number }` |
| **Cold plunge** | 3x per week               | Same as above |
| **Light therapy** | Every day or 5x/week    | `daily: true` or `timesPerWeek` |
| **Weight training** | 4-5x per week          | `timesPerWeek: { min: 4, max: 5 }` |
| **Cardio**      | 3x per week               | `timesPerWeek: { min: 3 }` |
| **Stretching**  | Daily or 5x/week          | `daily: true` or `timesPerWeek` |

Encrypted payload for `erv/goals` (conceptual): e.g. `{ "sleep": { "minHoursPerNight": 7 }, "supplements": { "daily": true }, "sauna": { "timesPerWeek": { "min": 4 } }, "weight": { "timesPerWeek": { "min": 4, "max": 5 } }, "cardio": { "timesPerWeek": { "min": 3 } } }`. Progress is computed from logged data (this week for frequency, last night for sleep, today for daily); no need to store progress in Nostr.

### 7.2 Main screen layout (dashboard + goals)

The main screen is the **dashboard**: status, goals, and entry into each category silo.

**1. Top (optional)** — **Today / This week** strip: e.g. "Today: Sleep done, Supplements done" or "This week: Sauna 2/4, Workouts 3/4." **Relay/sync** indicator (connected, last synced) secondary.

**2. Goals section (main page)** — Dedicated area on the main screen to **motivate** the user: list each set goal with progress. When a goal is **met**, show it as **completed** (e.g. bold green) with **session details in summary form** listed beneath it; details are pulled from the relevant category silo so the user sees what counted (e.g. "Sauna: 15 min, 180°F" or "Sleep: 7.2 hrs, 11p–6:12a") without overloading the main screen. When not met, show progress (e.g. "Sauna: 2/4 this week"). Tap row to open that category. Editing goals is done in the **Goals tab**, not on the main page.

**3. Goals tab** — Dedicated tab/screen where the user **sets and edits** all goals (per category, optional). Add, change, or clear goals here; main page is read-only for motivation.

**4. Category tiles** — Eight entries (Stretching, Weight Training, Cardio, Sauna, Cold Plunge, Light Therapy, Supplements, Sleep). Each opens that category's silo. Optional: small badge on tile (e.g. goal met / not met today or this week, or "logged today").

**5. Navigation** — Dashboard (main), **Goals** (tab for editing goals), **Settings** (see §7.5 for full inventory: relay, keys/login, theme, GPS, body weight unit (lb/kg) and fallback, max HR/age, temperature unit, etc.). Category content is reached from dashboard tiles or Goals section rows.

**6. Date navigation and calendar** — The main screen is **date-aware**: the user can view a specific day’s accomplishments or plan for a future day; the dashboard and goals section reflect the **selected date**, not only “today.”

- **Cycle through dates** — In the top bar (or near the date display), provide **left and right arrows** so the user can move one day into the **past** (to review what they logged) or into the **future** (to see what they want to accomplish or plan). The displayed date updates and the main screen content (goals progress, category summaries, “logged today” badges) reflects that date.
- **Week navigation** — In addition to day-by-day arrows, support **week navigation** (e.g. a second set of arrows or a “week” mode) so the user can jump **one week** backward or forward. The selected date updates by 7 days and the main screen shows that week’s context (e.g. the chosen day within that week); useful for reviewing “last week” or “next week” at a glance.
- **Calendar icon** — A **calendar icon** in the top bar of the main screen opens a **popup (or bottom sheet)** that shows a **month view** of the current month (or the month containing the selected date).
- **Month view** — In the month view, **days where the user has logged content** (any category) are indicated with a **small green checkmark** (or similar). The user can **tap a day** that has a checkmark (or any day) to **select that date**; the month view then **closes** and the main screen updates to show that date’s activities and status.
- **Back to today** — When the **selected date is not today**, show a **button** in the UI (e.g. “Today” or a home-style icon) that **returns the user to the current date** in one tap. When the selected date is already today, this button can be hidden or disabled so the UI stays clean.

This makes sense as a single, consistent pattern: one selected date drives the main screen; arrows and calendar picker change it; “Today” brings the user back to the current date. **Implementation:** selected date is app-level or screen-level state (e.g. ViewModel or DataStore); dashboard and category screens **query logs and goals for the selected date**, not only “today.” **Month view checkmarks:** compute “days where user logged content” by querying local DB across all category logs for each day in the displayed month (or maintain a lightweight per-date summary).

---

### 7.3 Theme and categories
- **Theme**: Sun-themed, muted warm palette (reds, yellows, oranges; Material 3). Keep contrast and accessibility in mind.
- **Categories**: Each category is developed separately. Goals and the main-screen Goals section can be added once at least one category exists so progress is computable.

### 7.4 User feedback (toast notifications)
Use **toast notifications** throughout the app to give users clear visual feedback:
- **On success** — When an event is published (or synced) successfully, show a short toast (e.g. “Saved and synced”) so the user knows the action completed.
- **On error** — When publish, sync, or any critical operation fails, show a longer toast with an error message so the user is notified and can retry or adjust.

Use the shared **`UserFeedback`** helper (`showSuccess(context, message?)`, `showError(context, message?)`) so behavior and copy are consistent. Call it from the UI layer (Activity/Fragment/Composable) after publish/sync results; use the default strings from resources when no custom message is needed.

### 7.5 Settings

Settings is a dedicated screen/tab (reached from main navigation; see §7.2 item 5). Store preferences in DataStore or similar; no sensitive values in plaintext. The following options are part of the plan; implement in phases as the features that use them are built.

| Setting | Purpose | Used by |
|--------|---------|--------|
| **Relay URL** | Nostr relay for sync (subscribe/publish). | Sync layer (§5.2); NIP-42 auth. |
| **Keys / login** | Choose auth: **local nsec** (paste/import or generate) or **Amber** (NIP-46 remote signer). Manage or switch account. | All publish/sign flows (§6). |
| **Theme** | App-wide theme (e.g. light / dark / system). Sun-themed palette applies (§7.3). | UI theming. |
| **GPS tracking (app-wide)** | Master switch: **on** or **off** for recording location during activities. When off, no GPS track is recorded regardless of per-activity toggles. | Cardio (§2.4): run, hike, bike, ruck track. |
| **Body weight unit** | **lb** (default) or **kg**. Affects display and input for the body weight diary and the fallback weight below. All formulas (MET, Pandolf) use kg internally; convert from lb when the user's unit is lb. | Body weight diary (§2.4); Cardio calorie estimation; Settings fallback weight. |
| **Body weight (fallback)** | **Single weight value** in the user's chosen unit (lb or kg, see Body weight unit above) when the user does not use the body weight diary. Used for Cardio calorie estimation (MET × weight × time) and rucking (Pandolf); convert to kg when unit is lb. If unset and no diary entry exists, prompt or skip estimate. Optionally: entry point to body weight diary or “use latest diary value.” | Cardio (§2.4) calorie estimation; body weight diary (§2.4). |
| **Max heart rate or age** | For **HR zone analysis** when a monitor is connected: either **max HR (bpm)** or **age** (used to estimate max HR e.g. 220 − age). Enables five-zone breakdown (50–60%, 60–70%, … 90–100% of max) in session detail. | Cardio (and optionally Workout) when BLE/Pebble HR is used (§2.4, Phase 10). |
| **Temperature unit** | **°F** or **°C** for Sauna and Cold plunge (and heat/cold routines). Default per locale or user choice. | §2.4 Sauna, Cold plunge, heat/cold routines. |
| **Music bar** | Show a minimal "now playing" bar to control whatever media is currently playing (music or podcasts from another app). When **on**, the bar appears (e.g. at bottom of screen); when **off**, it is hidden. Requires **notification access** when enabled (see §7.6). | §7.6 Music bar. |
| **Live workout bubble** | **On** by default (`UserPreferences.workoutBubbleEnabled`). When **on** (Android 11+ / API 30+): during an **active live weight workout**, ERV may show a **bubble** (dynamic shortcut + `BubbleMetadata`) after the user leaves the workout screen or another app (see §13). When **off**: **bubble only is disabled**; a **persistent ongoing notification** still appears while the workout runs—**required** for **`WeightLiveWorkoutForegroundService`** (`foregroundServiceType="health"`). ERV cannot hide that notification without ending the workout; the user may use **system app notification settings** to adjust behavior. Settings → **Live weight workout** spells this out (see [WEIGHT_TRAINING_SPEC.md](WEIGHT_TRAINING_SPEC.md)). | `WeightLiveWorkoutForegroundService`, `WeightWorkoutBubbleActivity`. |

**Other preferences** (add as needed): e.g. default timer durations, notification sounds, optional multi-relay list (future). Keep the list in this section so new options are documented and the gaps table (§9.1) does not duplicate resolved items.

### 7.6 Music bar (optional, minimal)

A **minimal media control bar** lets the user control music or podcasts from within ERV without switching apps—useful during workouts, cardio, or timers. The bar must stay **minimal** so it does not distract from the app’s primary purpose (health logging and goals).

- **Behavior**: Use Android’s **MediaSession** / **MediaController** to bind to the **active media session** from any app (e.g. Finamp, AntennaPod, Spotify, or any player that implements MediaSession). The bar shows **current track/podcast** (title, optional artwork) and **play/pause** and **skip** (next/previous). Tap the bar to open the source app if the user wants to browse or change content.
- **Placement**: **Bottom of screen** (or top, consistent across the app), **single compact row**: e.g. small artwork or icon + truncated title + play/pause + skip. Do not expand into a full player; no browsing or queue inside ERV.
- **Visibility**: **User-controlled** via Settings (§7.5): **Music bar** on/off. When **off**, the bar is hidden everywhere. When **on**, it appears on the main screen (and optionally on category screens) only when there is an **active media session**; when nothing is playing, the bar is hidden or shows a minimal “Nothing playing” state that does not occupy much space.
- **Permission**: Accessing other apps’ MediaSessions requires a **NotificationListenerService** and the user granting **notification access** (Settings → ERV → Notifications). Request this only when the user **enables** the music bar; explain in UI that it is used solely to show and control the current track. If the user revokes notification access, hide the bar and show an optional “Re-enable in Settings” hint.
- **Scope**: One implementation works for **all** MediaSession-based apps (Finamp, AntennaPod, Spotify, YouTube Music, etc.). No app-specific integration or ERV-stored media data; no Nostr events. Implementation can follow Phase 4 (dashboard layout reserves space) with the actual MediaSession/NotificationListener logic in a later phase (e.g. Phase 11).

---

## 8. Privacy and security

- **On device**: Nostr private key or Amber session in secure storage (Android Keystore / encrypted preferences). Minimize logging of keys or plaintext health data.
- **On wire**: NIP-44 only; no plaintext health data in event content. NIP-42 so the relay sees an authenticated client, not necessarily plaintext.
- **On relay**: Only ciphertext and metadata (kind, pubkey, d-tag, created_at) are visible; content is encrypted to the user.
- **Relay choice**: User brings their own relay; ERV does not require a central server. Prefer relays that support NIP-42 and retention policies the user is comfortable with.
- **No Google Play dependency**: ERV **does not depend on Google Play or Health Connect** for any core feature. The app must work on **GrapheneOS** and other Android setups without Google services. **Heart rate and other device data** are obtained via **direct BLE** (Android’s built-in Bluetooth LE APIs; standard **Heart Rate Profile** for compatible monitors) or **direct device integration** (e.g. libpebble3 for Pebble). No health data flows through Google; the user can use ERV privately without sharing data with the developer or Google.

---

## 9. Implementation phases (suggested)

Before or during implementation, resolve the **gaps and items** listed in **§9.1** so the plan and code stay aligned.

> **Rationale for phase ordering:** Phases are sequenced so that (a) each phase produces a testable, working increment, (b) dependencies flow forward (infrastructure before features, simpler silos before complex ones, goals right after the dashboard so every subsequent silo gets goal integration for free), and (c) cross-cutting concerns like hardening come last when all features exist. Cardio defines HR fields in its data model but defers BLE connection to Phase 10 so the core activity silo is independently testable.

1. **Scaffold and docs**  
   - Android project (Kotlin, Compose, Gradle), README, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, LICENSE (MIT).  
   - GitHub Action: build and test on push (e.g. assembleDebug, unit tests).  
   - Add **UserFeedback** toast helper (§7.4) for publish/sync success and error so it is available in later phases.  
   - This repo is already set up for the basic scaffold.

2. **Core Nostr and auth**  
   - Nostr WebSocket client, NIP-42 auth, NIP-44 encrypt/decrypt (to self).  
   - Local nsec login and secure storage.  
   - No categories yet; optional: publish one test kind-30078 event with d-tag `erv/test/...`.

3. **Amber (remote signer)**  
   - Integrate NIP-46 and Amber login (reuse FiatLife patterns).  
   - Sign NIP-42 and kind-30078 events via Amber.  
   - Verify kind 30078 is allowed by Amber.

4. **Dashboard, theme, and date navigation**  
   - Main screen with placeholders for all eight categories; sun-themed palette (§7.3).  
   - **Date navigation and calendar** (§7.2): **arrows** to cycle day/week (past/future); **calendar icon** in top bar opening **month view** with **green checkmarks** on days where the user logged content; tap a day to select it and show that date activities; when not on today, show **Back to today** button. Main screen is date-aware (selected date drives displayed data).  
   - Navigation shell: category tiles (all placeholders or “Coming soon” for now); Goals and Settings tabs/screens as per §7.2.  
   - **Settings page** (§7.5): Dedicated Settings screen (reached from main navigation). Store preferences in DataStore or similar; no sensitive values in plaintext. In this phase, implement the **screen structure** and the options needed for the shell: **Relay URL** (Nostr relay for sync), **Keys / login** (local nsec vs Amber; manage or switch account), **Theme** (light / dark / system; sun-themed palette applies). Add remaining options (GPS, body weight unit/fallback, max HR/age, temperature unit, music bar, etc.) in later phases as the features that use them are built (§7.5).  
   - Establishes the app shell so the first categories (Phase 5) are built into a consistent theme and navigation from day one.

5. **Local data and first categories: Light therapy + Supplements**  
   - Room DB, DataStore, repository layer.  
   - **Light therapy**: End-to-end (UI to Room to Nostr). **Session timer** (§2.4): themed (sun palette), user-settable duration, **audible time-up** when session ends, **auto-log the event** (add session to day log and persist/sync) when timer completes. `erv/light/<date>`, content e.g. `{ "sessions": [ { "minutes": 20 } ] }`.  
   - **Supplements**: End-to-end. **Master list** `erv/supplements/list` (what user takes: name, dosage, frequency, when to take); **daily log** `erv/supplements/<date>` (what was taken that day). Optional: integrate **NIH DSLD API** (or similar) to fetch brand, nutrition facts, and benefits when user adds or views a supplement.  
   - Use **UserFeedback** toasts (§7.4) for publish success and error on write/sync.  
   - Sync: subscribe for `erv/`, decrypt, merge into Room; publish on write.  
   - Wire navigation from dashboard: Light therapy and Supplements tiles open their silos; other tiles still show Coming soon.

6. **Goals**  
   - **Data**: `erv/goals` replaceable event (kind 30078, NIP-44), payload keyed by category (§7.1). Sync read/write like other ERV data.  
   - **Goals tab**: Dedicated screen/tab where the user sets and edits goals for any category (add, change, clear). No editing of goals on the main page.  
   - **Main page Goals section**: Display all set goals to motivate. For each goal: show progress (e.g. 2/4 this week). When **met**: mark as **completed** (e.g. bold green) and show **summary details of the session(s)** that met it beneath the row. Details are pulled from the category silo in a short summary format. Tap row to open that category.  
   - **Summary format**: Per-category summary (one line or two) derived from the latest or relevant log(s) that satisfied the goal. Implement once at least one category has log shape and goals are computable.  
   - **Why Phase 6 (not later):** Goals drive the dashboard motivational display (§7.2 item 2). Building them immediately after the dashboard means every subsequent category (Cardio, Workout, Stretching, etc.) benefits from goal tracking and progress feedback from day one. Testing each new silo includes verifying its goal integration, catching issues early rather than retrofitting goals after hardening.

7. **Cardio and Body weight diary**  
   - **Body weight diary** (§2.4) first: `erv/bodyweight`, entries by date; weight in **lb or kg** (default lb), stored as kg in payload; UI to log and view history/trend. Settings: **Body weight unit** (lb default, kg) and **Body weight (fallback)** value. Build **before or alongside** Cardio because calorie estimation (MET × weight_kg × time) depends on body weight. If the user does not use the diary, fall back to a single weight (and unit) in Settings or skip estimate.  
   - **Cardio** (§2.4): `erv/cardio/<date>` or per-session UUID. **Activity types**: built-in list (walk, run, sprint, hike, swim, ruck, bike, rowing, etc.) plus **user-defined custom types** (§2.4); calorie estimate may be skipped or approximate for custom types. **Calorie estimation**: MET-based (Compendium of Physical Activities) and Pandolf for rucking; store `estimatedKcal` per session; **body weight** from diary or Settings fallback. **GPS track**: phone records location during activity; **explicit user control** -- per-activity toggle and app-wide setting; after workout, **overlay track on OSM tile-based map** (OsmDroid or MapLibre; OSM attribution). Same Nostr pattern.  
   - **HR per activity** and **HR zone analysis** are defined in the Cardio data model (optional fields: `hrSamples`, `hrSummary`, `hrZones`) but **deferred to Phase 10** for actual BLE integration. At this phase, Cardio sessions store duration, type, calories, and GPS; HR fields are populated only when a monitor is connected (Phase 10).
   - **Foreground Service for active sessions (§5.1, §13)**: When a cardio session is started, launch an **`ActiveSessionService`** (Foreground Service) that owns the session timer, GPS recording, and session state. The service exposes state via `StateFlow` (or similar) to the Compose UI. The service posts a persistent notification (required for foreground services) showing elapsed time and current activity; this notification is also the foundation for the future **Workout Bubble** (§13). Build the service in this phase so the timer survives backgrounding; the Bubble UI is layered on in Phase 12.

8. **Workout (weight training)**  
   - `erv/weight/exercises` (exercise list: four compounds + custom, muscle group, push/pull, equipment) and `erv/weight/<date>` or `<uuid>` (session log with sets/reps/weight). Organize by muscle group or push/pull days. Refer to Runstr for log structure; keep kind 30078 and `erv/` namespace.  
   - Workout analytics: 1RM estimation (Epley, Brzycki), volume tracking, charting (Vico or similar). See §2.4 Workout analytics.  
   - Optional: HR per workout session when a monitor is connected (same fields as Cardio; populated after Phase 10).
   - **Foreground Service for active sessions (§5.1, §13)**: Reuse or extend the **`ActiveSessionService`** from Phase 7 for weight training sessions. The service tracks the active workout (current exercise, set number, rest timer, elapsed time) and posts a persistent notification. Same architecture: service is source of truth; ViewModel observes; Bubble (Phase 12) attaches later. If the cardio and workout session shapes differ enough, the service can use a sealed class (e.g. `ActiveSession.Cardio(...)` / `ActiveSession.Weight(...)`) to hold the appropriate state.

9. **Stretching and remaining silos**  
   - **Stretching**: **Bundled general stretch catalog** (read-only in APK); **user-defined routines** (e.g. pre-workout, pre-run, pre-swim); **guided routine player** — play, countdown per stretch, audible at zero, ~5 s transition, then next stretch; static image or text (no Lottie; §2.4 groundwork). **Yoga / large third-party pose libraries** are **out of scope for this phase** (see §2.4 **Future: yoga / expanded library**).  
   - **Remaining silos** (one at a time): **Sauna** and **Cold plunge** (§2.4): session timer + optional temperature, auto-log on timer end; **heat/cold routines** (`erv/heatcold/routines`) for contrast therapy (alternating sauna ↔ cold rounds) with guided player, each round logged to sauna/cold; **Sleep** (§2.4): log sleep window (bed/wake or duration), optional quality and notes. Goal types in §7.1 already exist for each.

10. **BLE Heart Rate and Pebble integration (experimental)**  
    - After Cardio (and optionally Workout) silos exist: add HR monitor support and populate the HR fields defined in Phase 7 data model.  
    - **Phase 10a -- BLE Heart Rate Profile**: Use Android built-in Bluetooth LE APIs to connect to monitors supporting the standard Heart Rate Profile (service 0x180D, characteristic 0x2A37). Store **time-series HR samples** during the session; compute **heart rate zone analysis** (five zones, max HR from age or Settings; §2.4). Attach HR to Cardio (and optionally Workout) sessions. Works on any Android with BLE, including GrapheneOS.  
    - **Phase 10b -- Pebble Time 2 / libpebble3**: Integrate libpebble3 (§12) so ERV receives HR directly from the Pebble watch. Optional custom watch app if needed (§12.6).  
    - **No Health Connect** -- all HR paths are direct BLE or direct device (§8). See §12 for full integration plan.  
    - **Pebble has no GPS** (§12); GPS for run/hike/bike/ruck comes from the **phone** (already implemented in Phase 7 Cardio).

11. **Hardening**  
    - Error handling, offline queue, sync indicators, optional multi-relay support.  
    - Ensure **toast feedback** (§7.4) is used for all publish/sync outcomes (success and error) across all silos.  
    - Security review (key handling, logging, NIP-44 usage).  
    - Final pass on all categories: edge cases, data validation, payload size limits, UX polish.  
    - **Optional: Music bar** (§7.6) — Minimal “now playing” bar at bottom of screen using **MediaSessionManager** + **MediaController** and a **NotificationListenerService** when the user has enabled the bar in Settings. Show only when an active media session exists; play/pause/skip and tap-to-open source app. Kept minimal so it does not detract from health logging.

12. **Workout Bubble (active session overlay) — §13**  
    - **Prerequisite**: Phases 7 and 8 must have the **`ActiveSessionService`** (Foreground Service) in place, with session state exposed via `StateFlow` and a persistent notification already showing during active sessions.  
    - **Bubble notification**: Upgrade the existing foreground service notification to include **`BubbleMetadata`** (Android 11+ / API 30). The notification's icon becomes the floating bubble; tapping expands it into a lightweight embedded Activity. On devices below API 30, the persistent notification with action buttons (pause, end, open app) serves as the fallback — no bubble, but the same functionality via the notification shade.  
    - **Bubble Activity**: A dedicated, lightweight `Activity` (declared `resizeableActivity="true"`, `allowEmbedded="true"` in the manifest) that renders inside the expanded bubble. Shows: **(a)** session type and elapsed time, **(b)** current exercise / activity name, **(c)** for weight training: current set progress (e.g. "Set 3/4") and a rest timer, **(d)** for cardio: distance (if GPS), current pace or speed, **(e)** action buttons: **log set** (weight), **pause/resume**, **end workout**. The Activity binds to the same `ActiveSessionService` and observes the same `StateFlow` as the main app UI — no data duplication.  
    - **Collapsed state**: The floating icon shows a small ERV badge; optionally animate or pulse to signal an active session. Dragging to the dismiss target ends the bubble overlay (not the workout — the notification and service continue).  
    - **UX**: When the user starts a workout in ERV and navigates away, the bubble appears automatically. Tapping the bubble expands the mini workout view. Tapping a "full app" button in the expanded bubble navigates back to the full workout screen inside ERV. Ending the workout from the bubble logs the session and stops the service (same as ending from the main UI).  
    - **Permissions**: Requires `POST_NOTIFICATIONS` (already requested) and `android.permission.FOREGROUND_SERVICE` / `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` (or appropriate type). The bubble itself requires no extra permission beyond the notification channel being configured for bubbles (`setAllowBubbles(true)` on the channel, `setBubbleMetadata(...)` on the notification). User must have notifications enabled for the channel.

---

## 9.1 Gaps and items to clarify (for implementation)

Items previously listed here but now specified in the plan (e.g. **Settings** in §7.5, **cardio activities with accurate calorie estimation** in §2.4) have been removed. The following **unresolved** gaps or loose ends should be resolved during implementation:

| Gap | Where it appears | Recommendation |
|-----|------------------|----------------|
| **Body weight for calories** | Cardio MET/Pandolf formulas need weight in kg. | User tracks in **lb (default) or kg** (§2.4). Store diary entries in kg; convert fallback weight from lb to kg when needed. Use optional **body weight diary** — latest or session-date entry; fallback to single weight + unit in Settings if user doesn’t use diary. Prompt or skip estimate if neither is set. |
| **GPS track payload size** | Long runs = many points; Nostr event content has practical size limits. | Downsample/thin track for sync, or keep full track local and sync simplified polyline; or split into separate event. Define in Cardio implementation. See §2.4 GPS bullet. |
| **Cardio: one event per day vs per session** | §2.3 and §2.4 allow either pattern. | Choose one: (a) one replaceable event per day with `sessions: [{ ... }, { ... }]` and `erv/cardio/<date>`, or (b) one event per session with `erv/cardio/<uuid>`. §2.4 Cardio spells out the "Event shape choice"; document the implementation decision in code. |
| **Selected date state and calendar data** | Date navigation drives main screen; “logged days” for month view. | Selected date: hold in ViewModel or DataStore; all dashboard/category queries use it. Month view: query local DB by date across categories for checkmarks (or maintain a summary index). See §7.2 item 6. |
| **HR zone analysis data** | Zone breakdown needs time-in-zone; min/max/avg alone are insufficient. | When a HR monitor is connected, store **time-series HR samples** (e.g. every 5–10 s) during the session so zone breakdown (e.g. % or minutes in Zones 1–5) can be computed. See §2.4 heart rate zone analysis. |
| **Sauna / Cold plunge merge on auto-log** | Timer auto-logs like Light therapy. | When a sauna or cold session ends, **merge** into existing `erv/sauna/<date>` or `erv/cold/<date>` (load event, append session to `sessions` array, replace). Contrast routine: log each step to its category; optionally tag with `routineId` and `roundIndex`. See §2.4. |
| **Light therapy: merge on auto-log** | Timer “auto-logs” when session ends. | When appends a session to the day’s log, **merge** into existing `erv/light/<date>` payload (load current event, append session to `sessions` array, replace event). |
| **Stretching: third-party yoga datasets** | LunaticPrakash / rebeccaestes are **future** (§2.4). | When yoga/expanded library ships: confirm LICENSE/README before bundling or redistributing assets. Not required for v1 bundled general stretches. |
| **Cardio: map built-in types to Compendium MET** | §2.4 now lists which activities have accurate MET/Pandolf (walk, run, hike, swim, ruck, bike, rowing, elliptical, stair, skiing, sports, etc.). | Implement mapping from each built-in activity type (and optional intensity/speed) to Compendium code or MET value (or Pandolf for rucking). Use §2.4 table and [Compendium](https://pacompendium.com/) as reference. |
| **Calorie estimation for custom cardio types only** | Custom (user-added) activities have no Compendium MET. | For custom types only: choose (a) skip estimate, (b) use a default MET (e.g. 5), or (c) let user set MET when defining the custom type. Built-in list and accurate-estimation activities are defined in §2.4. |
| **NIP-44 + Amber encrypt-to-self** | §4 says mirror FiatLife’s NIP-44 + Amber flow. | Confirm with FiatLife (or docs) how encrypt-to-self is performed when using Amber (e.g. key material availability); implement accordingly. |
| **Offline / conflict UX** | “Latest wins” for replaceable events; offline queue in hardening. | Consider: show “Last synced at X” and/or surface when local is ahead of relay; avoid silent overwrites if multi-device is common. |
| **Body weight payload growth** | `erv/bodyweight` is one replaceable event; daily logging over years = large payload. | Monitor payload size; shard by year (e.g. `erv/bodyweight/2026`) if single event exceeds relay limits. See §2.4 body weight. |
| **Stretching library: bundled vs synced** | §2.4 | **v1:** ship **bundled read-only** general stretch catalog in the APK. **Optional:** `erv/stretching/poses` only if custom user stretches must sync; otherwise omit. |
| **Foreground service type** | Phase 7/8 introduce `ActiveSessionService`; Android 14+ requires declaring `foregroundServiceType` in manifest. | Choose appropriate type(s): `location` (for GPS during cardio), `specialUse` (for workout timer without GPS). May need to declare multiple types or use `specialUse` as umbrella. Confirm Play Store policy (or F-Droid if not on Play) for the chosen type. |
| **Bubble: minimum API and fallback UX** | Bubbles require API 30 (Android 11). `minSdkVersion` may be lower. | On API < 30, fall back to a **rich persistent notification** with action buttons (pause, end, open app) — same functionality, no floating icon. Test both paths. |
| **Bubble: single-session constraint** | Only one workout can be active at a time (one foreground service notification = one bubble). | Enforce at service level: starting a new session must end or pause the current one. UI should prevent starting a second concurrent session. |
| **ActiveSessionService state shape** | Cardio and Weight have different session states (exercise/sets vs activity/distance). | Use a sealed class (e.g. `ActiveSession.Cardio(...)` / `ActiveSession.Weight(...)`) so the service, notification, and bubble Activity can render the right content. Define in Phase 7; extend in Phase 8. |

---

## 10. Summary

| Topic | Decision |
|-------|----------|
| **Nostr kind** | 30078 (parameterized replaceable) |
| **Namespace** | d-tag prefix `erv/` for all events |
| **Encryption** | NIP-44 (encrypt-to-self) for all event content |
| **Auth** | NIP-42 when relay requires it; kind 22242 auth event |
| **Login** | Local nsec or Amber (NIP-46); reuse FiatLife patterns |
| **Categories** | Eight silos; one replaceable event per (user, category, date) or per UUID where needed |
| **Conflicts** | None with Runstr (different kind) or FiatLife (different d-tag prefix) |
| **Relay** | User’s relay(s); efficient one-subscription strategy for all ERV data |

---

## 11. Future: iOS (or other platforms)

If you add an iPhone (or other) client later, the **current design does not need to change** in any fundamental way.

### What stays the same

- **Nostr event design** — Kind 30078, `erv/` d-tags, and NIP-44 encrypt-to-self are protocol-level. Any client (Android, iOS, web) can publish and subscribe to the same events. Data stays compatible.
- **NIP-42 and NIP-44** — Same auth and encryption flows; only the platform’s crypto/keystore APIs differ.
- **Sync and conflict handling** — Replaceable semantics and “latest wins” work the same across devices. A user can use ERV on Android and iOS with the **same Nostr key** and see the same data from the relay.

### What differs on iOS today

- **No in‑ecosystem Nostr signer** — There is no widely adopted iOS equivalent of Amber (NIP-55 Android signer). So an iOS build would support **only local nsec** (key stored in Keychain). “Remote signer” would be unavailable unless an iOS signer or NIP-46-compatible signer appears later.
- **Local nsec is already in the plan** — Your design already has “(1) Nostr nsec on device” as a first-class option. iOS would simply implement that path only; no change to event kinds or d-tags.

### Recommendations so a future iOS port is straightforward

1. **Abstract the signer** — In code, treat “who signs?” as a pluggable dependency (e.g. `Signer` interface: “sign this event”). On Android you implement “local signer” and “Amber (NIP-46) signer”; on iOS you’d implement only “local signer.” Relay, NIP-42, NIP-44, and event building stay shared or easy to reuse.
2. **Don’t tie event shape to Amber** — You’re already using a standard kind (30078) and encrypt-to-self. That works with any signer and any future iOS signer.
3. **Shared specs** — Keep the event and d-tag scheme (and any shared JSON schemas) in docs or a spec so an iOS (or Kotlin Multiplatform) client can reuse them without re-deriving.

So: **no change to your current design plans.** An iPhone version would be “same Nostr events, same relay, same NIP-42/NIP-44; local-key-only on iOS until an iOS signer exists.”

---

## 12. Pebble Time 2 (Core Devices) integration and companion apps

ERV should support **heart rate during workouts and cardio** and **sleep tracking** **without Google Play or Health Connect** (§8). **(1) Generic HR monitors** (BLE chest straps, many bands): use Android’s built-in **Bluetooth LE** APIs and the standard **Heart Rate Profile** (service 0x180D, characteristic 0x2A37) so ERV connects directly to the device; works on GrapheneOS and any Android with BLE. **(2) Pebble Time 2** (Core Devices / rePebble): use **libpebble3** to receive **HR** (for cardio/workout sessions) and **sleep data** (for the Sleep silo) directly from the watch. This section outlines the Pebble integration goal, the Core Devices stack, and a plan for any companion applications (including a watch app) that may be needed.

### 12.1 Goal

- **User flow (HR)**: User starts a “cardio” or “weight training” session in ERV; during the session, HR from the Pebble Time 2 is captured and associated with that session (e.g. samples or min/max/avg).
- **User flow (sleep)**: When a Pebble is connected, ERV can show and track sleep metrics from the watch. After sync (e.g. on app open or refresh), ERV pulls sleep data from libpebble3 HealthService (tag 83) for the relevant night(s) and populates or enriches the Sleep silo for that date (§2.4): duration, window (start/end), and optional device-derived metrics. User can still enter or override quality and notes; goals and history use the same `erv/sleep/<date>` payload.
- **Data**: Cardio (and optionally weight) session events support optional HR (§2.4). Sleep events support optional deviceMetrics and source (e.g. "pebble") when sleep is pulled from the watch (§2.4). All stay NIP-44 encrypted.
- **Privacy**: HR and sleep data stay local and NIP-44 encrypted like the rest of ERV data; no dependency on big-tech health clouds.

### 12.2 Core Devices ecosystem (reference)

| Component | Role | Links |
|-----------|------|--------|
| **PebbleOS** | Open-source OS on the watch; runs watchfaces and apps (C / JS). | [PebbleOS](https://github.com/coredevices/PebbleOS), [PebbleOS docs](https://pebbleos.readthedocs.io/) |
| **mobileapp** | Official cross-platform companion app (Android + iOS). Built with Kotlin, uses **libpebble3**. | [coredevices/mobileapp](https://github.com/coredevices/mobileapp) |
| **libpebble3** | Kotlin multiplatform library for everything a Pebble companion app needs (except UI): discovery, connection, protocol, BlobDB, firmware, **health data from the watch**, etc. | Same repo as mobileapp; [Roadmap](https://github.com/coredevices/mobileapp/wiki/Roadmap) |
| **Pebble Time 2 hardware** | Watch with built-in heart rate sensor (see legacy [HealthService / HRM docs](https://developer.rebble.io/guides/events-and-services/hrm/) for API concepts; new stack may expose similar). **No built-in GPS** — the watch does not record location. | [rePebble.com](https://rePebble.com), [Core Devices](https://github.com/coredevices) |

**GPS:** The Pebble does **not** have GPS. For run/hike/bike/ruck tracking, **GPS comes from the phone**. ERV records the track on the phone (location updates during the session) and can overlay it on an OSM tile-based map (§2.4 Cardio); the watch can still provide HR for the same session.

Note: The libpebble3 roadmap currently lists **“Health support” as a known missing feature** (to be added). The codebase actually already implements health sync and AppMessage (see §12.3); the roadmap may be outdated.

### 12.3 Documentation and codebase analysis: what's possible today

Review of the [Core Devices mobileapp](https://github.com/coredevices/mobileapp) (libpebble3) source and [Rebble SDK docs](https://developer.rebble.io/) shows:

**AppMessage (watch to phone)** — **Implemented.** [`AppMessageService.kt`](https://github.com/coredevices/mobileapp/blob/main/libpebble3/src/commonMain/kotlin/io/rebble/libpebblecommon/services/appmessage/AppMessageService.kt) provides `sendAppMessage()` and `inboundAppMessages(appUuid): Flow<InboundAppMessageData>`. A watch app can send key-value messages; the phone app subscribing to that app's UUID receives a Flow. So **streaming HR (or any data) from watch to ERV is possible today**: build a watch app that reads HR via the [Pebble HealthService/HRM API](https://developer.rebble.io/guides/events-and-services/hrm/) and sends AppMessages; ERV uses libpebble3 and `inboundAppMessages(uuid)` to receive them. See [Sending and Receiving Data](https://developer.rebble.io/guides/communication/sending-and-receiving-data/), [PebbleKit Android](https://developer.rebble.io/guides/communication/using-pebblekit-android/).

**Health (steps, sleep, HR)** — **Implemented.** [`HealthService.kt`](https://github.com/coredevices/mobileapp/blob/main/libpebble3/src/commonMain/kotlin/io/rebble/libpebblecommon/services/HealthService.kt) has `requestHealthData(fullSync)`. [`HealthDataProcessor.kt`](https://github.com/coredevices/mobileapp/blob/main/libpebble3/src/commonMain/kotlin/io/rebble/libpebblecommon/datalogging/HealthDataProcessor.kt) processes datalogging tags 81 (steps), **83 (sleep)**, 84 (overlay), 85 (HR). So **pulling HR** from the watch for cardio/workout sessions and **pulling sleep** for the Sleep silo are both possible: ERV uses libpebble3, calls `requestHealthData()`, and reads from the library's health DB — HR for the session time window, sleep for the relevant night(s). Sync-on-request, not live stream.

**Constraint:** The watch typically pairs with **one** companion app. ERV can stream or pull data directly only if ERV is that app (user pairs watch with ERV) or if a bridge forwards data. PebbleKit Android 2 uses per-app authorities, so ERV can declare its own provider; the watch connection is the limiting factor.

**Conclusion:** Direct streaming of data from the Pebble into ERV **is possible today** via libpebble3 (AppMessage for live HR from a custom watch app, or HealthService for synced health/HR). The roadmap's "Health support missing" likely refers to product/UI completeness, not absence of code.

### 12.4 Does pairing the watch solely to ERV limit watch functionality?

**Yes.** If the user pairs the Pebble **only** with ERV (and never uses the official Core Devices companion app), the watch can lose any feature that depends on the companion app and that ERV does not implement.

From the [libpebble3 Roadmap](https://github.com/coredevices/mobileapp/wiki/Roadmap), the companion app is expected to handle (among other things):

| Feature | Likely limited if only ERV is paired |
|--------|--------------------------------------|
| **Firmware updates** | Yes — companion delivers or triggers updates. Without the official app, the watch may not get new firmware. |
| **App locker / installing watch apps** | Yes — BlobDB sync installs and manages apps. ERV would need to implement or delegate this for users to add new watchfaces/apps. |
| **Timeline / calendar pins** | Yes — calendar and timeline sync is companion-side. |
| **Notifications (phone → watch)** | Yes — on Android, the companion relays notifications to the watch. |
| **Music control, phone call control** | Yes — companion handles these. |
| **Time sync** | Maybe — often basic; could be implemented in ERV if needed. |
| **Health (steps, sleep, HR)** | No — ERV would implement **HR for cardio/workout sessions** and **sleep tracking for the Sleep silo** (§2.4); full health dashboard might still live in the official app. |
| **AppMessage (e.g. custom HR app)** | No — ERV would support this for its own watch app. |

So **pairing solely to ERV would limit the watch** to what ERV implements (e.g. connection, health/HR, AppMessage for an ERV watch app). The user would lose firmware updates, app installation/management, notifications on the watch, timeline, music/call control, etc., unless ERV (or a separate “full” companion) provides them.

**Recommendation:** ERV **does not use Health Connect** (§8). Use **direct BLE** (Heart Rate Profile) for generic HR monitors and **Option A or C (libpebble3)** for Pebble so the user can keep the watch paired with the **official Core app** (full functionality) and if the official app does not expose HR data to other apps, ERV can use a **bridge** approach (§12.6 item 3) where a small companion receives HR from a custom watch app and forwards it to ERV via local intent or API. Use “ERV as sole companion” only if the user explicitly chooses a watch dedicated to ERV and accepts reduced general-purpose watch features.

### 12.5 Integration options (Android)

| Option | Description | Pros / cons |
|--------|-------------|-------------|
| **A. libpebble3 inside ERV** | ERV depends on (or embeds) libpebble3 and talks to the watch directly when the user has started a session. When “Health support” is added, ERV subscribes to HR and buffers samples for the current session. | Single app for “log session + get HR.” May conflict with the official companion app for pairing (e.g. [PebbleKit provider](https://github.com/coredevices/mobileapp#enabling-pebblekit-android-2) is app-specific; only one app can own certain authorities). |
| **B. ~~Android Health Connect~~ (ruled out — §8)** | The Core Devices companion app (or a future watch app) writes HR (and optionally sessions) to [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect). ERV reads HR for the time range of the user’s logged session and attaches it to the cardio/workout event. | No watch protocol in ERV; works with whatever app writes to Health Connect. Depends on the companion app (or another app) exposing HR there. |
| **C. Custom watch app + ERV** | A small PebbleOS watch app (C or JS) captures HR and sends it to the phone (e.g. via app message or a dedicated protocol). ERV (or a small bridge using libpebble3) receives and associates with the active session. | Full control over when and how HR is sampled; works even if the official app doesn’t expose HR. Requires building and maintaining a watch app and a phone-side receiver. |

**Recommendation:** Start with **Option A (libpebble3 inside ERV)** when libpebble3 health support is ready, so ERV receives HR directly from the Pebble. **Option C** (custom watch app) is the fallback if libpebble3 health is delayed. Evaluate whether ERV can integrate libpebble3 (e.g. as an optional Pebble companion mode for sessions) without conflicting with the official app.

### 12.6 Companion applications that may need to be built

1. **ERV (Android) — extended**  
   - **No new app.** ERV gains: (a) “Start workout/cardio” that opens a time window; (b) a way to obtain HR for that window via **direct BLE** (Heart Rate Profile) and/or **libpebble3** (Pebble) — no Health Connect; (c) storing HR summary/samples in the session’s encrypted payload (e.g. `erv/cardio/<id>`) and syncing via Nostr; (d) **sleep tracking**: when a Pebble is connected, pull sleep data from HealthService (tag 83) and populate/enrich **Sleep** (`erv/sleep/<date>`) so the user can see and track sleep metrics from the watch in the Sleep silo (§2.4).

2. **Pebble watch app (optional)**  
   - **When**: Only if HR cannot be obtained via libpebble3 directly (e.g. official app doesn’t write HR, or libpebble3 health is delayed).  
   - **What**: A minimal PebbleOS app (C or [Rocky.js](https://developer.rebble.io/developer.pebble.com/docs/index.html)) that:  
     - Declares the `health` capability (or equivalent in the new SDK).  
     - On “start” (e.g. from a phone command or in-app button), subscribes to HR updates from the system.  
     - Sends HR samples (or periodic summaries) to the phone via the existing app-message / PebbleKit path that libpebble3 supports.  
   - **Phone side**: ERV (using libpebble3) receives HR and stores in session; no Health Connect. Optional bridge only if needed to forward from another receiver to ERV.  
   - **Resources**: [Core Devices PebbleOS](https://github.com/coredevices/PebbleOS), [pebble-tool](https://github.com/coredevices/pebble-tool), [CloudPebble](https://github.com/coredevices/cloudpebble) (web IDE), [SDK docs](https://developer.rebble.io/).

3. **Bridge / “ERV Companion” (optional)**  
   - **When**: If the watch must pair with the official Core app for firmware/management, but that app doesn’t expose HR to ERV (and doesn’t expose HR to other apps). A separate small app could: use libpebble3 to receive HR from a **custom watch app** (Option C) and forward HR to ERV (e.g. via local API or intent). The bridge sends HR to ERV only; no Google services involved.  
   - This keeps ERV free of Pebble-specific code while still allowing a custom watch app to be the HR source.

### 12.7 Data model (unchanged; HR is an extension)

- **Cardio** (and optionally weight) session events already support “optional HR” in the data model defined in §2.4.  
- Encrypted JSON for a session can include e.g. `hrSamples: [{t, bpm}]` or `hrSummary: { min, max, avg }` for the session time range.  
- **Sleep** (§2.4) events (`erv/sleep/<date>`) can include optional `source` and `deviceMetrics` when sleep is pulled from a connected Pebble (HealthService tag 83); same kind and encryption.  
- No change to kind (30078) or d-tag scheme (`erv/cardio/...`, `erv/weight/...`, `erv/sleep/...`).

### 12.8 Phasing

- **After** core ERV (Nostr, categories, dashboard) and **after** the Cardio (and optionally Weight) silos exist: add HR monitor support as **§9 Phase 10** (experimental).  
- **Phase 10a**: Implement **direct BLE Heart Rate Profile** for compatible HR monitors (chest straps, etc.); store time-series HR samples and compute zone analysis; attach HR to Cardio (and optionally Workout) sessions. No Health Connect.  
- **Phase 10b**: Integrate **libpebble3** for Pebble Time 2 (Option A or C per §12.5) so ERV receives HR directly from the watch.  
- **Phase 10c** (if needed): Design and build the custom watch app (and optional bridge) per §12.6. All HR paths are direct BLE or direct device.

This keeps ERV’s core design unchanged while making Pebble Time 2 heart rate a supported input for workout and cardio sessions, with a clear path for companion apps (watch app and optional bridge) if the official stack does not expose HR.

---

## 13. Workout Bubble (active session overlay)

When a **cardio or weight training session is active**, ERV can display a **floating bubble** (Android Bubbles API) that hovers above all other apps on the device. This lets the user leave ERV — to change music, reply to a message, check the time — and still see their workout status and interact with the session without navigating back to the app. Think of it like Facebook Messenger’s chat heads or ESPN’s live score tracker, but for an active workout.

### 13.1 Concept

- **Collapsed (bubble icon)**: A small floating ERV icon on screen. Visible from any app while a session is active. Optionally shows a live timer or pulsing animation.
- **Expanded (mini workout view)**: Tapping the bubble opens a compact, embedded Activity inside the bubble window. This view shows key session info and action buttons — enough to interact without returning to the full app.
- **Full app return**: A button in the expanded bubble launches the full ERV workout screen for detailed interaction (e.g. browsing exercises, reviewing history).

### 13.2 What the bubble shows

The expanded bubble Activity renders differently depending on the active session type:

| Session type | Bubble content |
|-------------|----------------|
| **Cardio** | Activity name (e.g. “Running”), elapsed time, distance (if GPS active), current pace/speed, estimated calories so far. Buttons: **Pause/Resume**, **End workout**. |
| **Weight training** | Current exercise name, set progress (e.g. “Set 3/4”), rest timer (if resting between sets), elapsed workout time. Buttons: **Log set** (quick entry: reps + weight), **Next exercise**, **Pause/Resume**, **End workout**. |

The bubble Activity binds to the same **`ActiveSessionService`** (§5.1) and observes the same `StateFlow` as the main Compose UI. No data duplication; the service is the single source of truth.

### 13.3 Architecture (service-first design)

The bubble depends on architecture that is built **before** the bubble itself:

1. **`ActiveSessionService` (Foreground Service)** — Built in **Phase 7** (Cardio) and extended in **Phase 8** (Workout). Owns the session timer, GPS recording (cardio), current exercise/set state (workout), and exposes all state via `StateFlow`. Posts a **persistent notification** (required for foreground services) showing session status.
2. **Persistent notification (Phase 7/8)** — The foreground service notification shows elapsed time, activity name, and action buttons (pause, end, open app). This is the **fallback UX** on devices that do not support bubbles (API < 30) and is useful in its own right.
3. **Bubble upgrade (Phase 12)** — The existing notification gains `BubbleMetadata` pointing to the Bubble Activity. Android promotes it to a floating bubble. The notification channel is configured with `setAllowBubbles(true)`.
4. **Bubble Activity** — A lightweight `Activity` declared with `resizeableActivity="true"` and `allowEmbedded="true"` in the manifest. It binds to `ActiveSessionService`, observes state, and renders a compact Compose UI. It is **not** the main `MainActivity`; it is a separate, minimal entry point.

```
┌─────────────────────────────────────────────────┐
│              ActiveSessionService                │
│  (Foreground Service — source of truth)          │
│                                                  │
│  StateFlow<ActiveSession>                        │
│    ├── ActiveSession.Cardio(elapsed, distance,   │
│    │       pace, calories, activity, isPaused)   │
│    └── ActiveSession.Weight(elapsed, exercise,   │
│            setNum, totalSets, restTimer,         │
│            isPaused)                             │
│                                                  │
│  Notification (always present when active)       │
│    └── BubbleMetadata → BubbleActivity (API 30+) │
├──────────────────────┬──────────────────────────┤
│                      │                          │
│   Main Compose UI    │    Bubble Activity       │
│   (binds to service, │    (binds to service,    │
│    full workout      │     compact view,        │
│    screen)           │     quick actions)       │
└──────────────────────┴──────────────────────────┘
```

### 13.4 Fallback (API < 30)

On devices running Android 10 or below (API < 30), bubbles are not available. The **persistent notification** from the foreground service serves as the fallback:

- Shows elapsed time, activity/exercise name in the notification body.
- **Action buttons** on the notification: Pause/Resume, End workout, Open app.
- Tapping the notification body opens the full workout screen in ERV.
- This provides the same core functionality (status at a glance + quick actions from anywhere) without the floating bubble UX.

### 13.5 Permissions and manifest

- **`FOREGROUND_SERVICE`** — Required for the `ActiveSessionService`. Already needed for the timer to survive backgrounding (Phase 7).
- **`FOREGROUND_SERVICE_SPECIAL_USE`** (or `FOREGROUND_SERVICE_LOCATION` for GPS cardio sessions) — Android 14+ requires declaring `foregroundServiceType` in the manifest. Use `location` when GPS is active; `specialUse` otherwise.
- **`POST_NOTIFICATIONS`** — Already requested (Phase 1 scaffold). The bubble is backed by a notification; if the user disables notifications for the channel, the bubble will not appear.
- **Notification channel** — Create a dedicated channel (e.g. `active_session`) with `setAllowBubbles(true)`. Separate from the existing `routine_reminders` channel.
- **Bubble Activity in manifest**: `<activity android:name=".ui.bubble.BubbleActivity" android:resizeableActivity="true" android:allowEmbedded="true" ... />`.

### 13.6 UX flow

1. User starts a cardio or weight training session in ERV.
2. `ActiveSessionService` starts as a foreground service; persistent notification appears.
3. User navigates away from ERV (e.g. opens Spotify).
4. On API 30+: the notification is promoted to a **floating bubble** on screen. On API < 30: the notification stays in the shade with action buttons.
5. User taps the bubble → expanded mini view shows session status and quick actions.
6. User can **log a set**, **pause**, or **end the workout** directly from the bubble.
7. User taps “Open full app” → ERV opens to the full workout screen.
8. When the session ends (from bubble, notification, or main app), the service stops, notification/bubble disappear, and the session is logged and synced.

### 13.7 Phasing

- **Phase 7 (Cardio)**: Build `ActiveSessionService` with cardio state, persistent notification, and action buttons. Timer and GPS survive backgrounding. No bubble yet.
- **Phase 8 (Workout)**: Extend `ActiveSessionService` with weight training state (sealed class). Same notification pattern.
- **Phase 12 (Workout Bubble)**: Add `BubbleActivity`, `BubbleMetadata` on the notification, bubble notification channel. Wire up the compact Compose UI inside the bubble. Test on API 30+ devices; verify fallback on older devices.

---

This plan should be enough to structure the repo, data model, and app so ERV meets the requirements above while staying compatible with NIP-42, NIP-44, and remote signers like Amber.
<!--  -->