<a href="https://github.com/sponsors/gi-os"><img src="https://raw.githubusercontent.com/gi-os/gi-os/main/images/bright-sponsors.svg" width="100%" alt="Thank you to my sponsors"></a>

# BrightSteps

[**⬇ Download the latest APK**](https://github.com/gi-os/BrightSteps/releases/latest) · free, open source.

A step counter for the Light Phone III. It reads the phone's hardware step counter, keeps a
short history on device, and shows today against a daily goal — no account, no cloud, no Google
Play Services.

Plain sideloaded APK (not a Light SDK tool): the SDK sandbox bans `getSystemService`, and the
`ACTIVITY_RECOGNITION` permission the counter needs is not on its allowlist.

## How it works

- **Hardware counter, not accelerometer math.** `TYPE_STEP_COUNTER` accumulates in the sensor
  hub whether or not the app is running, at near-zero power. BrightSteps never holds a wake lock
  or runs a foreground service — it just reads the counter and lets go.
- **Sampled every ~15 minutes in the background.** A `setAndAllowWhileIdle` alarm chain (the one
  alarm that fires in Doze) snapshots the counter so day boundaries stay accurate even if the app
  is never opened. Each firing arms the next; the chain is rebuilt on boot and after an update.
- **Days are derived, not stored.** The raw readings are the only stored data. Totals per hour
  and per day are computed by a small, tested, Android-free core (`StepMath`): a reboot is read
  as the counter falling, a walk across midnight splits proportionally between the two days,
  DST days still tile hour by hour, and impossible jumps or long gaps are dropped.
- **The counter has no memory.** `TYPE_STEP_COUNTER` remembers nothing across a wipe and has no
  history API, so days before you install BrightSteps are permanently unknowable. History starts
  the day it does.

## Feeds BrightNotebook

BrightSteps exposes a read-only provider on the same bus BrightNotebook already reads for photos,
places and plays:

```
content://com.gios.brightsteps.steps/day/<yyyy-MM-dd>    -> date, total
content://com.gios.brightsteps.steps/hours/<yyyy-MM-dd>  -> hour_start_ms, steps
```

Every caller is checked against an allowlist by package name.

## Install

Grab the latest APK from [Releases](https://github.com/gi-os/BrightSteps/releases) (or via
Obtainium / BrightMarket). On first launch, allow the physical-activity permission — that is the
step counter, and it is plain AOSP, no Google account involved.

## Versions

Versioning is `v1.x.x`; CI stamps the patch number from the build.

### v1.0.0 — first release
- Today's step count against a settable daily goal (goal ring + big number).
- Last-7-days strip, square-root scaled so one big walk doesn't flatten the rest.
- Background sampling every ~15 min via a Doze-proof alarm chain; survives reboot and updates.
- Read-only steps provider for BrightNotebook (daily totals + hourly buckets).

## Build and release signing

`./gradlew :app:assembleRelease` builds an unsigned APK. Debug builds use the standard
Android debug key, which varies across CI runners. Use a local build for repeat debug
installs; a debug APK cannot update a production install. The **Check** workflow tests
signing with disposable keys and never publishes.

Before merging this change, disable [Build & Release APK](https://github.com/gi-os/BrightSteps/actions/workflows/build.yml).
A push to `main` otherwise publishes. Keep it disabled until the rotation and distribution
checks below are done and you intend to publish.

In a private directory outside the checkout, use Android Build Tools 35.0.0. Use the
original `keystore/brightsteps.jks` from git history (or Giovanni’s offline copy) and its
existing passwords only for the offline rotation step. Use fresh passwords for the new key.

```sh
umask 077
keytool -genkeypair -storetype JKS -keystore brightsteps-new.jks -alias brightsteps-release \
  -keyalg RSA -keysize 3072 -validity 10000

apksigner rotate --out brightsteps.lineage \
  --old-signer --ks /path/to/original/brightsteps.jks --ks-key-alias brightsteps \
  --set-installed-data true --set-rollback false \
  --new-signer --ks brightsteps-new.jks --ks-key-alias brightsteps-release

apksigner lineage --in brightsteps.lineage --print-certs
```

Verify signer #1's SHA-256 digest against `signing-lineage-root-fingerprint.txt` and
replace `signing-fingerprint.txt` in this PR with signer #2's SHA-256 digest. Do not
change the original pin. The release won't publish while the pins are still identical.

In [BrightSteps's environment settings](https://github.com/gi-os/BrightSteps/settings/environments),
create an environment named `release`. Under **Deployment branches and tags**, choose
**Selected branches and tags** and add a single **Branch** rule for `main`, with no tag
rules. Add the five secrets below to that environment only. Do not use repository-wide
secrets or upload the original key or its passwords.

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `brightsteps-new.jks` |
| `SIGNING_LINEAGE_BASE64` | Base64-encoded `brightsteps.lineage` |
| `KEYSTORE_PASSWORD` | New keystore password |
| `KEY_ALIAS` | `brightsteps-release` |
| `KEY_PASSWORD` | New key password |

On Linux, stream the files and enter the passwords at the prompts:

```sh
base64 -w 0 brightsteps-new.jks | gh secret set RELEASE_KEYSTORE_BASE64 --env release --repo gi-os/BrightSteps
base64 -w 0 brightsteps.lineage | gh secret set SIGNING_LINEAGE_BASE64 --env release --repo gi-os/BrightSteps
gh secret set KEYSTORE_PASSWORD --env release --repo gi-os/BrightSteps
gh secret set KEY_ALIAS --env release --repo gi-os/BrightSteps --body brightsteps-release
gh secret set KEY_PASSWORD --env release --repo gi-os/BrightSteps
```

CI supplies only the new private key to `scripts/sign-release.sh` and checks that the APK's
v3 lineage links both pins, preserves installed data, and disallows signer rollback. API 29+
does not need v1/v2 signatures. Removing the old key from the branch does not remove it
from Git history or protect installs that still trust it.

Before re-enabling publishing, verify an in-place update from the last shipped build to a
locally signed test APK with a greater `versionCode` on a device with no important step history.
Coordinate the BrightMarket migration: add the exact old-to-new `com.gios.brightsteps`
handover to [the index](https://github.com/gi-os/brightmarket-index/blob/HEAD/scripts/build_index.py)
and ensure [the updater](https://github.com/gi-os/BrightMarket) accepts the installed
predecessor for this transition. The index otherwise keeps the old release, and changing
the catalog pin alone makes the current updater classify old installs as foreign.

## Support

These apps are free, open, and built on my own time. Sponsorship pays the bills that don't go away: build servers, test hardware, and the crash reporter that keeps them shipping. Donation or not my code is always free for the world to use.

[Sponsor on GitHub](https://github.com/sponsors/gi-os)
