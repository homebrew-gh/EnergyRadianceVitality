# Start9 scaffold audit (FiatLife → ERV)

Deep inspection of `packages/start9/` and `apps/web/` packaging after Cursor freezes during local dev and `make x86-import`.

## Root causes of Cursor freezes

### 1. Debian rootfs built inside the repo (critical)

`scripts/build-image-import.sh` falls back to **mmdebstrap/debootstrap** when the quick Docker path is unavailable. It assembles a full Debian rootfs at:

```
apps/web/.pack/rootfs/
```

That directory contains **tens of thousands of files**. Cursor indexes the workspace; a rootfs under the project tree can peg CPU/RAM and freeze the IDE.

**Fix:** build rootfs under `/tmp` (or `$ERV_ROOTFS_DIR`), always delete on exit. Add `apps/web/.pack/rootfs/` to `.cursorignore`.

### 2. Heavy work triggered from the agent / Make

`make x86-import` chains:

1. `build-artifacts.sh` → `npm run build` + **`cargo build --release`** (minutes, high CPU)
2. `docker build` or mmdebstrap
3. `start-cli s9pk pack` → squashfs image work

Running this inside Cursor (especially with file watchers active) competes with the IDE. **Run package builds in an external terminal**, not via the agent.

### 3. Missing SDK build artifacts

`start-cli s9pk list-ingredients` (invoked from `s9pk.mk` on every `make`) requires `packages/start9/javascript/index.js`. Without `npm ci && npm run build` in `packages/start9/` first, start-cli spawns Node, errors, and Make dependency resolution becomes flaky.

### 4. No `.cursorignore` / incomplete `.gitignore`

The repo `.gitignore` only covered Android/Gradle. Build outputs (`app/build/`, `apps/web/web/node_modules/`, `apps/web/server/target/`, `.pack/rootfs/`) were visible to Cursor and bloating git status.

---

## Broken FiatLife leftovers (build/runtime bugs)

| Issue | Location | Impact |
|-------|----------|--------|
| Binary name `erv-web-web` | `startos/main.ts`, `apps/web/.pack/Dockerfile` | Container starts wrong/missing binary |
| Missing `Dockerfile.pack` | `scripts/build-artifacts.sh` line 27 | `cp` fails when refreshing `.pack` |
| Package name `fiatlife-startos` | `packages/start9/package.json` | Confusing; copy-paste not renamed |
| FiatLife icon label | `packages/start9/icon.svg` | Wrong branding |
| FiatLife version graph | `startos/versions/v0.4.0.*.ts` | Release notes describe bills/paycheck/mortgage |
| Missing `tsconfig.json` | `packages/start9/` | `npm run check` / SDK tooling incomplete |
| Missing `package-lock.json` | `packages/start9/` | `npm ci` in README fails |
| Missing `LICENSE` / `instructions.md` | `packages/start9/` | `start-cli s9pk list-ingredients` fails → Make breaks |
| `FL_TEST_*` env vars | `apps/web/server/src/relay_raw.rs` tests | FiatLife test naming |
| Unused CypherLog helpers | `apps/web/server/src/nostr_support.rs` | Dead FiatLife code; **10 build warnings** |
| FiatLife green/dollar icon | `packages/start9/icon.svg` (`#85BB65`, `$`) | Wrong branding |
| `io.nomoxcel.utxo.wallets` test fixture | `nostr_support.rs` test | Leftover nomoxcel d-tag |

All of the above are now removed. `cargo build --release`, `vite build`, and
`tsc --noEmit` (Start9 package) all complete with **zero warnings/errors**.

---

## V1 data contract — verified end-to-end (code level)

The "create on web → see on phone" path is confirmed consistent across both
codebases:

| Step | Web (`apps/web`) | Android (`app/`) | Match |
|------|------------------|------------------|-------|
| d-tag | `WEIGHT_ROUTINES_D_TAG = erv/weight/routines` | `WEIGHT_ROUTINES_D_TAG` (`WeightSync.kt`) | ✓ |
| Kind | 30078 (`KIND_APP_DATA`) | kind 30078 fetch by d-tag | ✓ |
| Envelope | `{ "routines": [...] }` (`routinesPayload`) | `WeightRoutinesPayload(routines)` | ✓ |
| Routine shape | `{id,name,exerciseIds,notes,lastModifiedEpochSeconds}` | `data class WeightRoutine` (same fields) | ✓ |
| Crypto | NIP-44 v2 encrypt-to-self | `signer.decryptFromSelf` | ✓ (same key) |
| Unknown fields | — | `Json { ignoreUnknownKeys = true }` | ✓ tolerant |
| Merge | bumps `lastModifiedEpochSeconds = now`, new UUID | `mergeWeightRoutinesByLastModified` → `local==null ⇒ remote` | ✓ adds routine |
| Exercise IDs | 5 built-ins (`erv-weight-exercise-bench-v1`, …) | `defaultCompoundExercises()` seeds same IDs | ✓ resolves |

---

## Correct build order (external terminal)

```bash
# From repo root — one shot:
./packages/start9/build.sh

# Or step by step:
./packages/start9/scripts/build-artifacts.sh
cd packages/start9
npm ci && npm run build    # → javascript/index.js
make x86-import            # → erv-web_x86_64.s9pk
```

If `docker build` from `.pack` works, mmdebstrap is skipped and rootfs is never created in the repo.

---

## What was fixed in this audit pass

- [x] `erv-web-web` → `erv-web` in `main.ts` and Dockerfiles
- [x] Added `packages/start9/Dockerfile.pack`
- [x] Rootfs moved to temp dir by default in `build-image-import.sh`
- [x] Slim ERV version graph (`0.1.0:0` only)
- [x] Renamed npm package to `erv-startos`
- [x] Added `tsconfig.json`, `.cursorignore`, expanded `.gitignore`
- [x] Added `LICENSE`, `instructions.md` (required by start-cli)
- [x] Generated `package-lock.json` + `javascript/index.js` via `npm install && npm run build`
- [x] Removed all FiatLife dead code from `nostr_support.rs` (zero warnings)
- [x] Replaced FiatLife green/dollar `icon.svg` with ERV sun mark
- [x] Verified web↔Android data contract (table above)
- [x] Added repo-root `packages/start9/build.sh` (cwd-independent build)

## Remaining for a working V1 (integration, not code)

These are **environment/onboarding** items — the code is ready.

- [ ] **Same key on both ends.** Web requires the raw **nsec** for NIP-44
  encrypt-to-self. If the phone signs via Amber (NIP-46), the web still needs
  the matching nsec so ciphertext decrypts (same npub). Confirm the user can
  enter the same nsec in the web companion.
- [ ] **Same relay on both ends.** Phone + StartOS must publish/read the same
  relay. On StartOS, install Haven for the recommended local relay path, or
  point both at an external `wss://`. Web supports multiple relay URLs.
- [ ] **Android sync trigger.** Confirm Settings has a manual "sync now"
  (`WeightSync.fetchFromNetwork`) so the user can pull after publishing.
- [ ] **Host package build.** Run `./packages/start9/build.sh` in an external
  terminal (Docker + start-cli + tar2sqfs). Not from the Cursor agent.
- [ ] **Local smoke test** before packaging: run the prebuilt binary, set up
  with the real nsec+relay, create a routine, confirm it lands on the phone.
