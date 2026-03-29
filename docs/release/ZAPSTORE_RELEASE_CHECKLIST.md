# ERV Zapstore Release Checklist

This checklist is for getting ERV ready for an open-source app store release. It prioritizes user trust, data ownership, and clear expectations over adding more logging silos.

## Must ship before release

- [x] Add a **Delete Data** flow in Settings.
  - Scope should be explicit:
    - Delete local data from this device
    - Stop future relay sync
    - Attempt relay cleanup for previously published encrypted data
  - Copy must be honest that relay deletion is **best effort**, not guaranteed.
- [x] Add a **Privacy Policy** screen in-app and link it from Settings.
- [x] Publish a **repo privacy policy** and mirror the same guarantees in the store listing.
- [x] Add a **full local reset** flow for users who want to leave ERV or start over.
- [x] Make **export everything** easy to find and verify before any destructive action.
- [x] Show **relay sync status** clearly:
  - queued
  - sent
  - retrying
  - failed
- [x] Settings now explains pending/offline relay uploads in plain language and labels pending items as queued / retrying / failed.
- [x] Review all **Android permissions** and explain each one in plain language.
- [x] **Audit Android Backup and device-transfer exposure** (`android:allowBackup`, Android 12+ **data extraction rules**).
  - ERV now disables Android cloud backup and device-to-device extraction for app data via manifest + XML rules.
  - Privacy policy and Settings copy now tell users to use ERV export / backup flows instead of OS backup.
- [ ] **Zapstore release integrity** (independent store = users must trust the binary).
  - Ship a correct **`zapstore.yaml`** (and any required **NIP-C1 / signing-certificate binding** per [Zapstore publish docs](https://zapstore.dev/docs/publish)).
  - Publish **verifiable release artifacts**: e.g. SHA-256 of the APK (and/or tag ↔ reproducible build notes) so users can confirm what they installed matches what you released.
  - Protect the **Android signing keystore** and the **Zapstore publish key** with the same seriousness as an nsec (separate storage, no repo commits).
- [x] Surface a **responsible disclosure / security contact** where store users will see it (About or Settings **and** store listing), aligned with **SECURITY.md** (private advisory, not public issues for sensitive reports).
  - Repo copy is in **SECURITY.md**, **README.md** (Contact), the in-app **Privacy Policy**, the Settings footer, and `docs/release/ZAPSTORE_STORE_LISTING.md` (`erv_contact@proton.me`, NIP-17 DM **homebrew_bitcoiner**).
- [x] Update public docs so they match the current implementation.
  - README tech stack
  - release notes / changelog
  - store description
  - import/export wording should distinguish **data interchange** from **backup and restore**

## Should ship soon after release

The remaining items in this section are intentionally deferred until after the first Zapstore release unless release testing uncovers a blocker.

- [x] Add **per-silo delete** actions so users can remove one category without wiping everything.
- [ ] Add **selective sync** so some categories can stay device-only.
- [ ] Add **per-relay controls** and clearer relay role explanations.
- [ ] Add a **backup before delete/import** confirmation path.
- [ ] Add **account/key migration guidance**:
  - local key to Amber
  - changing relays
  - moving to a new device
- [x] Document the **remote signer trust boundary** (e.g. Amber, NIP-46): a malicious or compromised signer can see or abuse **signing and decryption** on the user’s behalf. In-app or policy copy should say users should only connect signers they trust, and that ERV has no way to audit third-party signer code.
- [ ] Add an optional **privacy hardening** setting (or document OS behavior if you defer implementation):
  - **Block screenshots and screen recording** (`FLAG_SECURE`) for the whole app or for sensitive surfaces (login, key display, exports), for shared-device and shoulder-surfing risk.
- [ ] Evaluate an optional **app lock** (biometric / device credential) before opening the app or before **export / show key** flows—common expectation for health and key-bearing apps on shared phones.
- [x] Add an **export verification** note so users understand exports are plaintext unless they encrypt them separately.
- [x] Add a **data locations** note:
  - device storage
  - relays
  - optional media servers

## Good polish for store readiness

- [ ] Improve first-run onboarding around:
  - local-first usage
  - optional Nostr sync
  - what relay sync means
- [ ] Add a short **What ERV does not do** section in-app.
- [ ] Improve **empty states** and recovery states when relay auth or signer setup fails.
- [x] Add a clearer **offline / pending upload** explanation.
- [ ] Expand **accessibility checks** for text scaling, touch targets, and screen reader labels.
- [ ] Add clearer **error messages** for import/export failures.
- [x] Add a basic **manual test checklist** for release candidates.

## Nice to have after launch

- [ ] Search and filtering across logs.
- [ ] Tags and freeform notes across more data silos.
- [ ] Better duplicate detection during import.
- [ ] Retention controls for large local datasets.
- [ ] Optional encrypted export archives.

## Messaging guardrails

Use this wording consistently across ERV, docs, and store listings:

- ERV is **local first**.
- Sync is **optional**.
- Relay sync is **encrypted**, but relay operators can still observe metadata such as timing and your public key.
- ERV can **attempt** to remove or replace relay data, but cannot guarantee every relay permanently deletes retained copies.
- Exported files are **plaintext** unless the user encrypts them separately.
- ERV has **no first-party cloud account** and no server-side account deletion flow.
- **OS backup and transfer** (if enabled) may copy app data outside ERV’s export/delete flows unless excluded by backup rules—say so clearly if that remains true after audit.
- **Third-party Nostr signers** are chosen by the user; trust and risk for those apps are **outside ERV’s control**.

## Recommended implementation order

1. Privacy policy
2. Delete local data
3. Best-effort relay delete / replace flow
4. Full app reset
5. Android backup / data-extraction audit (manifest rules + policy copy)
6. Zapstore `zapstore.yaml`, signing binding, and release checksums
7. Better sync status and copy
8. README and store listing cleanup
