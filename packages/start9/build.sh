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

echo ">> packing x86_64 .s9pk"
make x86-import

echo ">> done: $PKG/erv-web_x86_64.s9pk"
