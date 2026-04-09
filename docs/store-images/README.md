# Store images (Zapstore)

## App icon

- **`store-icon.png`** — 512×512 listing icon, generated from the same artwork as the in-app launcher (`ic_launcher` vectors). Referenced explicitly in `zapstore.yaml` as `icon:` because **APK-only icons are vector/adaptive** and `zsp`’s extractor often cannot produce a correct bitmap (you may see a generic placeholder such as a Google “G” on the listing).
- **`store-icon.svg`** — source vector used to regenerate the PNG if you change launcher art.

## Screenshots

Put **phone UI** images under **`screenshots/`** (see `screenshots/README.md`). In **`zapstore.yaml`**, add **one `images:` line per file** (`screenshot-1.png`, …). **`zsp` does not expand globs or regex** — a single pattern string is treated as a literal path, so screenshots never upload that way. Keeping screenshots in this subfolder avoids duplicating **`store-icon.png`** as a screenshot.

Formats: `.png`, `.jpg`, `.jpeg`, `.webp`

After adding or changing images: commit, push, and **re-publish** with `zsp publish` (or your usual release flow) so Zapstore picks up new media.
