# BrightSteps

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

CI builds and releases on every push to `main`. Unit tests (`StepMath`) gate the build. The
signing key is committed on purpose and treated as public — Android identifies the app by
package + certificate, and the real protection is the fingerprint check in CI, not a hidden key.
