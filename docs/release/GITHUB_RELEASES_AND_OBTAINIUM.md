# GitHub Releases and Obtainium

ERV release APKs can be built and published entirely on GitHub, and every Android device can pick
them up with [Obtainium](https://github.com/ImranR98/Obtainium). No local build or file transfer is
needed once this is set up.

The StartOS package has its own workflow in the same repo; see [StartOS releases](#startos-releases).
The two never share tags: Android releases are `vYYYY.MM.DD[.N]` titled `ERV YYYY.MM.DD`, StartOS
releases are `start9-vX.Y.Z.N` titled `ERV Start9 X.Y.Z:N`.

## Setting up the secrets (quick steps)

Do this once, on the PC that holds your keystore and StartOS key. With the
[GitHub CLI](https://cli.github.com/) signed in (`gh auth login`), each secret is one command run
from your ERV checkout, and nothing passes through the clipboard:

```bash
# Android: values come from your keystore.properties (storeFile, storePassword, keyAlias, keyPassword)
base64 -w0 /absolute/path/to/erv-release.jks | gh secret set ERV_RELEASE_KEYSTORE_BASE64
gh secret set ERV_RELEASE_STORE_PASSWORD   # prompts; paste storePassword
gh secret set ERV_RELEASE_KEY_ALIAS        # prompts; paste keyAlias
gh secret set ERV_RELEASE_KEY_PASSWORD     # prompts; paste keyPassword

# StartOS: the developer key your past sideloads were signed with
ls ~/.startos/                             # developer.key.pem (older start-cli) or id.key.pem
gh secret set ERV_STARTOS_DEV_KEY < ~/.startos/developer.key.pem
```

Check with `gh secret list`. Without the CLI, add the same five under
**Settings > Secrets and variables > Actions > New repository secret**, as described below.

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

## StartOS releases

**Actions > Start9 release > Run workflow** does what `RELEASE_NOTES="..." ./packages/start9/build.sh`
does locally, then publishes the result:

1. Builds the web app and Rust server, and bumps the package version (`0.1.2:30` to `0.1.2:31`).
2. Commits the bump to the branch it ran on (`Bump StartOS package to 0.1.2:31`), so the next
   build, local or on GitHub, counts up from there.
3. Packs and signs `erv-web_x86_64.s9pk` and publishes it as release `start9-v0.1.2.31` with its
   SHA-256. These releases are never marked Latest.
4. You download the `.s9pk` from the release and sideload it in StartOS as before.

The release notes you type are what StartOS shows for the update.

### Signing key

StartOS only installs an update that is signed by the same developer key as the installed
package. Put the key you have been building with into the `ERV_STARTOS_DEV_KEY` secret: the whole
contents of `~/.startos/developer.key.pem` (or `id.key.pem` with newer `start-cli`), including the
`BEGIN` and `END` lines. If a run builds with a different key, StartOS will refuse it as an update.

### start-cli version

The workflow installs the newest `start-cli` release. If that ever breaks the build, set a
repository variable `START_CLI_VERSION` (Settings > Secrets and variables > Actions > Variables) to
the release tag that matches what you use locally (`start-cli --version`).

### Mixing local and GitHub builds

Both bump the same version file. After a local `build.sh`, commit and push the bumped
`packages/start9/startos/versions/` file before the next GitHub build, or GitHub will build a version
StartOS has already seen. Pull after a GitHub build before building locally. If `main` is protected
against pushes from GitHub Actions, the bump commit fails and the run stops before publishing.

### Keep Obtainium on Android releases

Obtainium looks at every release in the repo. So it never tries a StartOS release, open ERV in
Obtainium, tap the settings (gear) icon, and set **Filter release titles by regular expression** to:

```
^ERV [0-9]
```

That matches `ERV 2026.11.24` and skips `ERV Start9 0.1.2:31`.

