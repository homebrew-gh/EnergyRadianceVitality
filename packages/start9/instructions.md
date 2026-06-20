# ERV on StartOS

1. Install **ERV** from this package (Sideload tab → upload the `.s9pk`, or `make install` from a dev machine on your LAN).
2. Optionally install **Nostr RS Relay** or **Haven** on the same StartOS server.
3. Open the **Web UI** interface from the ERV service page.
4. **Setup** with the same **nsec** and **relay URL** as your Android ERV app.
5. Use **Catalog** to browse/edit weight, stretch, and cardio catalogs (`erv/catalog/*`).
6. Use **Routines** (Weight / Stretch / Cardio) to create, edit, and publish routines.
7. Use **Workouts** to compose circuit / superset sessions and publish `erv/workouts/library`.
8. On your phone: open ERV → sync → check the matching silo under Routines or Training → Workouts.

When **Nostr RS Relay** or **Haven** is installed, ERV detects it automatically and pre-fills setup with the correct relay URL (internal `ws://…startos` for container sync, or the LAN `wss://` URL when available). If neither relay is installed, enter an external `wss://` relay manually.

## Smoke test

1. Create a weight routine with two exercises → confirm success toast shows a Nostr event id.
2. On Android (same nsec + relay): sync → **Weight Training → Routines** → routine appears.
3. Repeat for a stretch or cardio routine if desired.
