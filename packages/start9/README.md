<p align="center">
  <img src="icon.svg" alt="ERV logo" width="21%">
</p>

# ERV Web Companion on StartOS

> **Upstream docs:** [Energy Radiance Vitality repository](https://github.com/samcornwell/EnergyRadianceVitality)
>
> This package wraps the ERV web companion (`apps/web/`). Behavior not described here matches the upstream project.

Bundles [`apps/web/`](../../apps/web/) into a `.s9pk` for **StartOS 0.4.x**.

The web UI connects to your **existing Nostr relay** (same nsec + relay as the Android app). It does not bundle a relay. On StartOS, **Haven** is the recommended companion relay because it also exposes Blossom media storage for future ERV image backup features.

## Prereqs

1. **start-cli** 0.4+ — [packaging guide](https://docs.start9.com/packaging/0.4.0.x/environment-setup.html)
2. **Node.js 20+**, npm, Docker
3. **Rust** toolchain (for `erv-web` server binary)
4. **squashfs-tools** + **squashfs-tools-ng** (`mksquashfs`, `tar2sqfs`)

## Local dev

```bash
# Terminal 1 — backend (serves built SPA from apps/web/web/dist)
cd apps/web/server && ERV_COOKIE_SECURE=0 cargo run

# Terminal 2 — frontend
cd apps/web/web && npm install && npm run dev
```

Open http://localhost:5173 (Vite proxies `/api` to port 3000).

## Build artifacts

From **repo root** (or use the one-shot script below):

```bash
./packages/start9/scripts/build-artifacts.sh
```

That builds the web SPA + Rust server and stages `apps/web/.pack/` (binary, dist, Dockerfile).

## Package (when start-cli is installed)

**Run in an external terminal** (not inside Cursor agent) — full builds are CPU-heavy.

One command from repo root:

```bash
RELEASE_NOTES="What changed in this build." ./packages/start9/build.sh
# → packages/start9/erv-web_x86_64.s9pk (version auto-bumped for in-place update)
```

Each build **bumps the downstream version** (`0.1.2:0` → `0.1.2:1`) so sideloading updates the existing install without uninstalling. Set `SKIP_VERSION_BUMP=1` only for local repacks that will not be installed.

Or step by step (always from repo root):

```bash
./packages/start9/scripts/build-artifacts.sh
cd packages/start9
npm ci && npm run build   # required before make (produces javascript/index.js)
make x86-import           # → erv-web_x86_64.s9pk (~32 MB)
make verify               # optional sanity check
```

### Install on your StartOS server

**Sideload (recommended first time):** StartOS web UI → **Sideload** → upload `erv-web_x86_64.s9pk`.

**Update an existing install:** Build with `./packages/start9/build.sh` (auto-bumps version), then sideload the new `.s9pk` — no uninstall needed. Or via CLI:

```bash
start-cli package install erv-web 0.1.2:1 --sideload packages/start9/erv-web_x86_64.s9pk
```

Use the version printed at the end of `build.sh`.

**CLI install (same LAN):** Add to `~/.startos/config.yaml`:

```yaml
host: http://your-start9-host.local
```

Then `cd packages/start9 && make install`.

After install: ERV → **Web UI** → Setup → `/app/routines/weight`, `/app/catalog`, etc.

Audit / troubleshooting: [START9_SCAFFOLD_AUDIT.md](../../docs/architecture/START9_SCAFFOLD_AUDIT.md)

## Layout

| Path | Purpose |
|------|---------|
| `startos/` | TypeScript SDK (manifest, main, interfaces) |
| `scripts/` | Host-build + docker-import pack helpers |
| `../../apps/web/server/` | Rust Axum backend (NIP-42/44, kind 30078) |
| `../../apps/web/web/` | React SPA (ERV sun theme) |

Progress checklist: [docs/architecture/START9_COMPANION_V1.md](../../docs/architecture/START9_COMPANION_V1.md)
