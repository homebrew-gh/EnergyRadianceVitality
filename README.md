# ERV — Energy Radiance Vitality

A privacy-first Android application for capturing daily health and wellness data. ERV is local first: data stays on your device by default, optional Nostr sync encrypts supported payloads before sending them to your relays, and Android cloud backup / device transfer are disabled for app data so off-device copies use ERV's explicit export and backup flows instead.

The app is no longer just a prototype shell. The current Android build includes working health logs, live workout flows, import/export, data deletion controls, privacy policy screens, Bluetooth sensor support, and release-signing / Zapstore submission prep.

## Features

- **Local-first health logging** — Stretching, weight training, cardio, sauna, cold plunge, light therapy, supplements, and sleep
- **Nostr-native identity** — Sign in with your Nostr key (nsec) or use a remote signer (e.g. [Amber](https://github.com/greenart7c3/Amber))
- **NIP-42 authentication** — Works with private relays that require authentication
- **NIP-44 encryption** — All health data encrypted before leaving your device
- **Live workouts and timers** — Weight training, cardio, and unified routine sessions with foreground-service continuity
- **Bluetooth sensor support** — Heart rate monitors plus cycling speed/cadence sensor support for live workouts
- **Programs and unified workouts** — Plan sessions by day, launch them from the dashboard, and track completion
- **Data control** — Export, backup/restore, per-silo delete, and full reset flows with privacy-first copy
- **Explicit export / restore flows** — Separate data-interchange files from ERV backup JSON used for migration and restore
- **Sun-themed UI** — Muted warm palette (reds, yellows, oranges)

## Current State

- The Android app is actively usable today for local-first health tracking.
- Optional relay sync is implemented for supported encrypted payloads.
- Settings now include privacy policy access, data-management tools, Android permission explanations, and support / contact links.
- Release signing and Zapstore submission prep are documented in-repo.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: Compose UI with repository-driven state and ViewModel-backed live workout flows
- **Local storage**: Jetpack DataStore plus app-private files for local data, exports, and media
- **Nostr**: OkHttp WebSockets, NIP-42 auth, NIP-44 encryption, and parameterized replaceable events for app data
- **Serialization / crypto**: kotlinx.serialization, secp256k1, and Bouncy Castle

## Building

### Prerequisites

- Android Studio (latest stable)
- JDK 17+
- Android SDK 34+

### Local build

```bash
git clone https://github.com/homebrew-gh/EnergyRadianceVitality.git
cd EnergyRadianceVitality
./gradlew assembleDebug
```

### CI

A [GitHub Actions](.github/workflows/android-build.yml) workflow runs on pushes and pull requests for `main` / `master`: it builds the debug APK, runs unit tests, and uploads the debug APK as an artifact.

## Documentation

- [Documentation index](docs/README.md) — Overview of release, policy, import/reference, architecture, and archived planning docs.
- [Plan of action](docs/architecture/PLAN_OF_ACTION.md) — Data model, Nostr event design, and implementation roadmap. **Phase 1 (scaffold and docs)** is complete: Kotlin + Compose + Gradle, README/CONTRIBUTING/CODE_OF_CONDUCT/SECURITY/LICENSE, GitHub Action (build + unit tests), and `UserFeedback` toast helper for publish/sync.
- [Privacy policy](docs/policies/PRIVACY_POLICY.md) — What ERV can guarantee, what depends on the user device, and what third-party relays or tools may still observe or retain.
- [Android release signing](docs/release/ANDROID_RELEASE_SIGNING.md) — How to create a release keystore, configure Gradle, build a signed APK, and prepare the checksum.
- [Zapstore release checklist](docs/release/ZAPSTORE_RELEASE_CHECKLIST.md) — Prioritized trust, privacy, deletion, and release-readiness work for the open-source app store launch.
- [Zapstore store listing draft](docs/release/ZAPSTORE_STORE_LISTING.md) — Paste-ready summary, privacy notes, and support contact copy for the first Zapstore release.
- [Manual release test checklist](docs/release/MANUAL_RELEASE_TEST_CHECKLIST.md) — Focused smoke-test checklist for release candidates before publishing.
- [Protocol graph (WOT + sharing)](docs/architecture/PROTOCOL_GRAPH.md) — Trust-filtered routine discovery, forking, and why a full website may complement the app.
- [Companion web: community benefits & pitfalls](docs/architecture/COMPANION_WEB_COMMUNITY_PITFALLS_BENEFITS.md) — Tradeoffs for fitness promotion and community if a companion site imports routines into user profiles.

## Release Prep

- `zapstore.yaml` is included as a starter config for `zsp publish`.
- Android release builds use date-based versioning: `YYYY.MM.DD` with optional same-day sequence suffix.
- `./prepare-zapstore-release.sh` builds the signed APK, generates the checksum, and writes ready-to-paste release notes.
- `./create-github-release.sh` creates a GitHub release only when you explicitly run it.
- Store screenshots belong in `docs/store-images/`.
- Android release signing setup lives in [docs/release/ANDROID_RELEASE_SIGNING.md](docs/release/ANDROID_RELEASE_SIGNING.md).

## Contact

- **General issues & feedback:** [erv_contact@proton.me](mailto:erv_contact@proton.me)
- **Nostr:** NIP-17 private message to the handle **homebrew_bitcoiner**
- **Security vulnerabilities:** see [SECURITY.md](SECURITY.md) (no public issues for sensitive reports).

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

[MIT](LICENSE) — see [LICENSE](LICENSE) for details.
