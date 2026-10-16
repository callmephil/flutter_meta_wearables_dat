"""Supplement the executable gate tests with its SDK-dependent call sites.

The full plugin cannot link into a macOS test. Keep these checks limited to
admission placement and balanced teardown scopes; behavior lives in Swift tests.
"""
from pathlib import Path
import re

plugin = Path(
    "ios/flutter_meta_wearables_dat/Sources/flutter_meta_wearables_dat/"
    "MetaWearablesDatPlugin.swift"
).read_text()


def method(name):
    start = re.search(r"\bfunc " + re.escape(name) + r"\(", plugin).start()
    return plugin[start:plugin.index("\n  }", start)]


for name in ("teardownStreamOnly", "teardownDeviceSession"):
    body = method(name)
    assert body.index("streamTeardownGate.enter()") < body.index("await "), name
    assert "defer { streamTeardownGate.leave() }" in body, name

start = method("startStreamSession")
guard = start.index("if streamTeardownGate.isActive {")
reuse = start.index("if let existing = streamSession")
assert guard < reuse, "Teardown admission must precede reuse of the old texture"
guard_end = start.index("\n      }", guard)
assert 'code: "STREAM_ACTIVE"' in start[guard:guard_end]
assert "\n        return" in start[guard:guard_end], "Busy starts must exit before texture reuse"

cleanup = method("performTeardownStreamOnly")
assert cleanup.index("await token.cancel()") < cleanup.index("\n    camera?.stop()"), (
    "Audio cancellation must still precede camera.stop()"
)
print("Plugin wiring: balanced scopes before suspension, busy rejection before reuse, audio stop ordering passed")
