# ERV Privacy Policy

Last updated: 2026-03-27

Energy Radiance Vitality (ERV) is designed to help you track health and wellness data without requiring a first-party cloud account. This policy explains what ERV can promise, what it cannot promise, and where your data may go when you choose to use optional network features.

## Short version

- ERV is **local first**. Your data is stored on your device.
- ERV does **not** operate its own cloud account system or hosted health database.
- ERV can optionally sync encrypted data to Nostr relays that you choose.
- ERV can export your data to files that you control.
- ERV cannot guarantee that third-party relays, media hosts, signers, or AI tools will delete or protect data the same way ERV does.

## What ERV stores locally

ERV stores the health and wellness information you enter, such as logs, settings, routines, and related metadata, on your device.

This may include:

- training logs
- cardio sessions
- stretching, heat/cold, light therapy, and supplement data
- profile and app settings
- relay configuration and sync state
- optional imported files you choose to merge into ERV

Local device security also depends on your device security. If your device is compromised, rooted, unlocked by another person, or backed up insecurely, ERV cannot guarantee local secrecy.

## No ERV cloud account

ERV does not require you to create an account with us. There is no ERV-operated cloud service that stores your health logs for normal use.

If you do not enable Nostr sync, your data stays local to your device unless you export it or share it yourself.

## Optional Nostr sync

If you sign in with a Nostr identity and enable relay sync, ERV may send encrypted health data to relays you configure.

### What ERV can guarantee

- ERV encrypts supported health payloads before sending them to configured data relays.
- ERV is designed so your health content is not sent to an ERV server, because ERV does not operate one for this purpose.
- You choose which relays to use.

### What ERV cannot guarantee

- ERV cannot guarantee that every relay will retain, replicate, delete, or honor replacement of data in the same way.
- ERV cannot guarantee that relay operators will never log metadata such as your public key, connection times, IP-address-derived information, or traffic patterns.
- ERV cannot guarantee deletion of data already copied, cached, mirrored, or retained by third-party relays.

## Remote signers and third-party services

You may choose to use external tools or services with ERV, such as:

- remote signers
- Nostr relays
- media servers
- AI tools used to transform import/export files

Those systems are not operated by ERV. Their privacy and retention practices are outside our control. Before using them, review their policies and decide whether you trust them with your data or metadata.

## Exported files

ERV can export your data to files for backup, transfer, or inspection.

### What ERV can guarantee

- Export is user-initiated.
- Exported files reflect ERV data in documented formats.

### What ERV cannot guarantee

- Exported files may be plaintext.
- If you store, share, upload, email, or paste exported data elsewhere, ERV cannot control what happens next.

If you need stronger protection for exported files, encrypt them with a tool you trust before sharing or storing them outside your device.

## Imports

ERV imports files that you explicitly choose. Imported files may contain inaccurate, incomplete, or sensitive data. ERV can validate file structure and preview merges, but it cannot guarantee that third-party source data is correct.

## Deletion and data removal

ERV aims to give users strong control over local and synced data, but there are important limits.

### Local data

When ERV deletes data from local storage on your device, ERV can remove the local copy that it controls inside the app.

### Relay data

When ERV asks relays to delete or replace previously published data, that process is best effort only.

ERV cannot guarantee:

- permanent deletion from every relay
- deletion of mirrored or archived copies
- deletion from backups maintained by relay operators
- deletion from systems outside ERV's control

## Analytics, ads, and tracking

ERV does not rely on a first-party advertising network or a first-party behavioral tracking service to provide the core app experience.

If that changes in a future release, this policy should be updated before release.

## Children and medical use

ERV is a self-tracking tool, not a medical device, emergency service, or substitute for professional care. Use your own judgment before storing or sharing sensitive health information.

## Changes to this policy

If ERV's data flows or third-party integrations change materially, this policy should be updated and the in-app copy should match the repository version.

## Contact and source

For source code, release notes, and project documentation, refer to the ERV repository.
