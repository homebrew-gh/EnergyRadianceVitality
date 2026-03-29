# ERV Android Permissions Guide

Last updated: 2026-03-28

ERV tries to keep permissions narrow and tied to optional features. This page explains what each Android permission is for in plain language.

## Short version

- ERV does not ask for every permission at first launch.
- Some permissions are only relevant if you use certain features.
- If you do not use a feature, you can usually deny its related permission.
- Denying a permission may disable part of ERV, but the rest of the app should still work where possible.

## Permissions ERV uses

### Internet

**Permission:** `INTERNET`

ERV uses internet access for optional network features such as:

- encrypted Nostr relay sync
- relay setup and relay communication
- public social posts to relays
- optional media uploads
- opening or resolving some online integrations

If you use ERV only locally, internet access is still present in the app package, but network features are optional.

### Bluetooth

**Permissions:** `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`

ERV uses Bluetooth permissions for external fitness sensors, such as:

- heart rate monitors
- cycling speed/cadence sensors

These permissions are for sensor discovery and connection. ERV does not need them if you never use Bluetooth accessories.

`BLUETOOTH_SCAN` is declared with `neverForLocation`, which means ERV is not asking for Bluetooth scanning in order to infer your location.

### Location

**Permissions:** `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`

ERV uses location access for outdoor cardio GPS features, such as:

- recording route tracks
- estimating distance from phone GPS
- keeping GPS workout tracking active while the app is backgrounded

If you only log indoor cardio or do not use GPS route tracking, you should not need to grant location access.

### Camera

**Permission:** `CAMERA`

ERV uses the camera for body-tracker photos and other photo capture flows you explicitly choose.

If you do not use photo features, you can deny camera access.

### Notifications

**Permission:** `POST_NOTIFICATIONS`

ERV uses notifications for features that need to stay visible or timely, including:

- active live workout notifications
- cardio timer notifications
- reminders
- return-to-workout actions and related background UX

If you deny notification permission, some ongoing workout and reminder experiences may be reduced or unavailable.

### Foreground services

**Permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`

ERV uses foreground services for time-sensitive active sessions that should continue while the app is backgrounded, including:

- live weight workouts
- active cardio timer sessions
- active unified routine sessions

This is what allows Android to keep those sessions alive with a visible ongoing notification while you switch apps or lock your screen.

### Exact alarms and boot restore

**Permissions:** `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`

ERV uses these for routine reminders:

- scheduling reminders at the time you selected
- restoring reminders after device reboot, time change, timezone change, or app update

If you do not use reminders, these permissions are less relevant to your day-to-day use.

## What ERV does not use these permissions for

ERV does not claim these permissions to run ads, sell behavioral profiles, or operate a first-party cloud tracking system.

That said, if you choose optional third-party services such as Nostr relays, media servers, signers, or AI tools, their own privacy practices are outside ERV's control.

## Your control

You can review Android permission grants in your device settings at any time.

If you revoke a permission later:

- ERV should keep working where that permission is not required
- the related feature may stop working until you grant it again

## Related reading

- `app/src/main/assets/privacy_policy.md`
- `docs/policies/PRIVACY_POLICY.md`
- `docs/release/ZAPSTORE_RELEASE_CHECKLIST.md`
