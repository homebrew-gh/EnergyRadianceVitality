# ERV Manual Release Test Checklist

Use this checklist on a release candidate before publishing to Zapstore.

## 1. Install and first launch

- Install the release APK on a clean device or emulator.
- Confirm the app launches without crashing.
- Confirm the welcome / login flow is readable and navigable.
- Confirm the app can be used locally without signing into Nostr.

## 2. Account and trust messaging

- Open `Settings`.
- Confirm the bottom `About & contact` section shows GitHub, email, and the Nostr contact handle.
- Open `Account` and confirm the remote-signer warning is visible.
- Open `Privacy Policy` and confirm it includes OS backup behavior, remote signer trust, and data locations.

## 3. Backup, export, and delete safety

- Open `Settings > Data Interchange + Backup`.
- Export one interchange file and one ERV backup JSON.
- Confirm the backup/export wording distinguishes interchange from restore-oriented backup.
- Open `Settings > Data Management` and confirm export is surfaced before destructive actions.
- Walk through a per-silo delete confirmation without completing it, then walk through full reset confirmation without completing it.

## 4. Relay and sync behavior

- If testing with a Nostr identity, configure at least one data relay.
- Create or edit a piece of data that should sync.
- Open `Settings > Relays` and confirm queued / sent / retrying / failed wording is understandable.
- Temporarily take the device offline or disable relays, create another synced change, and confirm the queued explanation still makes sense.
- Reconnect and confirm queued work eventually clears.

## 5. Core workout paths

- Start and finish one weight-training flow.
- Start and finish one cardio flow.
- Open stretching and complete one routine or log flow.
- If using programs or unified routines, launch one planned block and confirm it opens the expected screen.

## 6. Import / restore smoke tests

- Import one supported interchange file and confirm preview-before-merge works.
- Restore one ERV backup JSON on test data and confirm only included sections are replaced.
- Confirm error messages are understandable if you intentionally pick an invalid file.

## 7. Final release checks

- Confirm app version, icon, and app name look correct.
- Confirm privacy policy and README links in the repo match the current app behavior.
- Confirm the release APK is signed correctly.
- Generate and save the APK SHA-256 before publishing.
