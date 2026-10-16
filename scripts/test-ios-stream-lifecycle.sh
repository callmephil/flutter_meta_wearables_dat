#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
sources=ios/flutter_meta_wearables_dat/Sources/flutter_meta_wearables_dat
swiftc -swift-version 5 -warnings-as-errors -parse-as-library \
  "$sources/StreamTeardownGate.swift" \
  scripts/tests/ios_stream_lifecycle/GateTests.swift \
  -o "$test_dir/gate-tests"
"$test_dir/gate-tests"
python3 scripts/tests/ios_stream_lifecycle/check_wiring.py
# Compile the unchanged production handler against host-only Flutter channel
# types. AVAudioPCMBuffer and dispatch queues are the real macOS frameworks.
swiftc -swift-version 5 -warnings-as-errors -emit-module -emit-library \
  -module-name Flutter scripts/tests/ios_stream_lifecycle/Flutter.swift \
  -emit-module-path "$test_dir/Flutter.swiftmodule" -o "$test_dir/libFlutter.dylib"
swiftc -swift-version 5 -warnings-as-errors -parse-as-library \
  -I "$test_dir" -L "$test_dir" -lFlutter \
  -Xlinker -rpath -Xlinker "$test_dir" \
  "$sources/AudioFrameStreamHandler.swift" \
  scripts/tests/ios_stream_lifecycle/AudioTests.swift \
  -o "$test_dir/audio-tests"
"$test_dir/audio-tests"
