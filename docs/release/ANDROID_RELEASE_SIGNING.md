# Android Release Signing

This is the safest simple setup for building a real release APK for ERV.

ERV now uses a date-based Android versioning scheme for release builds:

- `versionName`: `YYYY.MM.DD` or `YYYY.MM.DD.N`
- `versionCode`: `YYYYMMDDNN`

Examples:

- first release on March 29, 2026 -> `versionName=2026.03.29`, `versionCode=2026032900`
- second release on the same day -> `versionName=2026.03.29.1`, `versionCode=2026032901`

## What you need

- a dedicated Android release keystore
- a local `keystore.properties` file with the passwords and alias
- the Gradle config in `app/build.gradle.kts` reading those values

The actual keystore and `keystore.properties` must never be committed.

## 1. Create your release keystore

Run this on your machine:

```bash
keytool -genkeypair -v \
  -keystore erv-release.jks \
  -alias erv \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

You will be prompted for:

- keystore password
- key password
- your name / organization details

You can store the resulting `erv-release.jks` anywhere outside the repo. A simple option is a private folder in your home directory.

Back it up carefully. If you lose this keystore, you lose the ability to ship updates under the same Android signing identity.

## 2. Create `keystore.properties`

Copy the example file:

```bash
cp keystore.properties.example keystore.properties
```

Then edit it with your real values:

```properties
storeFile=/absolute/path/to/erv-release.jks
storePassword=your-keystore-password
keyAlias=erv
keyPassword=your-key-password
```

## 3. Pick the release version

By default, Gradle uses the current UTC date and release sequence `0`.

You can inspect the resolved version with:

```bash
./gradlew :app:printReleaseVersion
```

If you need to cut another release on the same date, set one or both of these before building:

```bash
export ERV_VERSION_DATE=20260329
export ERV_VERSION_SEQUENCE=1
```

You can also pass them as Gradle properties instead:

```bash
./gradlew :app:printReleaseVersion -PervVersionDate=20260329 -PervVersionSequence=1
```

## 4. Build the signed release APK

From the repo root:

```bash
./gradlew assembleRelease
```

If signing is configured correctly, the signed APK will be created at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Repeatable release helper

If you do not want to remember the release steps each time, use:

```bash
./prepare-zapstore-release.sh
```

This helper script:

- prints the resolved `versionName` / `versionCode`
- builds the signed release APK
- generates the APK SHA-256
- writes paste-ready release notes to `build/release/release-notes.txt`
- writes the checksum alone to `build/release/app-release.apk.sha256`

Useful options:

```bash
./prepare-zapstore-release.sh --first-release
./prepare-zapstore-release.sh --date 20260329 --sequence 1
```

## Repeatable GitHub release helper

When you want the same APK to appear as the latest GitHub release, use:

```bash
./create-github-release.sh
```

This script is intentionally separate from normal development pushes. It only does anything when you run it yourself, and it asks for confirmation before creating:

- an annotated git tag like `v2026.03.29`
- a pushed remote tag on GitHub
- a GitHub Release with the APK and checksum attached

Useful options:

```bash
./create-github-release.sh --draft
./create-github-release.sh --yes
```

Recommended sequence:

```bash
./prepare-zapstore-release.sh --first-release
./create-github-release.sh
zsp publish --wizard
```

## 5. Optional: use environment variables for signing instead

If you do not want a local `keystore.properties` file, the Gradle config also accepts:

- `ERV_RELEASE_STORE_FILE`
- `ERV_RELEASE_STORE_PASSWORD`
- `ERV_RELEASE_KEY_ALIAS`
- `ERV_RELEASE_KEY_PASSWORD`

## 6. Verify the APK signature

You can verify the finished APK with:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 7. Generate the checksum

Before publishing to Zapstore or GitHub Releases:

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

Save that SHA-256 value in your release notes.

## 8. Publish to Zapstore

Make sure `zapstore.yaml` has your real `npub` and that your screenshots live in `docs/store-images/`.

First publish is usually easiest with the wizard:

```bash
zsp publish --wizard
```

After that, the normal flow can be:

```bash
./prepare-zapstore-release.sh
zsp publish
```

For the very first Zapstore release:

```bash
./prepare-zapstore-release.sh --first-release
zsp publish --wizard
```

Suggested release notes should include:

- resolved `versionName`
- resolved `versionCode`
- APK SHA-256
- link to the matching git tag or commit

## 9. Publish to GitHub

Normal `git push` does not create a GitHub Release.

Only this explicit helper does:

```bash
./create-github-release.sh
```

That separation keeps normal development safe:

- `git push origin main` -> normal code push only
- `./create-github-release.sh` -> intentional GitHub release

## 10. How this fits with Zapstore

- The APK is signed with this Android keystore.
- Zapstore metadata is signed separately with your Nostr identity.
- On first publish, Zapstore links the APK signing certificate to your Nostr identity.
