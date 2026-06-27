---
name: erv-start9-package
description: Builds and ships the ERV StartOS .s9pk package with correct version bumps, release notes, and sideload update flow. Use when packaging StartOS, sideloading, bump-version, erv-web.s9pk, start-cli, packages/start9, or StartOS companion deployment.
---

# ERV StartOS package

Bundles `apps/web/` (Rust server + React SPA) into a `.s9pk` for **StartOS 0.4.x**. The web UI uses the user's **existing Nostr relay** — it does not bundle a relay.

Also see `.cursor/rules/start9-update-package.mdc` (glob rule under `packages/start9/**`).

## Prereqs

- **start-cli** 0.4+ — [StartOS packaging guide](https://docs.start9.com/packaging/0.4.0.x/environment-setup.html)
- Node.js 20+, npm, Docker, Rust toolchain
- **squashfs-tools** + **squashfs-tools-ng** (`mksquashfs`, `tar2sqfs`)

## One-shot build (preferred)

**Run in an external terminal** — not via the Cursor agent (CPU-heavy, competes with IDE).

From repo root:

```bash
RELEASE_NOTES="Short summary of this build." ./packages/start9/build.sh
```

What `build.sh` does:

1. `./packages/start9/scripts/build-artifacts.sh` — SPA + server → `apps/web/.pack/`
2. `npm ci && npm run build` in `packages/start9/` — produces `javascript/index.js` (required before `make`)
3. **`scripts/bump-version.sh`** — bumps downstream revision (`0.1.2:0` → `0.1.2:1`) unless `SKIP_VERSION_BUMP=1`
4. `make x86-import` — output `packages/start9/erv-web_x86_64.s9pk`

Prints install command at end: `erv-web vX.Y.Z:N`.

## Version bump rules

StartOS only upgrades in place when the new package has a **higher version**. Re-sideloading the same version forces uninstall first.

**Wrapper-only changes** (web UI + server binary): bump **downstream** in the current file under `packages/start9/startos/versions/` (e.g. `v0.1.2.0.ts`).

Edit `releaseNotes.en_US` to describe what changed. `bump-version.sh` updates version string and notes when `RELEASE_NOTES` is set.

Set `SKIP_VERSION_BUMP=1` only for local repacks that will **not** be installed over an existing package.

## Install / update

**UI:** StartOS → **Sideload** → upload `erv-web_x86_64.s9pk`.

**CLI (same LAN):**

```bash
start-cli package install erv-web 0.1.2:1 --sideload packages/start9/erv-web_x86_64.s9pk
```

Use the version printed by `build.sh`. ARM hardware: `make arm-import` in `packages/start9/`.

**First-time CLI config** (`~/.startos/config.yaml`):

```yaml
host: http://YOUR-START9-HOST.local
```

Then `cd packages/start9 && make install`.

## Schema migrations (rare)

Most ERV bumps are wrapper-only. If user data / store schema must migrate:

1. Rename current version file to match old version (e.g. `v0.1.2.0.ts`)
2. Add it to `other` in `startos/versions/index.ts`
3. Create new current version file with `up` / `down` migrations

See [StartOS Versions docs](https://docs.start9.com/packaging/0.4.0.x/versions.html).

## Layout

| Path | Purpose |
|------|---------|
| `packages/start9/startos/` | TypeScript SDK (manifest, main, interfaces) |
| `packages/start9/scripts/` | Host-build + docker-import helpers |
| `apps/web/server/` | Rust Axum backend |
| `apps/web/web/` | React SPA |

Progress: [`docs/architecture/START9_COMPANION_V1.md`](../../docs/architecture/START9_COMPANION_V1.md). Troubleshooting: [`docs/architecture/START9_SCAFFOLD_AUDIT.md`](../../docs/architecture/START9_SCAFFOLD_AUDIT.md).

## Do not

- Ship a sideloadable rebuild without bumping version
- Skip release notes on builds the user will install
- Run full `build.sh` inside the agent unless explicitly requested
- Create rootfs under the repo — build uses temp dir; `apps/web/.pack/rootfs/` is in `.cursorignore`

## Related skill

For Gradle / Vite / Cargo verification without packing: **erv-build-verify**.
