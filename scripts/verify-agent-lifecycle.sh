#!/bin/bash
# Spec §52 lifecycle probe: prove the teachtrack offload handler survives
# process death + cold restart and activity recreation (rotation), by running
# the real sandbox probe (SandboxEndToEndInstrumentedTest capabilities test)
# after each disruption. Runs inside WSL with adb in PATH.
# Usage: bash scripts/verify-agent-lifecycle.sh [device-serial]
set -u
DEVICE="${1:-92f73a16}"
ADB="${ADB:-/root/android-sdk/platform-tools/adb} -s $DEVICE"
PKG=com.leslie.teachtrack
PROBE="-e class com.leslie.teachtrack.SandboxEndToEndInstrumentedTest#sandboxCapabilitiesReturnsStrictJson"
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"

probe() { # $1 = label
    echo "== probe: $1 =="
    $ADB shell am instrument -w -r $PROBE "$RUNNER" 2>&1 | grep -E 'INSTRUMENTATION_STATUS: stream|OK \(|FAILURES|INSTRUMENTATION_CODE'
}

echo "== (b) force-stop + cold restart =="
$ADB shell am force-stop $PKG
sleep 2
$ADB shell am start -n $PKG/.MainActivity >/dev/null
sleep 6
probe "after cold restart"

echo "== (a) activity recreation via rotation =="
$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 1
sleep 3
$ADB shell settings put system user_rotation 0
sleep 3
probe "after rotation recreate"

echo "== lifecycle probe done =="
