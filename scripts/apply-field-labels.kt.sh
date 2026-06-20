#!/usr/bin/env bash
# Replace static OutlinedTextField labels with FieldLabel and add import.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMPORT='import com.erv.app.ui.components.FieldLabel'
find "$ROOT/app/src/main/java/com/erv/app" -name '*.kt' -print0 | while IFS= read -r -d '' file; do
  if grep -q 'label = { Text("' "$file"; then
    perl -i -pe 's/label = \{ Text\("/label = { FieldLabel("/g' "$file"
    if ! grep -q 'import com.erv.app.ui.components.FieldLabel' "$file"; then
      perl -i -pe '
        if (!$done && /^import com\.erv\.app\./) {
          print "'"$IMPORT"'\n";
          $done = 1;
        }
      ' "$file"
    fi
  fi
done
