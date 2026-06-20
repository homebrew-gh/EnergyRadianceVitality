#!/usr/bin/env bash
# Bump the StartOS package downstream revision so sideloads install as updates.
# Wrapper-only changes: 0.1.2:0 → 0.1.2:1 (edit current version file in place).
set -euo pipefail

PKG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSIONS="$PKG/startos/versions"
INDEX="$VERSIONS/index.ts"
NOTES="${RELEASE_NOTES:-${1:-}}"

if [[ ! -f "$INDEX" ]]; then
  echo "Error: missing $INDEX" >&2
  exit 1
fi

CURRENT_EXPORT="$(grep -E '^\s*current:\s*v_' "$INDEX" | head -1 | sed -E 's/.*current:\s*(v_[0-9_]+).*/\1/')"
if [[ -z "$CURRENT_EXPORT" ]]; then
  echo "Error: could not read current version export from index.ts" >&2
  exit 1
fi

PARTS="${CURRENT_EXPORT#v_}"
IFS='_' read -r MA MI PA DO <<< "$PARTS"
VERSION_FILE="$VERSIONS/v${MA}.${MI}.${PA}.${DO}.ts"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Error: version file not found: $VERSION_FILE" >&2
  exit 1
fi

OLD_VERSION="$(grep -E "^\s*version:\s*'" "$VERSION_FILE" | head -1 | sed -E "s/.*version:\s*'([^']+)'.*/\1/")"
if [[ ! "$OLD_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+):([0-9]+)$ ]]; then
  echo "Error: could not parse version '$OLD_VERSION' in $VERSION_FILE" >&2
  exit 1
fi

UP="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
DOWN="${BASH_REMATCH[4]}"
NEW_DOWN=$((DOWN + 1))
NEW_VERSION="${UP}:${NEW_DOWN}"

if [[ -z "$NOTES" ]]; then
  NOTES="Development build $(date -u +%Y-%m-%d)."
fi

export VERSION_FILE NEW_VERSION NOTES
python3 << 'PY'
import os
import re
from pathlib import Path

path = Path(os.environ["VERSION_FILE"])
new_version = os.environ["NEW_VERSION"]
notes = os.environ["NOTES"].replace("\\", "\\\\").replace("'", "\\'")
text = path.read_text()

text, n = re.subn(
    r"(version:\s*')[^']+(')",
    rf"\g<1>{new_version}\2",
    text,
    count=1,
)
if n != 1:
    raise SystemExit("failed to update version field")

text, n = re.subn(
    r"releaseNotes:\s*\{[^}]*\},",
    f"releaseNotes: {{\n    en_US:\n      '{notes}',\n  }},",
    text,
    count=1,
    flags=re.DOTALL,
)
if n != 1:
    raise SystemExit("failed to update releaseNotes block")

path.write_text(text)
PY

echo ">> bumped StartOS version: $OLD_VERSION → $NEW_VERSION" >&2
echo ">> release notes: $NOTES" >&2
echo "$NEW_VERSION"
