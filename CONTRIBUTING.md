# Contributing to ERV (Energy Radiance Vitality)

Thank you for considering contributing to ERV. This document explains how to get started and what we expect.

## Code of conduct

By participating, you agree to uphold our [Code of Conduct](CODE_OF_CONDUCT.md).

## How to contribute

- **Bug reports** — Open an issue describing the bug, steps to reproduce, and your environment (device, Android version).
- **Feature ideas** — Open an issue with a clear description and, if possible, how it fits the app’s privacy-first, Nostr-based design.
- **Code** — Fork the repo, create a branch, make your changes, and open a pull request.

## Development setup

1. Clone your fork and add the upstream remote:
   ```bash
   git clone https://github.com/YOUR_USERNAME/EnergyRadianceVitality.git
   cd EnergyRadianceVitality
   git remote add upstream https://github.com/homebrew-gh/EnergyRadianceVitality.git
   ```
2. Open the project in Android Studio and sync Gradle.
3. Ensure `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass before submitting a PR (same as CI).

## Pull request guidelines

- Keep PRs focused; prefer several small PRs over one large one.
- Follow existing code style and architecture (see [docs/architecture/PLAN_OF_ACTION.md](docs/architecture/PLAN_OF_ACTION.md) for structure).
- Add or update tests where relevant.
- Update documentation if you change behavior or add features.

## Project structure and scope

Health categories (Stretching, Weight Training, Cardio, Sauna, Cold Plunge, Light Therapy, Supplements, Sleep) are developed as separate silos. When contributing a new category or feature, keep it scoped to one silo where possible and follow the shared Nostr event and encryption patterns described in the plan of action.

## Questions

Open a [GitHub Discussion](https://github.com/homebrew-gh/EnergyRadianceVitality/discussions) or an issue if something is unclear.

Thanks for contributing.
