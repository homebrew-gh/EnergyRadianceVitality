# ERV Zapstore Release Checklist

This checklist is for getting ERV ready for an open-source app store release. It prioritizes user trust, data ownership, and clear expectations over adding more logging silos.

## Must ship before release

- [ ] Add a **Delete Data** flow in Settings.
  - Scope should be explicit:
    - Delete local data from this device
    - Stop future relay sync
    - Attempt relay cleanup for previously published encrypted data
  - Copy must be honest that relay deletion is **best effort**, not guaranteed.
- [ ] Add a **Privacy Policy** screen in-app and link it from Settings.
- [ ] Publish a **repo privacy policy** and mirror the same guarantees in the store listing.
- [ ] Add a **full local reset** flow for users who want to leave ERV or start over.
- [ ] Make **export everything** easy to find and verify before any destructive action.
- [ ] Show **relay sync status** clearly:
  - queued
  - sent
  - retrying
  - failed
- [ ] Review all **Android permissions** and explain each one in plain language.
- [ ] Update public docs so they match the current implementation.
  - README tech stack
  - release notes / changelog
  - store description

## Should ship soon after release

- [ ] Add **per-silo delete** actions so users can remove one category without wiping everything.
- [ ] Add **selective sync** so some categories can stay device-only.
- [ ] Add **per-relay controls** and clearer relay role explanations.
- [ ] Add a **backup before delete/import** confirmation path.
- [ ] Add **account/key migration guidance**:
  - local key to Amber
  - changing relays
  - moving to a new device
- [ ] Add an **export verification** note so users understand exports are plaintext unless they encrypt them separately.
- [ ] Add a **data locations** note:
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
- [ ] Add a clearer **offline / pending upload** explanation.
- [ ] Expand **accessibility checks** for text scaling, touch targets, and screen reader labels.
- [ ] Add clearer **error messages** for import/export failures.
- [ ] Add a basic **manual test checklist** for release candidates.

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

## Recommended implementation order

1. Privacy policy
2. Delete local data
3. Best-effort relay delete / replace flow
4. Full app reset
5. Better sync status and copy
6. README and store listing cleanup
