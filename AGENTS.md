# Agent guide — ERV (Energy Radiance Vitality)

Instructions for AI coding agents working in this repository.

## Read first

- **[`docs/PHASES.md`](docs/PHASES.md)** — Current roadmap, d-tags, code map. Prefer this over large architecture docs (some are in `.cursorignore` because they freeze Cursor).
- **[`docs/architecture/ATHLETE_CONTEXT_WEB_PREP.md`](docs/architecture/ATHLETE_CONTEXT_WEB_PREP.md)** — Pre-AI web prep: training profile, history, snapshot (W1–W6).
- **[`CONTRIBUTING.md`](CONTRIBUTING.md)** — PR expectations and Android CI commands.

## Project skills (`.cursor/skills/`)

Use these skills for the matching task — read the skill file before implementing.

| Skill | When to use |
|-------|-------------|
| **[erv-build-verify](.cursor/skills/erv-build-verify/SKILL.md)** | Builds, tests, compile fixes, CI, Gradle, Vite, Cargo, Start9 / `.s9pk` |
| **[erv-cross-platform-ui](.cursor/skills/erv-cross-platform-ui/SKILL.md)** | Forms, labels, theme, web + Android UI parity |
| **[erv-nostr-sync-contract](.cursor/skills/erv-nostr-sync-contract/SKILL.md)** | Sync models, d-tags, kind 30078, import/export JSON, web ↔ Android data |
| **[erv-phase-scope](.cursor/skills/erv-phase-scope/SKILL.md)** | Roadmap phases, what not to build yet, silo scoping |
| **[erv-start9-package](.cursor/skills/erv-start9-package/SKILL.md)** | StartOS `.s9pk` build, version bump, sideload updates |
| **[erv-web-companion-patterns](.cursor/skills/erv-web-companion-patterns/SKILL.md)** | Web SPA routing, auth, API, builder layouts, publish flow |
| **[erv-android-compose-patterns](.cursor/skills/erv-android-compose-patterns/SKILL.md)** | Compose UI, navigation, repositories, silo structure |

## Always-on rules (`.cursor/rules/`)

- **`ui-label-title-case.mdc`** — Title-case multi-word field labels and section headers on web and Android.
- **`start9-update-package.mdc`** — Bump StartOS package version on every sideloadable build (applies under `packages/start9/**`).

## Repo layout

| Path | Purpose |
|------|---------|
| `app/` | Android app (Kotlin, Compose, Material 3) |
| `apps/web/web/` | Start9 companion SPA (React, Vite, Tailwind) |
| `apps/web/server/` | Companion backend (Rust, Axum, Nostr) |
| `packages/start9/` | StartOS package build |
| `docs/` | Release, import, architecture specs |

## Build defaults

- **Android PRs:** `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` (matches GitHub Actions).
- **Web changes:** `npm run typecheck` + `npm run build` in `apps/web/web/`.
- **StartOS package:** `./packages/start9/build.sh` — run in an **external terminal**, not inside the agent.

## Heavy builds and IDE freezes

See [`docs/architecture/START9_SCAFFOLD_AUDIT.md`](docs/architecture/START9_SCAFFOLD_AUDIT.md). Build outputs under `app/build/`, `target/`, `node_modules/`, and `.pack/rootfs/` are excluded via `.cursorignore`.
