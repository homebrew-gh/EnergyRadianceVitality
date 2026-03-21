# Cardio outdoor tracking: GPS, BLE bike sensors & share cards — implementation plan

This document turns the product direction in [PLAN_OF_ACTION.md §2.4](PLAN_OF_ACTION.md) into an actionable sequence for:

1. **GPS** — track + OSM review + **privacy-first sharing** (full **OSM map** *or* **track-only** / no basemap for social relays).
2. **Bluetooth bike speed sensors** — wheel-based speed/distance via the standard **Cycling Speed and Cadence (CSC)** BLE profile (e.g. Garmin / Wahoo–class accessories), aligned with ERV’s **direct BLE** approach (no Health Connect; see PLAN §8).

**Status:** Not implemented in app code yet; this is the plan.

---

## 1. Goals

### 1.1 GPS (phone)

1. **Record** a GPS track during **outdoor** cardio when the user opts in (phone GPS; Pebble has no GPS — see PLAN §12).
2. **Store** points with the session (local + encrypted sync per existing cardio day-log strategy).
3. **Review** after the workout: map view with track over **OpenStreetMap** tiles + required **attribution**.
4. **Share** to social relays (Nostr): a **share image** (and/or kind 30078 payload reference) that can show:
   - **Map mode:** polyline over OSM tiles (or static map snapshot), **or**
   - **Privacy mode:** polyline on a **neutral background only** (no map, no identifiable geography) — same stats (distance, pace, time) as the reference UX.
5. **Respect** **app-wide** and **per-session** GPS toggles (already specified in PLAN §2.4 / Settings §7.5).

### 1.2 BLE bike speed / cadence sensors

1. **Support** optional **Bluetooth LE** sensors that implement the **Bluetooth SIG Cycling Speed and Cadence (CSC)** profile (GATT). Common examples: **wheel magnet** / hub speed sensors (e.g. Garmin Speed Sensor and similar) and **cadence** sensors that speak the same standard — **not** a proprietary Garmin SDK; pairing is **direct BLE** from ERV like HR monitors (PLAN §2.4 / §8).
2. **Use** CSC **wheel revolution** + **last wheel event time** (and optional crank data if present) to compute **speed and cumulative distance** during a **cycling** session when GPS is off, weak, or user prefers sensor distance (e.g. indoor trainer bike with sensor, or outdoor ride without phone GPS).
3. **Require** user-entered **wheel circumference** (or preset sizes) for accurate distance from revolutions — same physics any head unit uses.
4. **Coexist** with GPS: **merge policy** TBD — e.g. prefer sensor distance for speed display when both active, or let user choose primary source for the logged session; document in implementation.
5. **Permissions:** `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+), plus foreground service when recording live; **no** location permission required for BLE-only connection (location may still be needed for **scanning** on some API levels — follow Android BLE scanning rules).

## 2. Non-goals (initial release)

- Live segment leaderboards, turn-by-turn, or routing.
- GPS in swimming / indoor treadmill (outdoor + opted-in only).
- Watch-side GPS (device limitation).
- **ANT+**-only sensors as the first target (many phones lack ANT+ hardware; **BLE CSC first**).
- Full **smart trainer** control (ERG, FE-C) — out of scope here; CSC speed/cadence **broadcast** only unless expanded later.

---

## 3. User-facing behavior

| Control | Behavior |
|--------|----------|
| **Settings → GPS (app-wide)** | Master switch: when off, no location capture for cardio; per-activity toggles ignored or disabled in UI. |
| **Start outdoor session** | If app-wide on: offer **“Record GPS track”** (default product choice: **off** until user enables once, or **on** — decide explicitly in UX). |
| **During session** | Foreground service (existing plan: `ActiveSessionService`) requests location updates at a sensible interval; user sees ongoing notification. |
| **After save** | Session detail: **Map** tab (OSM + polyline) + **Stats**; if track present, **Share** opens composer with **Share style: Map / Privacy (track only)**. |
| **Share preview** | Renders bitmap or vector export: polyline + stats; Map variant uses OSM snapshot or embedded tile strip with attribution on image or in caption. |
| **Bike + BLE** | When activity is **cycling** and a CSC sensor is paired for the session: show live speed/distance from wheel (and cadence if available); persist summary + optional revolution samples if needed for debugging (usually summary + total distance is enough). |

---

## 4. Data model (sketch)

Extend `CardioSession` (or attach to session id in a sidecar) with optional:

```text
gpsTrack: {
  points: [ { lat, lon, timeOffsetMs or epochSeconds } ],  // or delta-encoded for size
  source: "phone_gps",
  recordedAtVersion: 1
}
```

Optional **BLE bike sensor** attachment (summary-first to limit payload size):

```text
bikeSensor: {
  source: "ble_csc",
  wheelCircumferenceMm: number,       // user-configured
  totalWheelRevolutions: number?,     // end − start, or null if only speed inferred
  avgCadenceRpm: number?,             // if crank data present
  deviceName: string?                 // optional display only
}
```

Derived fields (can be recomputed): total distance from GPS polyline (Haversine sum); from **CSC**, distance = revolutions × circumference. Optional elevation from GPS later.

**Sync / size:** PLAN already calls out **downsampling for Nostr** — keep **full resolution on device**; sync **thinned** polyline + checksum, or **separate** `erv/cardio/track/<sessionId>` event if daily payload too large. For CSC, **sync summary fields** unless product needs full revolution stream (unlikely).

---

## 5. Android implementation phases

### Phase A — Permissions & policy

- `ACCESS_FINE_LOCATION` (and `ACCESS_COARSE` if needed for fallback).
- **Android 10+:** background location only if justified; prefer **foreground service** + notification during active cardio (matches PLAN §5.1 / Phase 7).
- Play policy text: disclose GPS use in Settings and before first recording.

### Phase B — Location capture

- **Fused Location Provider** (Play Services) *or* **`LocationManager` + GNSS** (AOSP-only / GrapheneOS-friendly) — project requirement: prefer path that works **without** GMS where feasible; evaluate `FusedLocationProviderClient` vs `LocationManager.requestLocationUpdates` for min SDK.
- Batch points every **N seconds** or **M meters** (configurable constants); pause recording when speed ~0 if desired.
- Wire into **`ActiveSessionService`** (or cardio-specific child) so recording survives background; stop on session end / discard.

### Phase C — Persistence

- Store track in local repository with session; migration for existing sessions (null track).
- Serialization for encrypted JSON (see Cardio sync).

### Phase D — Map review (in-app)

- Library: **OsmDroid** or **MapLibre** (per PLAN §2.4); show polyline, fit bounds, OSM attribution string in UI.
- No API key for default OSM tile usage; respect [tile usage policy](https://operations.osmfoundation.org/policies/tiles/).

### Phase E — Share image generation

- **Privacy mode:** draw polyline in **normalized device coordinates** (bounding box fit) on a solid or gradient background (see reference: path only + Distance / Pace / Time). No lat/lon text on image.
- **Map mode:** render map + polyline to bitmap (offscreen `MapView` snapshot or tile compositing); include small **“© OpenStreetMap contributors”** on image or in mandatory caption text for Nostr note.
- Export: `Bitmap` → PNG/JPEG → attach to share intent or encode for Nostr file upload if/when supported.

### Phase F — Nostr / social

- Publish note with **kind** and **tags** per existing ERV conventions; body can include stats + link to image blob or inline summary.
- Ensure **privacy mode** image does not embed recoverable coordinates (strip EXIF; polyline is already abstracted if normalized — do not export raw lat/lon in PNG metadata).

### Phase G — BLE Cycling Speed and Cadence (bike)

- **Discovery:** Scan for BLE peripherals advertising **Cycling Speed and Cadence Service** (Bluetooth SIG assigned UUID `0x1816`); allow user to **pick a sensor** and **remember** its address for reuse (DataStore).
- **Connection:** `BluetoothGatt` connect → enable **CSC Measurement** notifications (`0x2A5B`); parse wheel/crank fields per [CSC specification](https://www.bluetooth.com/specifications/specs/cycling-speed-and-cadence-profile-1-1/).
- **Math:** Maintain last wheel event time and revolution count; derive **instant speed** and **distance** using **wheel circumference** from Settings or per-session input (mm).
- **Lifecycle:** Start/stop with cardio **ActiveSessionService**; disconnect on session end; handle sensor drop / reconnect.
- **Testing:** Real devices (e.g. common BLE speed sensors); verify behavior when **GPS + CSC** both active.

---

## 6. UX details (reference alignment)

- **Stats row:** Distance (user units), Pace (e.g. min/km or min/mi), Time — same as current manual session summary where applicable.
- **Branding:** optional small app mark on share card (top corner) — design pass later.
- **Relay posting:** reuse patterns from other “share to Nostr” flows in the app if present; otherwise minimal: “Post note” with generated image.

---

## 7. Testing checklist

- [ ] GPS off app-wide → no prompts / no recording.
- [ ] Session without GPS → unchanged flows (pace × time, manual distance).
- [ ] **Bike + BLE CSC:** distance/speed updates during session; saved session includes sensor summary + wheel circumference used.
- [ ] **Bike + BLE + GPS:** merge/display policy works and is documented for users.
- [ ] Long run → memory / DB size; thinning for sync.
- [ ] Share **privacy** image contains no map tiles and no EXIF GPS.
- [ ] Share **map** image includes OSM attribution path compliant with license.

---

## 8. Open decisions (resolve before coding)

1. **Default** for “Record GPS” on first outdoor start: on vs off.
2. **GMS vs AOSP-only** location stack for GrapheneOS / degoogled builds.
3. **Minimum** point interval vs battery (e.g. 2 s vs 5 s).
4. **Nostr:** single daily event vs separate track blob event when track &gt; ~50 KB thinned.
5. **GPS + CSC both on:** which source **wins** for logged distance / pace (sensor vs GPS-derived) — product rule + edge cases (tunnel, bad GPS).
6. **Wheel circumference:** global default vs per-bike presets (road / MTB / trainer).

---

## 9. References in repo

- [PLAN_OF_ACTION.md §2.4 — GPS track and map](PLAN_OF_ACTION.md) (parent spec).
- [PLAN_OF_ACTION.md §7.5 — GPS tracking (app-wide)](PLAN_OF_ACTION.md).
- Bluetooth **Cycling Speed and Cadence** — [Bluetooth SIG profile](https://www.bluetooth.com/specifications/specs/cycling-speed-and-cadence-profile-1-1/) (implementation reference for GATT).
- Reference UX: track-only card (polyline + Distance / Pace / Time, no basemap) — user-provided mockup used to align share-card layout.
