# Store images (Zapstore)

## App icon

- **`store-icon.png`** — 512×512 listing icon, generated from the same artwork as the in-app launcher (`ic_launcher` vectors). Referenced explicitly in `zapstore.yaml` as `icon:` because **APK-only icons are vector/adaptive** and `zsp`’s extractor often cannot produce a correct bitmap (you may see a generic placeholder such as a Google “G” on the listing).
- **`store-icon.svg`** — source vector used to regenerate the PNG if you change launcher art.

## Screenshots

Put **phone UI** images under **`screenshots/`** (see `screenshots/README.md`). `zapstore.yaml` uses a glob on that folder only so the **icon file is not** duplicated as a “screenshot.”

Formats: `.png`, `.jpg`, `.jpeg`, `.webp`

After adding or changing images: commit, push, and **re-publish** with `zsp publish` (or your usual release flow) so Zapstore picks up new media.
