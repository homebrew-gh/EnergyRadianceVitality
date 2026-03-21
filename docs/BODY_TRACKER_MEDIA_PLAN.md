# Body Tracker — sensitive photo storage (planning only)

**Status:** Not implemented. This file captures product and engineering direction for a future **Body Tracker** (or similar) feature where users photograph progress and **must not** have those images tied to public Nostr posts or shared Blossom hosts used for workouts.

ERV already distinguishes **public** vs **private** Blossom origins in Settings (`blossomPublicServerOrigin` vs `blossomPrivateServerOrigin`). Private Blossom is intended for this class of media; **local phone storage** and **user-connected cloud** are additional options users may prefer.

---

## 1. Goals

1. Let users capture or import photos for longitudinal body / progress tracking inside the app.
2. Ensure **no accidental publication**: these assets are never uploaded to the **public** Blossom server, never embedded in kind 1 (or other social) notes, and never synced via paths meant for “shareable” media unless the user explicitly opts in (default should be **off**).
3. Offer a **clear choice of storage location** so users understand where files live and who can access them.

---

## 2. Storage destination options (user-selectable)

| Option | Summary | Pros | Cons / notes |
|--------|---------|------|----------------|
| **On-device** | App-private or user-visible folder (e.g. app storage + optional export). | No account; works offline; user keeps full control on device. | Lost if app cleared / phone lost unless user backs up; large libraries use device space. |
| **Private Blossom** | Upload to `blossomPrivateServerOrigin` (PUT `/upload`, kind `24242`), same protocol as public Blossom but **different host** the user trusts (often self-hosted). | Sync across devices that use the same key + server; user-chosen infra. | Requires server; URLs must stay out of public notes; key must match server auth. |
| **Cloud account (e.g. Google Drive)** | Per-user OAuth + folder chosen by user (or app-created folder). | Familiar backup / multi-device; user may already pay for storage. | OAuth scope UX, API quotas, Google Play services / account dependency; more compliance surface. |

**UX idea:** After the user taps “Add photo” (or on first use of Body Tracker), show a **short explainer** and let them pick a **default** destination: *This device*, *Private Blossom*, or *Google Drive* (and optionally “ask every time”). Settings should mirror this with the ability to change default and migrate or leave existing photos where they are (migration TBD).

---

## 3. Prompting behavior

- **Per capture (optional mode):** “Store this photo on…” with last-used or default pre-selected.
- **Settings default:** Single source of truth for new captures; advanced users can override per shot if we add that toggle.
- **Copy:** Explicit language that **public workout / social uploads use a different server** and body photos **are not posted to Nostr** unless we add a separate, explicit future flow (default off).

---

## 4. Engineering notes (when implementing)

- **Private Blossom:** Reuse `BlossomUploader.uploadBlob` against `UserPreferences.blossomPrivateServerOrigin`; never call that path from `publishWorkoutNote` or kind 1 flows.
- **On-device:** Prefer **app-specific storage** for maximum privacy; optional **MediaStore** / SAF export if users want gallery backups.
- **Google Drive:** Android **Drive API** or **SAF** to a folder the user picks; consider scoped storage and background upload constraints.
- **Metadata:** Strip or minimize EXIF if storing in locations where leakage matters; document behavior.
- **Encryption at rest (optional later):** For device storage, consider encrypting files with a key derived from device credential / passphrase if threat model warrants it.

---

## 5. Non-goals (initial Body Tracker release)

- Automatic upload of body photos to **public** Blossom or NIP-96.
- Sharing body-tracker images to relays without a dedicated, explicit consent flow.
- Replacing user’s choice with a single hard-coded backend.

---

## 6. Dependencies on existing ERV pieces

- `UserPreferences.blossomPrivateServerOrigin` — already persisted; UI exists under Blossom settings.
- `BlossomUploader` — protocol implementation; parameterize base URL for private vs public callers.
- Future: a small **BodyTrackerRepository** (or similar) that records `{ localUri | remoteDescriptor, storageKind, createdAt, … }` without putting URLs in Nostr events.

---

*Last updated: planning draft for future implementation.*
