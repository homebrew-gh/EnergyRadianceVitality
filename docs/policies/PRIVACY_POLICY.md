# ERV Privacy Policy

Last updated: 2026-03-29

Energy Radiance Vitality (ERV) is designed to help you track health and wellness data without requiring a first-party cloud account. This policy explains what ERV can promise, what it cannot promise, and where your data may go when you choose to use optional network features.

## Short version

- ERV is **local first**. Your data is stored on your device.
- ERV does **not** operate its own cloud account system or hosted health database.
- ERV can optionally sync encrypted data to Nostr relays that you choose.
- ERV can export your data to files that you control before destructive actions.
- ERV cannot guarantee that third-party relays, media hosts, signers, or AI tools will delete or protect data the same way ERV does.

## What ERV stores locally

ERV stores the health and wellness information you enter, such as logs, settings, routines, reminder schedules, body-tracker photos, and related metadata, on your device.

This may include:

- training logs
- cardio sessions
- stretching, heat/cold, light therapy, and supplement data
- programs, unified routines, reminders, and body-tracker entries
- profile and app settings
- relay configuration and sync state
- optional imported files you choose to merge into ERV

Local device security also depends on your device security. If your device is compromised, rooted, unlocked by another person, or otherwise exposed outside ERV, ERV cannot guarantee local secrecy.

## OS backup and device transfer

ERV disables Android cloud backup and Android device-to-device data extraction for app data.

That means:

- your health logs, local key material, relay auth, and sync state are not intended to ride through Google/Android backup
- moving to a new device should use ERV's own export and restore flows instead of relying on OS-level transfer
- ERV's delete and reset flows are easier to describe honestly because they do not compete with a hidden OS backup copy

This policy is intentional for privacy. If you want an ERV copy outside the app, use ERV export files that you control directly.

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

If you use a remote signer, that signer may be able to sign or decrypt on your behalf depending on how you authorize it. ERV cannot audit third-party signer code and cannot guarantee how a signer app handles secrets, prompts, logs, or decrypted material. Only connect signers you trust.

## Exported files

ERV can export your data to files for backup, transfer, or inspection. ERV now also prompts you to export before section deletes and full local reset actions.

### What ERV can guarantee

- Export is user-initiated.
- Exported files reflect ERV data in documented formats.

### What ERV cannot guarantee

- Exported files are plaintext unless you encrypt them separately.
- If you store, share, upload, email, or paste exported data elsewhere, ERV cannot control what happens next.

If you need stronger protection for exported files, encrypt them with a tool you trust before sharing or storing them outside your device.

## Where data can live

Depending on the features you use, your data may live in more than one place:

- on your device inside ERV's local storage
- on the Nostr relays you configure for encrypted sync
- on optional media servers if you choose media features that publish or upload files
- in plaintext export files if you create them

ERV does not operate a first-party health cloud, but third-party relays, signers, media hosts, and tools may still observe or retain metadata or content according to their own policies.

## Imports

ERV imports files that you explicitly choose. Imported files may contain inaccurate, incomplete, or sensitive data. ERV can validate file structure and preview merges, but it cannot guarantee that third-party source data is correct.

## Deletion and data removal

ERV aims to give users strong control over local and synced data, but there are important limits.

### Local data

When ERV deletes data from local storage on your device, ERV can remove the local copy that it controls inside the app. This includes section-level deletes for supported data silos and a full local reset flow for users who want to leave ERV or start over.

When you choose full deletion, ERV also clears the saved local sign-in state on that device and returns you to the initial login flow.

### Relay data

When ERV asks relays to delete or replace previously published data, that process is best effort only. ERV currently does this by publishing replacement data for known encrypted relay payloads where possible, not by guaranteeing that every relay permanently erases old retained copies.

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

For questions or issues with the application:

- **Email:** erv_contact@proton.me
- **Nostr:** NIP-17 private message to the handle **homebrew_bitcoiner**

For source code, release notes, and project documentation, refer to the ERV repository.

For security vulnerabilities, follow [SECURITY.md](../../SECURITY.md) (do not use a public issue for sensitive reports).
