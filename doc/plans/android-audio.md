# Android in-stream audio

Implements the plan prepared in the PR #50 review chat, on merged DAT 1.0.1
(`5fdb1ff`). Keep the existing Dart API and iOS implementation. No version bump.

## Preflight evidence and scope

- DAT 1.0.1 camera AAR exposes `Stream.audioStream`, `AudioFrame.buffer`,
  `AudioCodec.PCM(AudioSampleRate, numberOfChannels)` and nullable
  `StreamConfiguration.audioCodec`. Core exposes `Permission.MICROPHONE`.
- Meta DAT v1.0 audio guide: PCM is 16-bit; 16 kHz mono matches the existing Dart
  contract. In-stream audio is development/beta-channel only, not production.
- Core/camera AAR manifests request Bluetooth, not RECORD_AUDIO. Inspection finds
  no AudioRecord or RECORD_AUDIO references in their class files. This path receives
  glasses PCM over DAT, not through Android microphone capture/HFP.
- Retain `connectedDevice` foreground service. Do not add phone microphone
  permissions speculatively. Actual Pixel foreground/background/lock delivery must
  still be checked; static artifact inspection is not hardware evidence.
- Sources: https://wearables.developer.meta.com/docs/develop/dat/set-up-release-channels/
  and https://developer.android.com/develop/background-work/services/fgs/service-types
  (connectedDevice versus microphone). DAT audio API was also verified using Meta's
  versioned documentation search and the shipped 1.0.1 AAR.

## Implementation

1. Generalize camera permission handling for microphone; one mutex, typed SDK error
   mapping, unique activity-result IDs, bounded waits, cleanup on permanent detach.
   Rotation preserves the pending request for the reattached activity.
2. Default video-only; enable PCM16/16kHz/mono for `audio: true`. Reject changing
   audio configuration on a live stream until stop. Attach collector before start.
3. Copy valid buffer bytes before suspension; sequential awaited platform-thread
   delivery bounds bridge work without sampling. Listener/session generations and
   terminal-state checks reject stale callbacks. Invalidate and cancel before stop.
4. Add native configuration, permission, buffer and actual plugin lifecycle tests;
   require nonempty passing JUnit reports in CI. Retain Dart/iOS regression checks.
5. Update docs/changelog and prepare a separate PR. Background interruption
   investigation remains separate; do not claim this feature resolves it.

## Pixel hardware QA procedure

Use a Pixel paired to compatible Meta glasses, with Meta AI Developer Mode, DAT
installed, and a development/beta channel. Connect USB debugging and authorize host.

1. Request microphone permission. Check denial returns false, then grant and verify
   camera plus PCM arrive through the public Dart APIs (payload byte counts and
   signal level; do not retain recordings by default).
2. Cancel/relisten and stop/restart repeatedly. Expect no audio after cancellation
   or completed stop, fresh audio on restart, and no PCM in video-only sessions.
3. Background streaming ON: Home and 30-second lock/unlock; record video/audio
   continuity and every SDK error. OFF: expect intentional stop and no further PCM.
4. Disconnect/reconnect glasses and retry. Confirm a fresh session and permission
   failure mapping. Check permission activity rotation/timeout cleanup if practical.
5. Stop capture, disable background streaming and restore the normal example.

Mock/unit tests do not establish glasses-microphone fidelity or lock reliability.

## Automated validation

- Android debug and release APKs build. Existing Gradle/AGP/Kotlin deprecation
  warnings remain; no toolchain upgrade is included.
- 28 native tests pass: 8 existing foreground tests plus 20 audio/configuration/
  permission/lifecycle checks. CI asserts every new suite ran without skips.
- Core Dart: 30 tests pass; lib analysis/format pass. Dart changes are documentation
  only, so hot reload is not applicable. The native Android QA app was rebuilt.
- Existing iOS teardown/wiring harness and all 15 audio checks pass.
- Initial test failures were fixture issues: coroutine debug names include a suffix
  (fixed by comparing thread identity), and a JVM plugin test needed a test Main
  dispatcher. Both corrected and passing.

## Physical evidence (2026-10-16)

Pixel 8a, Android 17, paired Ray-Ban Meta / Headliner 00C1, DAT 1.0.1.
The initial `datAppOnTheGlassesUpdateRequired` failure was resolved by the user
updating DAT on the glasses. Registration and microphone grant succeeded.
A temporary entrypoint in ignored `example/build/` counts public Dart callbacks,
PCM bytes/peaks and gaps; it does not retain audio or video payloads.

- Foreground audio + video: over three minutes of delivery; 2,777 audio chunks /
  5,687,296 bytes before restart, nonzero PCM peak 5,672, maximum observed audio
  callback gap 397 ms. This verifies delivery and signal, not subjective fidelity.
- Cancel/relisten: zero callbacks in the two-second cancellation window, then
  44 fresh callbacks in three seconds after resubscribing.
- Background enabled: Home followed by more than 30 seconds screen-off/dozing;
  both counters continued, maximum audio gap 362 ms. Foreground preview returned
  live. This is one trial, not a long-duration reliability guarantee.
- Explicit stop after that trial: zero audio callbacks during the two-second
  post-completion window.
- Video-only restart: video arrived while an attached audio listener received
  zero PCM callbacks. A subsequent delayed audio restart also delivered fresh PCM.
- Background disabled: Home emitted `stoppedForBackground` followed by `stopped`;
  returning to foreground did not resume capture. Audio stayed at 166 callbacks
  across subsequent observations, and the explicit-stop check again reported zero
  callbacks after completion.
- Rapid stop/start failed once: the new stream stayed STARTING with no video or
  audio; native DAT logs reported a LOW-to-MEDIUM Bluetooth link-switch timeout.
  The QA app stopped updating while stop was pending; it was relaunched. Relaunch
  restored audio/video. Another MEDIUM-to-LOW timeout appeared after a successful
  stop, followed by device reconnection and a successful delayed video-only start.
  These logs identify the failing transport stage, not a proven root cause or
  proof that this is pre-existing. Rapid-restart reliability remains unresolved.

Permission denial, activity rotation/timeout and stale-result behavior have unit
coverage but were not all reproduced physically. No production-channel support
is claimed. Background interruption investigation remains a separate task.

Capture was stopped, background streaming disabled, and the normal example was
rebuilt and reinstalled in place, preserving app data. Raw QA logs and the original
APK backup remain local under `/tmp/android-dat-audio-qa/`; no recordings were saved.
