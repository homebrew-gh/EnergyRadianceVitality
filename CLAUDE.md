@AGENTS.md

# Claude Code notes

`AGENTS.md` (imported above) is shared with Cursor. Claude-specific equivalents of the Cursor config live under `.claude/`; keep both copies in sync when editing either.

| Cursor | Claude Code |
|--------|-------------|
| `.cursor/skills/<name>/SKILL.md` | `.claude/skills/<name>/SKILL.md` (same 7 `erv-*` skills, invocable as `/erv-build-verify` etc.) |
| `.cursor/rules/ui-label-title-case.mdc` (always apply) | `.claude/rules/ui-label-title-case.md` (always loaded) |
| `.cursor/rules/start9-update-package.mdc` (glob `packages/start9/**`) | `.claude/rules/start9-update-package.md` (`paths: packages/start9/**`) |
| `.cursorignore` | Secrets denied in `.claude/settings.json`; build outputs and heavy docs below |

## Build and verify

Run the smallest check that covers what changed (details in `/erv-build-verify`). Don't say a build passes unless you ran it.

| Changed | Run |
|---------|-----|
| `app/**` | `./gradlew assembleDebug --no-daemon` and `./gradlew testDebugUnitTest --no-daemon` (the CI gate) |
| `apps/web/web/**` | `cd apps/web/web && npm run typecheck && npm run build` |
| `apps/web/server/**` | `cd apps/web/server && cargo build --release` |
| `packages/start9/**` | `./packages/start9/scripts/build-artifacts.sh` |

Heavy or release steps the user runs in their own terminal, never from the agent unless explicitly asked:

- `RELEASE_NOTES="..." ./packages/start9/build.sh` builds the StartOS `.s9pk`. It auto-bumps the version.
- `./prepare-zapstore-release.sh` builds the signed release APK (it needs `keystore.properties`).
- `./create-github-release.sh` publishes a GitHub release.

## Files to avoid

- **Secrets:** don't read, print or commit `keystore.properties`, `*.jks` / `*.keystore` / `*.p12`, `.android/`, or `apps/web/server/data/cookie.key`. Use `keystore.properties.example` for the expected keys.
- **Build outputs:** don't search or read `.gradle/`, `app/build/`, `build/`, `apps/web/.pack/`, `apps/web/server/target/`, `node_modules/`, `dist/`, `packages/start9/javascript/`, or `*.s9pk`.
- **Huge docs:** `docs/archive/PLAN_OF_ACTION.md` and `docs/architecture/WORKOUT_PLAN_EDITOR_SPEC.md`. Grep these for targeted sections instead of reading them whole; start from `docs/PHASES.md`.
