---
name: erv-build-verify
description: Runs the correct ERV build and test commands for Android, web, Rust server, and StartOS packages. Use when building, verifying CI parity, fixing compile errors, preparing PRs, or when the user mentions gradle, assembleDebug, vite, cargo, start9, s9pk, or build.sh.
---

# ERV build verify

Run the **smallest relevant verification** for what changed. Do not claim "build passes" without running commands.

## By surface

### Android (required for most app PRs)

From repo root:

```bash
./gradlew assembleDebug --no-daemon
./gradlew testDebugUnitTest --no-daemon
```

This matches [`.github/workflows/android-build.yml`](../../.github/workflows/android-build.yml) — the only CI gate today.

### Web SPA

```bash
cd apps/web/web
npm ci
npm run typecheck
npm run build
```

### Rust server

```bash
cd apps/web/server
cargo build --release
```

Run after changes under `apps/web/server/src/`.

### Web + server artifacts (StartOS staging)

From repo root:

```bash
./packages/start9/scripts/build-artifacts.sh
```

Builds SPA + server and stages `apps/web/.pack/`.

### StartOS `.s9pk` (full package)

**Run in an external terminal**, not via the Cursor agent — CPU-heavy (Docker/mmdebstrap, squashfs, file watchers).

From repo root:

```bash
RELEASE_NOTES="Short summary of this build." ./packages/start9/build.sh
```

- Auto-bumps downstream version for in-place sideload updates (see `.cursor/rules/start9-update-package.mdc`).
- Output: `packages/start9/erv-web_x86_64.s9pk`
- Set `SKIP_VERSION_BUMP=1` only for local repacks that will not be installed.

Step-by-step reference: [`packages/start9/README.md`](../../packages/start9/README.md), [`docs/architecture/START9_SCAFFOLD_AUDIT.md`](../../docs/architecture/START9_SCAFFOLD_AUDIT.md).

## What to run when

| Changed paths | Minimum verify |
|---------------|----------------|
| `app/**` | Gradle assemble + unit tests |
| `apps/web/web/**` | `npm run typecheck` + `npm run build` |
| `apps/web/server/**` | `cargo build --release` |
| `packages/start9/**` or packaging scripts | `build-artifacts.sh`; full `build.sh` only when user wants a package |
| Cross-cutting (web + Android sync) | All three: Gradle, web typecheck/build, cargo if server touched |

## Do not

- Run `./packages/start9/build.sh` or `make x86-import` inside the agent unless the user explicitly asks and accepts long runtime.
- Index or commit build outputs — they are in `.gitignore` / `.cursorignore` (`app/build/`, `target/`, `node_modules/`, `.pack/rootfs/`, `*.s9pk`).
- Assume web or StartOS are CI-gated; only Android is automated today. Call out the gap when relevant.

## Local dev (not verification)

```bash
# Terminal 1
cd apps/web/server && ERV_COOKIE_SECURE=0 cargo run

# Terminal 2
cd apps/web/web && npm run dev
```

Vite proxies `/api` to port 3000.
