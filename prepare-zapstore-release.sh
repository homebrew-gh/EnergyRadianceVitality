#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
OUTPUT_DIR="$REPO_ROOT/build/release"
NOTES_FILE="$OUTPUT_DIR/release-notes.txt"
CHECKSUM_FILE="$OUTPUT_DIR/app-release.apk.sha256"

release_date="${ERV_VERSION_DATE:-}"
release_sequence="${ERV_VERSION_SEQUENCE:-}"
publish_hint="zsp publish"

usage() {
  cat <<'EOF'
Usage: ./prepare-zapstore-release.sh [options]

Options:
  --date YYYYMMDD        Override the release date used for versioning.
  --sequence N           Override the same-day release sequence (0-99).
  --first-release        Print `zsp publish --wizard` as the next step.
  --help                 Show this help text.

Environment variables:
  ERV_VERSION_DATE       Same as --date
  ERV_VERSION_SEQUENCE   Same as --sequence

What this script does:
  1. Resolves the Android release version
  2. Builds the signed release APK
  3. Generates the APK SHA-256 checksum
  4. Writes paste-ready release notes to build/release/release-notes.txt
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --date)
      release_date="${2:-}"
      shift 2
      ;;
    --sequence)
      release_sequence="${2:-}"
      shift 2
      ;;
    --first-release)
      publish_hint="zsp publish --wizard"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${ERV_RELEASE_STORE_FILE:-}" && ! -f "$REPO_ROOT/keystore.properties" ]]; then
  echo "Missing signing config. Add keystore.properties or ERV_RELEASE_* environment variables first." >&2
  exit 1
fi

if [[ ! -f "$REPO_ROOT/zapstore.yaml" ]]; then
  echo "Missing zapstore.yaml in repo root." >&2
  exit 1
fi

if ! command -v sha256sum >/dev/null 2>&1; then
  echo "sha256sum is required but not installed." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

export ERV_VERSION_DATE="${release_date}"
export ERV_VERSION_SEQUENCE="${release_sequence}"

pushd "$REPO_ROOT" >/dev/null

version_output="$(./gradlew :app:printReleaseVersion --quiet)"
printf '%s\n' "$version_output"

version_name="$(printf '%s\n' "$version_output" | awk -F= '/ERV versionName=/{print $2}')"
version_code="$(printf '%s\n' "$version_output" | awk -F= '/ERV versionCode=/{print $2}')"

if [[ -z "$version_name" || -z "$version_code" ]]; then
  echo "Failed to resolve release version from Gradle output." >&2
  exit 1
fi

./gradlew assembleRelease --no-daemon

if [[ ! -f "$APK_PATH" ]]; then
  echo "Expected APK not found at $APK_PATH" >&2
  exit 1
fi

apk_sha256="$(sha256sum "$APK_PATH" | awk '{print $1}')"
printf '%s\n' "$apk_sha256" > "$CHECKSUM_FILE"

cat > "$NOTES_FILE" <<EOF
First public ERV release.

Local-first Android health tracking with optional encrypted Nostr sync for supported data. Includes workouts, body tracking, import/export, privacy policy, and data reset/delete controls.

Version: $version_name
SHA-256: $apk_sha256
Source: https://github.com/homebrew-gh/EnergyRadianceVitality
EOF

cat <<EOF

Release artifacts ready.

APK: $APK_PATH
Checksum: $apk_sha256
Checksum file: $CHECKSUM_FILE
Release notes: $NOTES_FILE

Next step:
  $publish_hint
EOF

popd >/dev/null
