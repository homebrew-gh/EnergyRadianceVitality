#!/usr/bin/env bash
# Prints the next ERV release date and same-day sequence as shell assignments:
#   ERV_VERSION_DATE=YYYYMMDD
#   ERV_VERSION_SEQUENCE=N
#
# The first release of a UTC day is vYYYY.MM.DD (sequence 0); later ones that day are
# vYYYY.MM.DD.1, .2, ... This matches the versionName / versionCode scheme in
# app/build.gradle.kts, so every release has a higher versionCode than the one before.
#
# Usage: next-release-version.sh [YYYYMMDD]   (tags are read from the local git repo)
set -euo pipefail

date_compact="${1:-$(date -u +%Y%m%d)}"
if [[ ! "$date_compact" =~ ^[0-9]{8}$ ]]; then
  echo "Date must be YYYYMMDD, got: $date_compact" >&2
  exit 1
fi
date_dotted="${date_compact:0:4}.${date_compact:4:2}.${date_compact:6:2}"

next=0
while IFS= read -r tag; do
  [[ -z "$tag" ]] && continue
  if [[ "$tag" == "v$date_dotted" ]]; then
    seq=0
  elif [[ "$tag" =~ ^v${date_dotted//./\\.}\.([0-9]{1,2})$ ]]; then
    seq=$((10#${BASH_REMATCH[1]}))
  else
    continue
  fi
  (( seq + 1 > next )) && next=$((seq + 1))
done < <(git tag --list "v$date_dotted*")

if (( next > 99 )); then
  echo "More than 100 releases on $date_dotted; the versionCode scheme allows 0-99 per day." >&2
  exit 1
fi

echo "ERV_VERSION_DATE=$date_compact"
echo "ERV_VERSION_SEQUENCE=$next"
