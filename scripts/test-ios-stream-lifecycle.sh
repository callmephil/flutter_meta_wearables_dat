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
