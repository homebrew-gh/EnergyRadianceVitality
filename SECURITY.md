# Security

ERV handles sensitive health data. Security is a priority.

## Reporting a vulnerability

If you believe you have found a security vulnerability, please report it responsibly:

- **Do not** open a public GitHub issue for security-sensitive bugs.
- **Email:** [erv_contact@proton.me](mailto:erv_contact@proton.me)
- **Nostr:** NIP-17 private message to the handle **homebrew_bitcoiner**
- Or open a **private** security advisory: [GitHub Security Advisories](https://github.com/homebrew-gh/EnergyRadianceVitality/security/advisories/new)

We will acknowledge your report and work with you on a fix and disclosure timeline.

## Security model

- **Keys** — Nostr private keys (nsec) or remote signer (e.g. Amber) are used only for signing and decryption. Keys are not sent to our servers (we have no servers).
- **Data** — Health data is encrypted with NIP-44 before being sent to relays. Only the key holder can decrypt.
- **Relays** — The app is designed to work with your own relay(s). Use NIP-42 where your relay requires authentication.
- **Local storage** — Sensitive data in local DB should be protected by Android Keystore where applicable.

## Supported versions

We provide security updates for the latest release. Please upgrade to the newest version when possible.
