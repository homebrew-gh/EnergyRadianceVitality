# ERV Zapstore Store Listing Draft

Use this as the starting point for the first Zapstore release listing. Keep it aligned with the in-app privacy policy, `README.md`, and `SECURITY.md`.

## Name

ERV

## Summary

Local-first Android health tracker with optional encrypted Nostr sync.

## Description

ERV helps you track health and wellness data on your own device without a first-party cloud account.

What ERV supports today:

- weight training
- cardio
- stretching
- sauna and cold plunge
- light therapy
- supplements
- programs and unified routines
- body tracking

Privacy and ownership model:

- ERV is local first.
- Sync is optional.
- Supported health payloads are encrypted before relay sync.
- ERV does not operate its own hosted health-data cloud.
- Android cloud backup and device transfer are disabled for app data; use ERV export / backup files when you want an off-device copy.
- Exported files are plaintext unless you encrypt them separately.
- Relay operators may still observe metadata such as timing and your public key.
- ERV can attempt to remove or replace synced relay data, but cannot guarantee every relay permanently deletes retained copies.
- Third-party remote signers, relays, and media hosts are outside ERV's control.

Import / export wording:

- Data interchange is for AI, coach, and script workflows.
- ERV backup JSON is for migration, archival, and restore.

## Support and contact

- Email: `erv_contact@proton.me`
- Nostr: NIP-17 DM to `homebrew_bitcoiner`
- Security issues: use the contact methods in `SECURITY.md` or the repository's private security advisory flow, not a public issue

## Suggested release-note footer

- Source: `https://github.com/homebrew-gh/EnergyRadianceVitality`
- Privacy policy: `docs/PRIVACY_POLICY.md`
- Security policy: `SECURITY.md`
