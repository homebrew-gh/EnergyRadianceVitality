#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
OUTPUT_DIR="$REPO_ROOT/build/release"
NOTES_FILE="$OUTPUT_DIR/release-notes.txt"
CHECKSUM_FILE="$OUTPUT_DIR/app-release.apk.sha256"

draft_release="false"
auto_confirm="false"

usage() {
  cat <<'EOF'
Usage: ./create-github-release.sh [options]

Options:
  --draft     Create the GitHub release as a draft.
  --yes       Skip the interactive confirmation prompt.
  --help      Show this help text.

Before running:
  1. Run ./prepare-zapstore-release.sh
  2. Make sure you are on the commit you want to release
  3. Make sure GitHub CLI (`gh`) is installed and authenticated

What this script does:
  1. Reads the prepared release notes and version
  2. Creates an annotated git tag like v2026.03.29
  3. Pushes that tag to origin
  4. Creates a GitHub Release with the APK and checksum attached

This script only runs when you call it explicitly.
Normal git pushes do not create GitHub releases.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --draft)
      draft_release="true"
      shift
      ;;
    --yes)
      auto_confirm="true"
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

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required but was not found." >&2
  echo "Install it from https://cli.github.com/ and run: gh auth login" >&2
  exit 1
fi

if [[ ! -f "$APK_PATH" || ! -f "$NOTES_FILE" || ! -f "$CHECKSUM_FILE" ]]; then
  echo "Missing prepared release artifacts." >&2
  echo "Run ./prepare-zapstore-release.sh first." >&2
  exit 1
fi

pushd "$REPO_ROOT" >/dev/null

version_name="$(awk -F': ' '/^Version: /{print $2}' "$NOTES_FILE")"
release_title="ERV $version_name"
release_tag="v$version_name"
head_commit="$(git rev-parse --short HEAD)"
current_branch="$(git rev-parse --abbrev-ref HEAD)"
origin_main_commit="$(git rev-parse --short origin/main 2>/dev/null || true)"
meaningful_status="$(git status --porcelain -- . ':(exclude).gradle' ':(exclude)app/build' ':(exclude)build/release')"

if [[ -z "$version_name" ]]; then
  echo "Could not read Version from $NOTES_FILE" >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$release_tag" >/dev/null; then
  echo "Tag $release_tag already exists locally." >&2
  exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/$release_tag" >/dev/null 2>&1; then
  echo "Tag $release_tag already exists on origin." >&2
  exit 1
fi

if [[ -n "$meaningful_status" ]]; then
  echo "Refusing to create a release with uncommitted source/doc changes:" >&2
  printf '%s\n' "$meaningful_status" >&2
  echo "Commit or stash those changes first, then rerun this script." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated. Run: gh auth login" >&2
  exit 1
fi

cat <<EOF
About to create a GitHub release with:

Title:   $release_title
Tag:     $release_tag
Branch:  $current_branch
Commit:  $head_commit
APK:     $APK_PATH
Notes:   $NOTES_FILE

origin/main currently points to: ${origin_main_commit:-unknown}
EOF

if [[ "$head_commit" != "$origin_main_commit" && -n "$origin_main_commit" ]]; then
  cat <<EOF

Warning: HEAD does not match origin/main.
This is okay if you intentionally want to release the current commit,
but normal development pushes do not create releases automatically.
EOF
fi

if [[ "$auto_confirm" != "true" ]]; then
  printf "\nType RELEASE to continue: "
  read -r confirmation
  if [[ "$confirmation" != "RELEASE" ]]; then
    echo "Aborted."
    exit 1
  fi
fi

git tag -a "$release_tag" -m "$release_title"
git push origin "refs/tags/$release_tag"

gh_args=(
  release create "$release_tag"
  "$APK_PATH"
  "$CHECKSUM_FILE"
  --title "$release_title"
  --notes-file "$NOTES_FILE"
)

if [[ "$draft_release" == "true" ]]; then
  gh_args+=(--draft)
fi

gh "${gh_args[@]}"

cat <<EOF

GitHub release created successfully.

Tag: $release_tag
Title: $release_title
EOF

popd >/dev/null
