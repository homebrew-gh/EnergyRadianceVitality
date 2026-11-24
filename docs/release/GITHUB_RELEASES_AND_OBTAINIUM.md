# GitHub Releases and Obtainium

ERV release APKs can be built and published entirely on GitHub, and every Android device can pick
them up with [Obtainium](https://github.com/ImranR98/Obtainium). No local build or file transfer is
needed once this is set up.

## How a release works

1. Open the repo on GitHub, go to **Actions > Android release**, and press **Run workflow**.
2. Optionally type release notes. Leave them blank to list the merged pull requests since the last release.
3. The workflow runs the unit tests, builds the signed release APK, checks its signature, tags the
   commit and publishes a GitHub Release with `erv-vYYYY.MM.DD.apk` and its SHA-256 attached.
4. Obtainium sees the new release and offers the update on each device.

The version comes from the UTC date and the tags already on GitHub, using the same scheme as
[Android release signing](ANDROID_RELEASE_SIGNING.md): the first release of a day is
`vYYYY.MM.DD` (versionCode `YYYYMMDD00`), and later ones that day are `vYYYY.MM.DD.1`, `.2` and so
on. Each release therefore has a higher versionCode than the last, which Android and Obtainium need
to treat it as an update. The logic lives in `scripts/ci/next-release-version.sh`.

Releases only happen when you press Run workflow. Pushes and pull requests still run the normal
`Android build` check and publish nothing.

## One-time setup: signing secrets

The workflow signs with the same release keystore you use locally. It needs four repository
secrets under **Settings > Secrets and variables > Actions > New repository secret**:

| Secret | Value |
| --- | --- |
| `ERV_RELEASE_KEYSTORE_BASE64` | The keystore file, base64-encoded (see below) |
| `ERV_RELEASE_STORE_PASSWORD` | `storePassword` from your `keystore.properties` |
| `ERV_RELEASE_KEY_ALIAS` | `keyAlias` from your `keystore.properties` |
| `ERV_RELEASE_KEY_PASSWORD` | `keyPassword` from your `keystore.properties` |

To get the base64 value on Linux, run this and paste the output into the secret:

```bash
base64 -w0 /absolute/path/to/erv-release.jks
```

On macOS use `base64 -i /absolute/path/to/erv-release.jks`.

Storing the keystore as a secret means GitHub holds a copy of your signing key. Secrets are
encrypted and are not shown in logs or to pull requests from forks. Keep your own backup of the
keystore regardless: if it is lost, devices cannot update without reinstalling.

If a secret is missing, the workflow stops at its first step and names the missing secret.

## One-time setup: Obtainium on each device

1. Install Obtainium (from its GitHub releases, F-Droid or IzzyOnDroid).
2. Tap **Add App** and enter `https://github.com/homebrew-gh/EnergyRadianceVitality`.
3. Leave **Include prereleases** off, unless you want releases marked as pre-release.
4. If the repo is private, add a GitHub personal access token with read access under
   Obtainium's GitHub source settings.
5. Tap **Add**. If ERV is already installed, Obtainium tracks it from then on.

### Signature check

Android only installs an update signed with the same key as the installed app. Each release lists
its **signing certificate SHA-256**. If the ERV already on a device was a debug build, or was signed
with another key, the first Obtainium update fails with a signature or "package conflicts" error.
In that case export your data or sync it to a relay first, uninstall ERV, then install from
Obtainium. Later updates install normally.

## Relationship to the local scripts

`prepare-zapstore-release.sh` and `create-github-release.sh` still work for local releases and
Zapstore. The workflow picks the next free version from the tags on GitHub, so a release made with
`create-github-release.sh` (which pushes its tag) is counted. A local release whose tag was never
pushed is not, and could share a version with a later workflow release.
