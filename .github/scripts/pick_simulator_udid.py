#!/usr/bin/env python3
"""Print the UDID of an available iPhone simulator, or exit non-zero.

Called as:
    UDID=$(xcrun simctl list devices available -j | python3 pick_simulator_udid.py)

Why a UDID rather than a name: `xcodebuild -destination
'platform=iOS Simulator,name=iPhone 16'` is a claim about the runner image that
nothing checks. A runner without that device type fails as

    Unable to find a destination matching the provided destination specifier

which names neither the missing device type nor the missing runtime, and it
fails in the test step, four steps after the decision was really wrong.

This exists for the same reason the file is a file and not inline: it runs
inside a YAML `run: |` block, where a line at column 0 ends the block.
"""
import json
import sys

try:
    data = json.load(sys.stdin)
except (ValueError, KeyError) as exc:
    sys.exit("cannot read the device list from simctl: %s" % exc)

devices = data.get("devices", {})
candidates = [d for group in devices.values() for d in group]
# Shutdown devices are still listed; a UDID that is not booted is still usable
# (xcodebuild boots it), so the only filter that matters is "available", which
# simctl already applied.
iphones = [d for d in candidates if d.get("name", "").startswith("iPhone")]

if not iphones:
    sys.exit("no available iPhone simulator on this runner")

# Newest runtime first, because the newest runtime is the one whose iOS the
# project is actually built against (deploymentTarget 16.0, so any works, but a
# newer runtime is the closer match to a real device).
iphones.sort(key=lambda d: d.get("runtime", ""), reverse=True)
print(iphones[0]["udid"])
