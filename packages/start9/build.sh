#!/usr/bin/env bash
# One-shot Start9 package build from repo root (cwd-independent).
set -euo pipefail

PKG="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$PKG/../.." && pwd)"

echo ">> repo root: $ROOT"
echo ">> building web + server artifacts"
"$PKG/scripts/build-artifacts.sh"

echo ">> building StartOS SDK bundle"
cd "$PKG"
if [[ -f package-lock.json ]]; then
  npm ci
else
  echo ">> no package-lock.json — running npm install"
  npm install
fi
npm run build

if [[ "${SKIP_VERSION_BUMP:-0}" != "1" ]]; then
  echo ">> bumping package version (update-capable sideload)"
  chmod +x "$PKG/scripts/bump-version.sh"
  BUILT_VERSION="$("$PKG/scripts/bump-version.sh" "${RELEASE_NOTES:-${1:-}}")"
  npm run build
fi

echo ">> packing x86_64 .s9pk"
make x86-import

if [[ -z "${BUILT_VERSION:-}" ]]; then
  CURRENT_EXPORT="$(grep -E '^\s*current:\s*v_' "$PKG/startos/versions/index.ts" | sed -E 's/.*current:\s*(v_[0-9_]+).*/\1/')"
  PARTS="${CURRENT_EXPORT#v_}"
  IFS='_' read -r MA MI PA DO <<< "$PARTS"
  BUILT_VERSION="$(grep -E "^\s*version:\s*'" "$PKG/startos/versions/v${MA}.${MI}.${PA}.${DO}.ts" | sed -E "s/.*'([^']+)'.*/\1/")"
fi
echo ">> done: $PKG/erv-web_x86_64.s9pk (erv-web v${BUILT_VERSION})"
echo ">> update install: start-cli package install erv-web ${BUILT_VERSION} --sideload $PKG/erv-web_x86_64.s9pk"
