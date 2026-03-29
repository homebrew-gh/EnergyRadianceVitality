# Android Release Signing

This is the safest simple setup for building a real release APK for ERV.

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

## 3. Build the signed release APK

From the repo root:

```bash
./gradlew assembleRelease
```

If signing is configured correctly, the signed APK will be created at:

```text
app/build/outputs/apk/release/app-release.apk
```

## 4. Optional: use environment variables instead

If you do not want a local `keystore.properties` file, the Gradle config also accepts:

- `ERV_RELEASE_STORE_FILE`
- `ERV_RELEASE_STORE_PASSWORD`
- `ERV_RELEASE_KEY_ALIAS`
- `ERV_RELEASE_KEY_PASSWORD`

## 5. Verify the APK signature

You can verify the finished APK with:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 6. Generate the checksum

Before publishing to Zapstore or GitHub Releases:

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

Save that SHA-256 value in your release notes.

## 7. How this fits with Zapstore

- The APK is signed with this Android keystore.
- Zapstore metadata is signed separately with your Nostr identity.
- On first publish, Zapstore links the APK signing certificate to your Nostr identity.
