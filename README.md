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

## Build

```
./gradlew :app:assembleRelease
```

Without signing credentials, the release APK is unsigned and debug builds use the standard Android
debug key. Neither can update an installed production APK.

### Release signing

Before merging the signing change, disable **Build & Release APK** so the merge does
not publish. In a private directory outside the checkout, use Android Build Tools
35.0.0 to generate the replacement key and create a proof of rotation from the original
release key. Giovanni: the original `keystore/brightsteps.jks` is available in git history
(or your existing offline copy); restore it only into that private directory. Use fresh
passwords when prompted.

```sh
keytool -genkeypair -storetype JKS -keystore brightsteps-new.jks -alias brightsteps-release \
  -keyalg RSA -keysize 3072 -validity 10000

apksigner rotate --out brightsteps.lineage \
  --old-signer --ks /path/to/original/brightsteps.jks --ks-key-alias brightsteps \
  --set-installed-data true --set-rollback false \
  --new-signer --ks brightsteps-new.jks --ks-key-alias brightsteps-release

apksigner lineage --in brightsteps.lineage --print-certs
```

Verify that signer #1's SHA-256 digest matches `signing-lineage-root-fingerprint.txt`,
then replace `signing-fingerprint.txt` with signer #2's SHA-256 digest. Do not change
the original fingerprint. Keep the original private key out of CI; it is only needed
offline to create the lineage.

In **Settings → Secrets and variables → Actions**, set these five secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `brightsteps-new.jks` |
| `SIGNING_LINEAGE_BASE64` | Base64-encoded `brightsteps.lineage` |
| `KEYSTORE_PASSWORD` | New keystore password |
| `KEY_ALIAS` | `brightsteps-release` |
| `KEY_PASSWORD` | New key password |

On Linux, upload the files without printing their contents and enter the other values when prompted:

```sh
base64 -w 0 brightsteps-new.jks | gh secret set RELEASE_KEYSTORE_BASE64 --repo gi-os/BrightSteps
base64 -w 0 brightsteps.lineage | gh secret set SIGNING_LINEAGE_BASE64 --repo gi-os/BrightSteps
gh secret set KEYSTORE_PASSWORD --repo gi-os/BrightSteps
gh secret set KEY_ALIAS --repo gi-os/BrightSteps
gh secret set KEY_PASSWORD --repo gi-os/BrightSteps
```

CI signs with the new key using APK Signature Scheme v3 and verifies the proof of rotation
from the original pin to the new pin. BrightSteps requires Android API 29, so no supported
device needs a v1/v2 signature or the old private key. Android can accept an in-place update,
but a catalog that compares only the current certificate can still hide it from users on
the old signer. Coordinate the BrightMarket catalog/updater transition before re-enabling
publishing, and only re-enable it for an authorized release.

A push to `main` (other than ignored paths) or a manual run of `build.yml` builds,
signs, tags, and publishes. Tag pushes do not trigger it. Branch and PR checks
in `check.yml` publish nothing.

## Support

These apps are free, open, and built on my own time. Sponsorship pays the bills that don't go away: build servers, test hardware, and the crash reporter that keeps them shipping. Donation or not my code is always free for the world to use.

[Sponsor on GitHub](https://github.com/sponsors/gi-os)
