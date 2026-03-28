# ERV — Energy Radiance Vitality

A privacy-first Android application for capturing daily health and wellness data. All data is stored locally on device and synced to your personal Nostr relay using NIP-44 encryption. No cloud accounts, no big-tech health silos—your data stays yours.

## Features

- **Local-first health logging** — Stretching, weight training, cardio, sauna, cold plunge, light therapy, supplements, and sleep
- **Nostr-native identity** — Sign in with your Nostr key (nsec) or use a remote signer (e.g. [Amber](https://github.com/greenart7c3/Amber))
- **NIP-42 authentication** — Works with private relays that require authentication
- **NIP-44 encryption** — All health data encrypted before leaving your device
- **Sun-themed UI** — Muted warm palette (reds, yellows, oranges)

## Tech Stack (Planned)

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **Local storage**: Room Database + DataStore
- **Nostr**: NIP-42 (auth), NIP-44 (encryption), parameterized replaceable events for app data

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

A [GitHub Actions](.github/workflows/android-build.yml) workflow runs on every push to `main`/`master`: it builds the debug APK and runs unit tests, then uploads the APK as an artifact. The workflow requires a Gradle-based Android project and [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) in the repo (the workflow will fail until the app module and `gradlew` are added).

## Documentation

- [Plan of action](docs/PLAN_OF_ACTION.md) — Data model, Nostr event design, and implementation roadmap. **Phase 1 (scaffold and docs)** is complete: Kotlin + Compose + Gradle, README/CONTRIBUTING/CODE_OF_CONDUCT/SECURITY/LICENSE, GitHub Action (build + unit tests), and `UserFeedback` toast helper for publish/sync.
- [Privacy policy](docs/PRIVACY_POLICY.md) — What ERV can guarantee, what depends on the user device, and what third-party relays or tools may still observe or retain.
- [Zapstore release checklist](docs/ZAPSTORE_RELEASE_CHECKLIST.md) — Prioritized trust, privacy, deletion, and release-readiness work for the open-source app store launch.
- [Protocol graph (WOT + sharing)](docs/PROTOCOL_GRAPH.md) — Trust-filtered routine discovery, forking, and why a full website may complement the app.
- [Companion web: community benefits & pitfalls](docs/COMPANION_WEB_COMMUNITY_PITFALLS_BENEFITS.md) — Tradeoffs for fitness promotion and community if a companion site imports routines into user profiles.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

[MIT](LICENSE) — see [LICENSE](LICENSE) for details.
