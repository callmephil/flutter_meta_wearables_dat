# DAT 1.0.1 update plan and verification

Target: both Flutter packages **1.0.1 stable**, explicitly selected by the maintainer.
Base: `bf3f9051556d971591215746d0b042f17cbe2e18` (main, includes PR #50).
No merge, tag, or publication is authorized by this workflow.

## Evidence and release triage

- [iOS changelog](https://github.com/facebook/meta-wearables-dat-ios/blob/bbae597/CHANGELOG.md): no developer-facing changes in 1.0.1.
- [Android changelog](https://github.com/facebook/meta-wearables-dat-android/blob/f4371bc/CHANGELOG.md): two streaming fixes.
- iOS binary tags: `1.0.0` (`1f38beecba83c4c8b5e343540f9cd615323ab19a`) → `1.0.1` (`0bd369298eef6a980ad0bdb6c8a5502f2c02a08c`). Tags contain binaries; source/sample comparison uses main release commits `122fd16` → `bbae597`.
- Android release commits: `d3159f738676221181577dd745d83d10b6ab46ba` → `f4371bc9f90249d51eb07d7f8a61e8d829294f16`; Maven Central AARs inspected for all three modules at both versions.
- All three iOS device `.swiftinterface` files are byte-identical to 1.0.0. Deployment floor remains 17.2. SourceKit generated interfaces retain doc comments and comparable symbol counts (Core 214, Camera 93, MockDevice 184 declarations). Only Core's documentation for `datAppOnTheGlassesUpdateRequired` changes, clarifying navigation and creation of a fresh session after updating.
- Android `javap -public` on every class: core (2,578 classes) and mock (448) unchanged; camera 682 → 683 classes, only internal `AsyncFrameHandler` signatures change. The new internal feed runnable and nonblocking queue polling agree with the sample's decoder fix. No plugin-used public API changes.
- iOS native skill bodies and CameraAccess source are unchanged; plugin metadata and the sample SDK pin change. Android skills only update the dependency pin; CameraAccess switches from blocking polling to pairing queued frames with available decoder input buffers.

### iOS

| Category | Item | Relevance and evidence | Action |
| --- | --- | --- | --- |
| Added | None listed or found in the three shipped interfaces | Not relevant; identical interfaces | No action |
| Changed | Core generated documentation clarifies DAT glasses-app update recovery | Already covered by the existing navigation API and fresh-session flow | Refresh snapshot |
| Fixed | None listed | No developer-facing fix claimed by upstream | Adopt 1.0.1 binaries; do not claim the known lock issue is fixed |
| Removed | None listed or found | All plugin-referenced interfaces preserved | No action |
| Excluded | Display and other experimental capability modules | No new 1.0.1 scope; not part of these packages | Keep excluded |

### Android

| Category | Item | Relevance and evidence | Action |
| --- | --- | --- | --- |
| Added | None listed in public API | `javap` confirms no public SDK surface addition used by plugin | No new Dart/native API |
| Changed | None listed in public API | Internal camera decoder changes only | Dependency bump |
| Fixed | HEVC buffer corruption causing sporadic freezes | Relevant SDK streaming implementation; plugin delivers raw I420 but SDK owns its transport/decoder | Adopt SDK fix; hardware verification required, no claim all freezes are fixed |
| Fixed | Stream lag/stutter performance issues | Relevant; binary `AsyncFrameHandler` and CameraAccess sample use nonblocking feed logic | Adopt SDK fix without copying SDK internals |
| Removed | None listed in public API | Only internal getter/synthetic accessors change; not referenced by plugin | No action |
| Excluded | Display and other experimental capability modules | Outside this patch update | Keep excluded |

## Implementation plan (written before tracked implementation changes)

1. Replace all three vendored xcframeworks from the verified staged 1.0.1 payload, then thin simulator slices. Update both Android `ext.mwdat_version` values. No Swift/Kotlin runtime implementation change is forced by the inspected API.
2. Refresh `doc/ios/MWDATCore.swift`; Camera/MockDevice generated snapshots are unchanged and need no artificial diff.
3. Bump both pubspecs and podspecs to 1.0.1. Move PR #50's existing Unreleased entries into the core 1.0.1 section, retaining microphone streaming and both teardown/subscription fixes. Also record already-merged PR #52's Android example JPEG orientation/sharing fix, which is unreleased since v1.0.0. Add the mock changelog entry describing SDK alignment and unchanged API.
4. Sweep README, mock README, llms.txt, AGENTS.md, consumer agent guides, Dart API documentation, CLAUDE.md and maintainer guidance. Update current version/install snippets and correct stale claims that iOS audio is unexposed; preserve historical version notes and the unresolved iOS lock limitation. This is documentation only; Android audio remains unsupported.
5. Example: no new UI or runtime change for DAT 1.0.1. Refresh only intentional local-package versions in its lockfile; preserve unrelated SDK transitives. Do not import the separate diagnostics branch.
6. In a separate tooling commit, remove the workflow's stale hardcoded RC target, document inspection of both reference clones after divergence, make the SourceKitten YAML-file invocation explicit, and replace the maintainer guide's delete-before-extract recipe with staged validation.

## Verification plan

- Official Dart static-analysis skill; prefer MCP analysis for core, mock and example. Run both package suites and existing example tests.
- Native iOS lifecycle/audio regression harness from PR #50.
- Clean unsigned release iOS example build (SwiftPM) and scratch consumer build (CocoaPods), verifying both pods. Preserve global Flutter SwiftPM setting (`true` at start).
- Android release APK; plugin unit tests and example photo-sharing tests from PR #52.
- Both publish dry-runs; version/artifact/source checks and `git diff --check`.
- PR creation, current-head CI inspection, and fresh-context plugin-pr-review agent. Address substantiated findings and re-review, maximum three rounds.
- Only comments/documentation change under lib, so no hot reload/restart is required. No physical-device result is implied by build or unit-test success.

## Manual hardware handoff

Upgrade in place first, preserving existing app data. Record platform, device/glasses model, firmware, transport, codec and settings.

1. On iOS and Android, register/confirm camera permission; start, stop and restart three times. Expect fresh preview each time, correct terminal states and a new usable texture after restart.
2. On Android, stream a moving scene for several minutes at the normal quality/frame rate; inspect lag/stutter and frame delivery. Capture/share portrait and landscape photos: expect correctly oriented JPEGs. Compare to 1.0.0 under matched settings before attributing improvement to the SDK.
3. On iOS, test raw and hvc1. In a consumer exercising PR #50, request microphone permission, subscribe before `startStreamSession(audio: true)`, verify PCM delivery, cancel/re-subscribe and stop/restart. Expect no stale audio after cancellation and `STREAM_ACTIVE` for starts overlapping teardown. The example does not expose an audio toggle.
4. Background streaming OFF: background/lock, expect intentional stoppedForBackground then stopped, clear preview and manual restart. ON: repeat foreground/background and 30-second lock/unlock cycles; check continuity and record every failure. The intermittent iOS lock termination is still unresolved.
5. Separately remove glasses, fold hinges and power off during streaming. Removal behavior can vary; on terminal error/stopped expect preview cleanup and a fresh manual start once ready. Observe thermal warnings if they occur naturally; do not deliberately overheat hardware.
6. Mock on each platform: configure registration/permissions, pair, power on, don, start/stop/restart, capture, then unpair. Mock validates integration only: it cannot establish real transport stability, glasses-mic audio, physical doff/hinge behavior, thermal conditions or lock/background reliability.

## Decisions and limitations

- Release version: maintainer explicitly selected stable 1.0.1 for both packages. PR #50 adds public APIs since 1.0.0, for which conventional semver would use 1.1.0; retain the selected target and disclose this exception.
- Conservative dependency-only runtime adoption (95% recommendation); implementing Android audio or new experimental capabilities would add unrelated API and hardware-validation scope.
- Include #52's already-merged example fix in release notes (99% recommendation); omitting it would leave the release notes incomplete.
- Keep the existing iOS lock limitation (99% recommendation); iOS 1.0.1 reports no developer-facing fix, and no new physical evidence clears it.
- Both SDK clones had rewritten upstream 1.0.0 histories. Each was clean; the maintainer separately authorized backup branches and alignment. Backup branch: `codex/backup-before-dat-1.0.1-20261016` in each reference clone.
- No unresolved implementation question after binary inspection. Physical regression outcomes remain for maintainer testing before merge.

## Results

- MCP analysis: core, mock and example passed; changed Dart doc comment formatted and re-analyzed.
- Dart tests: core 30, mock 4, example 11; all passed.
- Native iOS lifecycle harness: teardown gate and wiring checks passed; audio handler 15 checks, 0 failures.
- SwiftPM clean unsigned release iOS example build passed (Runner.app, 36.7 MB).
- All 85 staged iOS device-slice files match the copied 1.0.1 payload byte-for-byte; framework plists report 1.0.1; device/simulator slices are arm64 and the floor remains 17.2.
- All 25 distinct Android SDK imports exist in 1.0.1 AARs, including the top-level CameraKt.addCamera function.
- CI install-snippet check passed: 12 constraints across 30 documents.
- Initial publish dry-runs: only expected modified-file warnings (exit 65), compressed archives 12 MB core / 2 MB mock. Clean-commit reruns pending.
- CocoaPods unsigned release consumer build passed (31.5 MB), with both 1.0.1 plugins present in Podfile.lock.
- Android release APK built successfully (68.9 MB); existing Flutter warnings flag future AGP/Kotlin support deprecations. No toolchain change was made.
- Android JUnit reports: 8 AppForegroundTracker tests and 2 PhotoSharing tests, all passed with zero skips. Independent PR review pending.
- Global SwiftPM setting restored to its original `true` immediately after scaffolding the CocoaPods consumer, whose own pubspec now pins `false`.

## Workflow improvements

Separate tooling commit: removed the stale RC target, documented inspection of both divergent reference clones together, made SourceKitten's YAML-file recipe explicit, and validated staged frameworks before deletion in the maintainer guide. No stop/approval rule was weakened.
